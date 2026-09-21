package dev.modloader.domain

import java.io.File
import java.io.RandomAccessFile

/** ZipFile central directory'yi RAM'e yüklemeden önce üst sınırları doğrula. ZIP64 bilinçli reddedilir. */
object ZipPreflight {
    fun check(file: File) = RandomAccessFile(file, "r").use { input ->
        val size = input.length()
        require(size in 22..Limits.ZIP_BYTES) { "Geçersiz ZIP boyutu" }
        val length = minOf(size, 65557L).toInt()
        val tail = ByteArray(length)
        input.seek(size - length); input.readFully(tail)
        fun u16(data: ByteArray, at: Int) = (data[at].toInt() and 255) or ((data[at + 1].toInt() and 255) shl 8)
        fun u32(data: ByteArray, at: Int) = u16(data, at).toLong() or (u16(data, at + 2).toLong() shl 16)
        val end = (length - 22 downTo 0).firstOrNull { i ->
            u32(tail, i) == 0x06054b50L && i + 22 + u16(tail, i + 20) == length
        } ?: throw IllegalArgumentException("ZIP central directory sonu bulunamadı")
        require(u16(tail, end + 4) == 0 && u16(tail, end + 6) == 0) { "Çok parçalı ZIP desteklenmiyor" }
        val count = u16(tail, end + 10)
        require(count in 1..Limits.ENTRIES && count == u16(tail, end + 8)) { "ZIP girdi sayısı sınırı" }
        val centralSize = u32(tail, end + 12)
        val centralOffset = u32(tail, end + 16)
        require(centralSize in 46..1024L * 1024 && centralOffset + centralSize == size - length + end) { "ZIP64/bozuk central directory" }
        input.seek(centralOffset)
        repeat(count) {
            val header = ByteArray(46); input.readFully(header)
            require(u32(header, 0) == 0x02014b50L) { "Bozuk ZIP girdisi" }
            val flags = u16(header, 8)
            require(flags and 0x41 == 0) { "Şifreli ZIP desteklenmiyor" }
            require(u32(header, 20) != 0xffffffffL && u32(header, 24) != 0xffffffffL && u32(header, 42) != 0xffffffffL) { "ZIP64 desteklenmiyor" }
            require(u16(header, 34) == 0) { "Çok parçalı ZIP desteklenmiyor" }
            val unixMode = (u32(header, 38) shr 16).toInt() and 0xf000
            require(unixMode == 0 || unixMode == 0x8000 || unixMode == 0x4000) { "ZIP sembolik bağ/özel dosya reddedildi" }
            val nameBytes = u16(header, 28)
            require(nameBytes in 1..960) { "ZIP yolu çok uzun" }
            val next = input.filePointer + nameBytes + u16(header, 30) + u16(header, 32)
            require(next <= centralOffset + centralSize) { "Central directory sınırı aşıldı" }
            input.seek(next)
        }
        require(input.filePointer == centralOffset + centralSize) { "ZIP girdi sayısı uyuşmuyor" }
    }
}

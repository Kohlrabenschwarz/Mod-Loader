package dev.modloader.engine

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import dev.modloader.domain.Limits
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

internal object SafeFs {
    // Mevcut her bileşeni lstat ile kontrol eder; linkleri izlemez.
    // Eşzamanlı kötü niyetli dizin değiştirmesine karşı openat/JNI gerekir; README tehdit modeli.
    fun checked(base: File, relative: String): File {
        require(!relative.startsWith('/') && relative.split('/').none { it.isEmpty() || it == "." || it == ".." })
        var cursor = base
        val baseStat = Os.lstat(base.path)
        check(OsConstants.S_ISDIR(baseStat.st_mode)) { "Geçersiz kök" }
        relative.split('/').forEachIndexed { index, part ->
            cursor = File(cursor, part)
            val stat = try { Os.lstat(cursor.path) } catch (e: ErrnoException) {
                if (e.errno == OsConstants.ENOENT) null else throw e
            }
            if (stat != null) {
                check(!OsConstants.S_ISLNK(stat.st_mode)) { "Sembolik bağ reddedildi" }
                check(OsConstants.S_ISREG(stat.st_mode) || OsConstants.S_ISDIR(stat.st_mode)) { "Özel dosya reddedildi" }
                if (index < relative.split('/').lastIndex) check(OsConstants.S_ISDIR(stat.st_mode))
            }
        }
        return cursor
    }

    fun syncDir(dir: File) {
        val fd = Os.open(dir.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
        try {
            check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) { "fsync hedefi dizin değil" }
            Os.fsync(fd)
        } finally { Os.close(fd) }
    }

    fun mkdir(dir: File) {
        if (dir.isDirectory) return
        val parent = requireNotNull(dir.parentFile)
        if (!parent.isDirectory) mkdir(parent)
        check(dir.mkdir()) { "Dizin oluşturulamadı: ${dir.name}" }
        syncDir(parent); syncDir(dir)
    }

    fun hash(file: File, progress: (Long) -> Unit = {}): String? {
        if (!file.exists()) return null
        check(file.isFile && file.length() <= Limits.TOTAL_BYTES) { "Hedef normal dosya değil veya çok büyük" }
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                check(count <= Limits.TOTAL_BYTES) { "Dosya okuma sınırı aşıldı" }
                digest.update(buffer, 0, n); progress(n.toLong())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun copy(input: InputStream, destination: File, limit: Long, progress: (Long) -> Unit = {}) {
        mkdir(destination.parentFile!!)
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(64 * 1024)
            var count = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                count += n
                check(count <= limit) { "Dosya boyutu sınırı aşıldı" }
                output.write(buffer, 0, n); progress(n.toLong())
            }
            output.fd.sync()
        }
        syncDir(destination.parentFile!!)
    }

    fun rename(source: File, target: File) {
        mkdir(target.parentFile!!)
        Os.rename(source.path, target.path) // Aynı dosya sisteminde dosya düzeyinde atomik.
        syncDir(target.parentFile!!)
        if (source.parentFile != target.parentFile) syncDir(source.parentFile!!)
    }

    fun writeAtomic(file: File, text: String) {
        val temp = checked(file.parentFile!!, "${file.name}.tmp")
        text.byteInputStream().use { copy(it, temp, 256L * 1024) }
        rename(temp, file)
    }

    // Yalnızca yönetilen, doğrulanmış alt klasörler için; symlink/özel dosya takip edilmez.
    fun removeTree(base: File, relative: String) {
        val target = checked(base, relative)
        if (!target.exists()) return
        if (target.isDirectory) {
            val children = target.list() ?: error("Klasör okunamadı")
            children.forEach { removeTree(target, it) }
        }
        check(target.delete()) { "Dosya/klasör silinemedi" }
        syncDir(target.parentFile!!)
    }
}

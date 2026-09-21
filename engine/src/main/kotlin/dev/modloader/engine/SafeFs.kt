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
import java.util.UUID

internal object SafeFs {
    private fun statOrNull(file: File) = try { Os.lstat(file.path) } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) null else throw e
    }

    // Mevcut her bileşeni lstat ile kontrol eder; linkleri izlemez.
    // Eşzamanlı kötü niyetli dizin değiştirmesine karşı openat/JNI gerekir; README tehdit modeli.
    fun checked(base: File, relative: String): File {
        require(!relative.startsWith('/') && relative.split('/').none { it.isEmpty() || it == "." || it == ".." })
        var cursor = base
        val baseStat = Os.lstat(base.path)
        check(OsConstants.S_ISDIR(baseStat.st_mode)) { "Geçersiz kök" }
        relative.split('/').forEachIndexed { index, part ->
            cursor = File(cursor, part)
            val stat = statOrNull(cursor)
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
        statOrNull(dir)?.let { stat ->
            check(!OsConstants.S_ISLNK(stat.st_mode) && OsConstants.S_ISDIR(stat.st_mode)) { "Dizin hedefi güvenli değil" }
            return
        }
        val parent = requireNotNull(dir.parentFile)
        mkdir(parent)
        check(dir.mkdir()) { "Dizin oluşturulamadı: ${dir.name}" }
        syncDir(parent); syncDir(dir)
    }

    /** Son yol bileşeninde symlink takibini kapatır ve açılan nesnenin normal dosya olduğunu doğrular. */
    fun input(file: File): FileInputStream {
        val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
        try { check(OsConstants.S_ISREG(Os.fstat(fd).st_mode)) { "Kaynak normal dosya değil" } }
        catch (e: Exception) { Os.close(fd); throw e }
        return FileInputStream(fd)
    }

    fun readText(file: File, limit: Long = 256L * 1024): String = input(file).use { stream ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            check(output.size().toLong() + n <= limit) { "Metin dosyası sınırı aşıldı" }
            output.write(buffer, 0, n)
        }
        output.toString(Charsets.UTF_8.name())
    }

    fun <T> withFileLock(file: File, block: () -> T): T {
        mkdir(file.parentFile!!)
        val fd = Os.open(file.path, OsConstants.O_RDWR or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0x180)
        try { check(OsConstants.S_ISREG(Os.fstat(fd).st_mode)) { "Kilit hedefi normal dosya değil" } }
        catch (e: Exception) { Os.close(fd); throw e }
        return FileOutputStream(fd).use { output ->
            val lock = output.channel.tryLock() ?: error("Başka mod işlemi çalışıyor")
            lock.use { block() }
        }
    }

    fun hash(file: File, progress: (Long) -> Unit = {}): String? {
        val initial = statOrNull(file) ?: return null
        check(OsConstants.S_ISREG(initial.st_mode) && initial.st_size <= Limits.TOTAL_BYTES) { "Hedef normal dosya değil veya çok büyük" }
        val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
        input(file).use { input ->
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

    fun copy(input: InputStream, destination: File, limit: Long, exclusive: Boolean = false, progress: (Long) -> Unit = {}) {
        mkdir(destination.parentFile!!)
        val flags = OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_NOFOLLOW or
            OsConstants.O_CLOEXEC or (if (exclusive) OsConstants.O_EXCL else 0)
        val fd = Os.open(destination.path, flags, 0x180) // 0600; external-storage FUSE may mask the final mode.
        try {
            check(OsConstants.S_ISREG(Os.fstat(fd).st_mode)) { "Hedef normal dosya değil" }
            if (!exclusive) Os.ftruncate(fd, 0)
        }
        catch (e: Exception) { Os.close(fd); throw e }
        FileOutputStream(fd).use { output ->
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
        val parent = file.parentFile!!
        val temp = checked(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        try {
            text.byteInputStream().use { copy(it, temp, 256L * 1024, exclusive = true) }
            rename(temp, file)
        } finally {
            if (statOrNull(temp) != null) check(temp.delete()) { "Geçici dosya silinemedi" }
        }
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

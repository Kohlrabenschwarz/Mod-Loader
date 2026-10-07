package dev.modloader.engine

import android.os.ParcelFileDescriptor
import java.io.IOException

/** Large library responses cross Binder as a descriptor, rather than hitting its transaction limit. */
internal object ResponsePipe {
    fun open(response: String): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            Thread({
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).bufferedWriter(Charsets.UTF_8).use { it.write(response) }
                } catch (_: IOException) { /* The receiving app disconnected or closed the stream. */ }
            }, "mod-library-response").apply { isDaemon = true }.start()
        } catch (e: Exception) {
            pipe.forEach { it.close() }
            throw e
        }
        return pipe[0]
    }
}

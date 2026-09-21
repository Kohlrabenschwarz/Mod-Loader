package dev.modloader.engine

import dev.modloader.domain.GameTarget
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Yalnızca sabit oyunu durdurur; kullanıcı girdisi veya sh -c kullanılmaz. */
internal object GameProcess {
    fun stop() {
        val process = ProcessBuilder("/system/bin/am", "force-stop", "--user", "0", GameTarget.PACKAGE_NAME)
            .redirectErrorStream(true).start()
        // Borunun dolup komutu kilitlemesini önle; çıktı RAM'de biriktirilmez.
        val drain = thread(isDaemon = true, name = "game-stop-output") {
            try { process.inputStream.use { input -> val buffer = ByteArray(1024); while (input.read(buffer) >= 0) { } } }
            catch (_: java.io.IOException) { }
        }
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw dev.modloader.domain.EngineFailure(10, "GAME_STOP_TIMEOUT")
            if (process.exitValue() != 0) throw dev.modloader.domain.EngineFailure(10, "GAME_STOP_FAILED")
        } finally {
            if (process.isAlive) process.destroyForcibly()
            process.inputStream.close(); process.outputStream.close(); process.errorStream.close()
            drain.join(1000)
        }
    }
}

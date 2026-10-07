package dev.modloader.engine

import android.os.ParcelFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsePipeTest {
    @Test fun streamsUnicodeResponsesLargerThanBinderTransactionBudget() {
        val response = "Mod · 纹理 · İtû · описание\n".repeat(50_000)
        assertTrue(response.toByteArray(Charsets.UTF_8).size > 1024 * 1024)
        val received = ParcelFileDescriptor.AutoCloseInputStream(ResponsePipe.open(response))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(response, received)
    }
}

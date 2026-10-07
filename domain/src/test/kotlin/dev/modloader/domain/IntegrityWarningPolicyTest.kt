package dev.modloader.domain

import kotlin.test.*

class IntegrityWarningPolicyTest {
    @Test fun newContentMissingFilesAndBaselineChangesInvalidateDismissal() {
        val original = listOf(IntegrityChange("bundle", "a".repeat(64), "b".repeat(64)))
        val hidden = IntegrityWarningPolicy.fingerprint(original)
        assertNotEquals(hidden, IntegrityWarningPolicy.fingerprint(original.map { it.copy(actual = "c".repeat(64)) }))
        assertNotEquals(hidden, IntegrityWarningPolicy.fingerprint(original.map { it.copy(actual = null) }))
        assertNotEquals(hidden, IntegrityWarningPolicy.fingerprint(original.map { it.copy(expected = "c".repeat(64)) }))
        assertNotEquals(hidden, IntegrityWarningPolicy.fingerprint(original + IntegrityChange("another", null, "d".repeat(64))))
    }
    @Test fun reorderKeepsTheSameObservedWarning() {
        val changes = listOf(IntegrityChange("a", null, "b".repeat(64)), IntegrityChange("b", "a".repeat(64), null))
        assertEquals(IntegrityWarningPolicy.fingerprint(changes), IntegrityWarningPolicy.fingerprint(changes.reversed()))
    }
}

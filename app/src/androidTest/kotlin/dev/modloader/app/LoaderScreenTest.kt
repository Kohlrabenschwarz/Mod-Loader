package dev.modloader.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.mutableStateOf
import dev.modloader.bridge.ShizukuStatus
import dev.modloader.domain.IntegrityChange
import dev.modloader.domain.ModUpdateSource
import dev.modloader.domain.ModUpdateRelease
import dev.modloader.engine.ModMetadata
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoaderScreenTest {
    @Test fun plusOpensTwoImportOptionsAndFileChoiceRunsOnlyAfterSelection() {
        var files = 0
        var links = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en"), ShizukuStatus.READY, 1,
                onImport = { files++ }, onImportLink = { links++ })
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.import_zip)).performClick()
        assertEquals(0, files); assertEquals(0, links)
        compose.onNodeWithContentDescription("Add from file").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add from link").assertIsDisplayed()
        screenshot("import-menu")
        compose.onNodeWithContentDescription("Add from file").performClick()
        assertEquals(1, files); assertEquals(0, links)
        compose.onNodeWithContentDescription("Add from link").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.import_zip)).performClick()
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithContentDescription("Add from file").assertDoesNotExist()
        assertEquals(1, files); assertEquals(0, links)
    }
    @Test fun linkImportRequiresValidHttpsAndExplicitConfirmation() {
        var imported = ""
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en"), ShizukuStatus.READY, 1, onImportLink = { imported = it })
        } }
        fun openLink() {
            compose.onNodeWithContentDescription(context.getString(R.string.import_zip)).performClick()
            compose.onNodeWithContentDescription("Add from link").performClick()
        }
        openLink()
        compose.onNodeWithText("Import").assertIsNotEnabled()
        compose.onNodeWithText("ZIP download link").performTextReplacement("http://example.com/mod.zip")
        compose.onNodeWithText("Import").assertIsNotEnabled()
        compose.onNodeWithText("ZIP download link").performTextReplacement("https://user:pass@example.com/mod.zip")
        compose.onNodeWithText("Import").assertIsNotEnabled()
        compose.onNodeWithText("ZIP download link").performTextReplacement("https://example.com/mod.zip")
        compose.onNodeWithText("Import").assertIsEnabled(); assertEquals("", imported)
        screenshot("import-link")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("", imported)
        openLink()
        compose.onNodeWithText("Import").performClick()
        assertEquals("https://example.com/mod.zip", imported)
    }
    @get:Rule val compose = createComposeRule()
    private fun mod(id: String, name: String, active: Boolean = false) = LibraryMod(id, File("unused.zip"),
        ModMetadata(name, "Tests", "A visual fixture", "1.0", listOf("shared"), null), null,
        active = active, archived = true, requiredBytes = 128L * 1024 * 1024, availableBytes = 256L * 1024 * 1024)
    @Test fun developerSettingsSaveOnlyValidPublicationDirectories() {
        val ui = mutableStateOf(LoaderUi(language = "en"))
        var enabled = false
        var saved = ""
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(ui.value, ShizukuStatus.READY, 1,
                onDeveloperMode = { enabled = it; ui.value = ui.value.copy(developerMode = it) },
                onPublicationBaseUrl = { saved = it; ui.value = ui.value.copy(publicationBaseUrl = it) })
        } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("Dev Mode").performClick()
        assertTrue(enabled)
        compose.onNodeWithText("Publication directory URL").performScrollTo().performTextReplacement("http://example.com/mods/")
        compose.onNodeWithText("Save publication address").performScrollTo().assertIsNotEnabled()
        assertEquals("", saved)
        compose.onNodeWithText("Publication directory URL").performScrollTo().performTextReplacement("https://example.com/mods/?token=secret")
        compose.onNodeWithText("Save publication address").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Publication directory URL").performScrollTo().performTextReplacement("https://example.com/mods/")
        compose.onNodeWithText("Save publication address").performScrollTo().performClick()
        assertEquals("https://example.com/mods/", saved)
        screenshot("dev-settings")
    }
    @Test fun importCollisionWaitsForApprovalCanCancelAndSelectsOnlyOneTarget() {
        val first = mod("first", "Fixture").copy(folder = "first-folder")
        val second = mod("second", "Fixture").copy(folder = "second-folder")
        val incoming = mod("incoming", "Fixture")
        val collision = ImportCollisionUi(incoming, listOf(first, second))
        val ui = mutableStateOf(LoaderUi(language = "en", mods = listOf(first, second), importCollision = collision))
        val status = mutableStateOf(ShizukuStatus.READY)
        var canceled = 0
        var replaced = ""
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(ui.value, status.value, 1,
                onCancelImport = { canceled++; ui.value = ui.value.copy(importCollision = null) },
                onOverwriteImport = { replaced = it; ui.value = ui.value.copy(importCollision = null) })
        } }
        compose.onNodeWithText("Mod conflict").assertIsDisplayed(); assertEquals("", replaced)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, canceled); assertEquals("", replaced)
        compose.runOnIdle { ui.value = ui.value.copy(importCollision = collision); status.value = ShizukuStatus.DENIED }
        compose.onNodeWithText("Overwrite").assertIsNotEnabled()
        compose.runOnIdle { status.value = ShizukuStatus.READY }
        compose.onNodeWithText("second-folder").performClick()
        screenshot("import-overwrite")
        compose.onNodeWithText("Overwrite").performClick()
        assertEquals("second", replaced)
    }
    @Test fun updateFailureShowsStatusAndActualSourceWithoutQuerySecrets() {
        val source = ModUpdateSource("com.example.mod", "https://example.com/latest.json?token=secret")
        val candidate = mod("updatable", "Update fixture").let {
            it.copy(metadata = it.metadata.copy(modId = source.modId, versionCode = 1, update = source))
        }
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en", mods = listOf(candidate),
                modUpdates = mapOf(candidate.id to ModUpdateUi(ModUpdateStatus.ERROR, errorDetail = "HTTP 401"))),
                ShizukuStatus.READY, 1)
        } }
        compose.onNodeWithText("Details: HTTP 401").assertDoesNotExist()
        compose.onNodeWithText("Update fixture").performClick()
        compose.onNodeWithText("Details: HTTP 401").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Update source: https://example.com/latest.json").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("secret", substring = true)).assertCountEquals(0)
    }
    @Test fun modUpdateRequiresConfirmationAndNeverActivatesTheNewVersion() {
        var installs = 0
        var activations = 0
        val source = ModUpdateSource("com.example.mod", "https://example.com/latest.json")
        val candidate = mod("updatable", "Update fixture", true).let {
            it.copy(metadata = it.metadata.copy(modId = source.modId, versionCode = 1, update = source))
        }
        val release = ModUpdateRelease(source.modId, "1.2.0", 2, "https://example.com/v2.zip", "a".repeat(64), 1024, "New textures")
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en", mods = listOf(candidate),
                modUpdates = mapOf(candidate.id to ModUpdateUi(ModUpdateStatus.AVAILABLE, release))), ShizukuStatus.READY, 1,
                onInstallModUpdate = { installs++ }, onToggle = { _, _ -> activations++ })
        } }
        compose.onNodeWithText("Check mod update").assertDoesNotExist()
        compose.onNodeWithText("Download and update mod").assertDoesNotExist()
        compose.onNodeWithText("Update fixture").performClick()
        compose.onNodeWithText("Check mod update").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download and update mod").performScrollTo().performClick()
        assertEquals(0, installs)
        compose.onNodeWithText("New textures").assertIsDisplayed()
        compose.onNodeWithText("Update source: example.com").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, installs)
        compose.onNodeWithText("Download and update mod").performClick()
        compose.onNode(hasText("Download and update mod") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(1, installs); assertEquals(0, activations)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val resolver = instrumentation.targetContext.contentResolver
        val uri = requireNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ModLoaderQA")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }))
        requireNotNull(resolver.openOutputStream(uri)).use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        resolver.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        bitmap.recycle()
    }
    @Test fun deniedConnectionExplainsPermissionAndCanRetry() {
        var retries = 0
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en"), ShizukuStatus.DENIED, 3, onRetryConnection = { retries++ })
        } }
        compose.onNodeWithText("Allow Mod Loader in Shizuku, then return here.").assertIsDisplayed()
        compose.onNodeWithText("Retry connection").performClick()
        assertEquals(1, retries)
        screenshot("connection")
    }
    @Test fun searchFiltersClearsAndKeepsHiddenActiveModsInConflictChecks() {
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en", mods = listOf(mod("one", "Candidate"), mod("two", "Existing", true))),
                ShizukuStatus.READY, 1)
        } }
        compose.onNodeWithText("Search mods").performTextInput("candidate")
        compose.onNodeWithText("1 of 2 mods").assertExists()
        compose.onNodeWithText("Existing").assertDoesNotExist()
        compose.onNodeWithContentDescription("Activate Candidate").performScrollTo().performClick()
        compose.onNodeWithText("Disable these conflicting mods first:").assertIsDisplayed()
        compose.onNodeWithText("Existing").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Clear").performScrollTo().performClick()
        compose.onNodeWithText("Search mods").performTextInput("no-such-mod")
        compose.onNodeWithText("No matching mods. Try another search.").assertExists()
        screenshot("search")
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("2 of 2 mods").assertDoesNotExist()
        compose.onNodeWithContentDescription("Activate Candidate").assertExists()
    }
    @Test fun activationShowsConflictingModAndDisablesConfirmation() {
        compose.setContent { LoaderTheme(false, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "en", mods = listOf(mod("one", "Candidate"), mod("two", "Existing", true))),
                ShizukuStatus.READY, 1)
        } }
        compose.onNodeWithContentDescription("Activate Candidate").performClick()
        compose.onNodeWithText("Disable these conflicting mods first:").assertIsDisplayed()
        compose.onNodeWithText("Activate mod").assertIsNotEnabled()
        screenshot("conflict")
    }
    @Test fun changedFilesAndRecoveryGuidanceAreVisibleInTurkish() {
        val changed = mod("one", "Texture").copy(shaMismatch = true, backupAt = 1_790_000_000_000,
            changes = listOf(IntegrityChange("shared", "a".repeat(64), "b".repeat(64))))
        compose.setContent { LoaderTheme(true, Accent.PURPLE) {
            LoaderScreen(LoaderUi(language = "tr", dark = true, mods = listOf(changed)), ShizukuStatus.READY, 1)
        } }
        compose.onNodeWithContentDescription("Dosya değişikliği algılandı").performClick()
        compose.onNodeWithText("shared").assertExists()
        compose.onNodeWithText("Beklenen SHA-256: ${"a".repeat(64)}").assertExists()
        screenshot("recovery")
    }
}

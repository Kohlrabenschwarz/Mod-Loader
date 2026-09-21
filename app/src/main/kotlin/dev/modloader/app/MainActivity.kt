package dev.modloader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.modloader.domain.GameTarget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: LoaderViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            SideEffect {
                val bar = if (ui.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            LoaderTheme(ui.dark, ui.accent) {
                if (ui.agreementAccepted) LoaderRoute(vm)
                else UserAgreementDialog(ui.language, required = true, onLanguage = vm::language, onAccept = vm::acceptAgreement,
                    onDismiss = { finishAffinity() })
            }
        }
    }
}

// Android etkileşimleri burada; LoaderScreen yalnızca durum ve callback alır.
@Composable
private fun LoaderRoute(vm: LoaderViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val status by vm.shizuku.status.collectAsStateWithLifecycle()
    val attempt by vm.shizuku.attempt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshIntegrity()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importZip) }
    LoaderScreen(ui, status, attempt,
        onCheckUpdates = vm::checkUpdates,
        onUpdate = { vm.openUpdate { url -> context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } },
        onImport = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        onPlay = { vm.play {
            val intent = context.packageManager.getLaunchIntentForPackage(GameTarget.PACKAGE_NAME)
                ?: throw dev.modloader.domain.EngineFailure(10, "GAME_MISSING")
            context.startActivity(intent)
        } }, onLanguage = vm::language, onDark = vm::dark, onAccent = vm::accent,
        onToggle = vm::setActive, onDelete = vm::deleteMod, onWarningAction = vm::warningAction, onIgnore = vm::ignoreWarning)
}

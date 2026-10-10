package dev.ramim.phonestatus

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ramim.phonestatus.data.StatsViewModel
import dev.ramim.phonestatus.data.TopApps
import dev.ramim.phonestatus.live.ForegroundGate
import dev.ramim.phonestatus.live.LiveUpdateService
import dev.ramim.phonestatus.ui.Dashboard
import dev.ramim.phonestatus.ui.PhoneStatusTheme
import dev.ramim.phonestatus.widget.StatusWidgetProvider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: StatsViewModel by viewModels()

    /** True when the user let Phone Status ignore battery optimisation (so Android keeps the live service alive). */
    private var batteryExempt by mutableStateOf(false)

    private fun readExempt(): Boolean = try {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (_: Exception) {
        false
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            PhoneStatusTheme {
                val stats by vm.stats.collectAsStateWithLifecycle()
                val thermal by vm.thermal.collectAsStateWithLifecycle()
                val topApps by TopApps.state.collectAsStateWithLifecycle()
                val smartPause by ForegroundGate.connected.collectAsStateWithLifecycle()
                Dashboard(
                    stats = stats,
                    thermal = thermal,
                    topApps = topApps,
                    onRefreshApps = { lifecycleScope.launch { TopApps.refresh(applicationContext) } },
                    batteryExempt = batteryExempt,
                    onAllowBackground = { askBackground() },
                    onAddWidget = { requestPinWidget(this) },
                    onRetryRoot = vm::retryRoot,
                    smartPause = smartPause,
                    onOpenHomeDragon = {
                        val launch = packageManager.getLaunchIntentForPackage(ForegroundGate.HOME_DRAGON_PACKAGE)
                        if (launch != null) {
                            startActivity(launch)
                        } else {
                            Toast.makeText(this, "Home Dragon is not installed", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ForegroundGate.register(this)
        batteryExempt = readExempt()
        // The app is in the foreground here, so Android always allows starting the live service.
        LiveUpdateService.startIfWidgets(this)
    }

    /** Shows Android's own "let this app run in the background" question; falls back to the settings list. */
    private fun askBackground() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Open Settings → Battery and allow Phone Status to run in the background", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestPinWidget(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(ctx, StatusWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(
                ctx,
                "Long-press your home screen → Widgets → Phone Status",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}

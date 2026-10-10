package dev.ramim.phonestatus

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ramim.phonestatus.data.StatsViewModel
import dev.ramim.phonestatus.live.ForegroundGate
import dev.ramim.phonestatus.live.LiveUpdateService
import dev.ramim.phonestatus.ui.Dashboard
import dev.ramim.phonestatus.ui.PhoneStatusTheme
import dev.ramim.phonestatus.ui.rememberLiveHz
import dev.ramim.phonestatus.widget.StatusWidgetProvider

class MainActivity : ComponentActivity() {

    private val vm: StatsViewModel by viewModels()

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
                val liveHz by rememberLiveHz()
                val smartPause by ForegroundGate.connected.collectAsStateWithLifecycle()
                Dashboard(
                    stats = stats,
                    thermal = thermal,
                    liveHz = liveHz,
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
        // The app is in the foreground here, so Android always allows starting the live service.
        LiveUpdateService.startIfWidgets(this)
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

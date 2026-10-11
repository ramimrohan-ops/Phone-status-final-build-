package dev.ramim.phonestatus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ramim.phonestatus.live.ForegroundGate
import dev.ramim.phonestatus.live.LiveDebug
import dev.ramim.phonestatus.live.SwitchEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Six lines about the link with Home Dragon and the widget service, a Copy button and the last five changes of state. */
@Composable
fun DebugTab() {
    val ctx = LocalContext.current
    val connected by ForegroundGate.connected.collectAsStateWithLifecycle()
    val run by ForegroundGate.allowed.collectAsStateWithLifecycle()
    val why by ForegroundGate.why.collectAsStateWithLifecycle()
    val events by ForegroundGate.events.collectAsStateWithLifecycle()
    val lastSignal by ForegroundGate.lastSignalAt.collectAsStateWithLifecycle()
    val lastUpdate by LiveDebug.lastUpdateAt.collectAsStateWithLifecycle()
    val serviceUp by LiveDebug.serviceUp.collectAsStateWithLifecycle()
    val shown by LiveDebug.notification.collectAsStateWithLifecycle()
    val status by LiveDebug.status.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val state = if (!connected) "No answer yet" else if (run) "Run" else "Don't run"
    val link = if (lastSignal == 0L) "no message yet" else "message ${ago(now, lastSignal)}"
    val service = if (serviceUp) status else "not running"
    val update = if (lastUpdate == 0L) "never" else ago(now, lastUpdate)
    val notification = if (shown) "shown" else "hidden"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("Debug", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Hairline()
        Line("State", state, if (run) Palette.Accent else Palette.Warn)
        Line("Why", if (connected) why else "—")
        Line("Link", link, if (connected) Palette.TextPrimary else Palette.Warn)
        Line("Service", service, if (serviceUp) Palette.TextPrimary else Palette.Warn)
        Line("Last update", update)
        Line("Notification", notification)
        Hairline()
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { copy(ctx, state, why, link, service, update, notification, events) },
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Palette.Accent),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Accent),
        ) {
            Text("Copy", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(24.dp))
        Label("Last 5 changes")
        Spacer(Modifier.height(8.dp))
        if (events.isEmpty()) {
            Text("None yet.", color = Palette.TextLabel, fontSize = 13.sp)
        } else {
            events.take(5).forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(clock(e.time), color = Palette.TextDim, fontSize = 12.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        (if (e.run) "Run" else "Don't run") + ": " + e.text,
                        color = Palette.TextLabel,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

private fun ago(now: Long, then: Long): String {
    val s = ((now - then) / 1000L).coerceAtLeast(0L)
    return if (s < 60) "${s}s ago" else "${s / 60}m ${s % 60}s ago"
}

private fun copy(
    ctx: Context,
    state: String,
    why: String,
    link: String,
    service: String,
    update: String,
    notification: String,
    events: List<SwitchEvent>,
) {
    val text = buildString {
        appendLine("Phone Status debug")
        appendLine("state: $state")
        appendLine("why: $why")
        appendLine("link: $link")
        appendLine("service: $service")
        appendLine("last update: $update")
        appendLine("notification: $notification")
        appendLine("last changes (newest first):")
        events.take(5).forEach { appendLine("${clock(it.time)}  ${if (it.run) "Run" else "Don't run"}: ${it.text}") }
    }
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Phone Status debug", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}

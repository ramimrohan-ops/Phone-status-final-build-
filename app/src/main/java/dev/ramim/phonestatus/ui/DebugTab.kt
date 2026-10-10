package dev.ramim.phonestatus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ramim.phonestatus.live.ForegroundGate
import dev.ramim.phonestatus.live.LiveDebug
import dev.ramim.phonestatus.live.SwitchEvent
import dev.ramim.phonestatus.data.TopApps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Shows what smart pause sees and decides, so a stuck widget can be traced to its cause. */
@Composable
fun DebugTab(onOpenHomeDragon: () -> Unit) {
    val ctx = LocalContext.current
    val connected by ForegroundGate.connected.collectAsStateWithLifecycle()
    val allowed by ForegroundGate.allowed.collectAsStateWithLifecycle()
    val events by ForegroundGate.events.collectAsStateWithLifecycle()
    val why by ForegroundGate.why.collectAsStateWithLifecycle()
    val lastSignal by ForegroundGate.lastSignalAt.collectAsStateWithLifecycle()
    val status by LiveDebug.status.collectAsStateWithLifecycle()
    val rate by LiveDebug.rate.collectAsStateWithLifecycle()
    val lastUpdate by LiveDebug.lastUpdateAt.collectAsStateWithLifecycle()
    val screenOn by LiveDebug.screenOn.collectAsStateWithLifecycle()
    val serviceUp by LiveDebug.serviceUp.collectAsStateWithLifecycle()
    val topApps by TopApps.state.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val exempt = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName) && now > 0L
    } catch (_: Exception) {
        false
    }
    val updating = lastUpdate != 0L && now - lastUpdate < 3_000L
    val liveRate = if (updating) String.format(Locale.US, "%.1f / s", rate) else "0 / s (not updating)"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Label("Smart pause")
        Spacer(Modifier.height(4.dp))
        Text("Debug", color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                !connected -> "Home Dragon has not answered yet, so updates are paused. Open Home Dragon and check that its icon finder is on."
                allowed -> "Home Dragon says the home screen is on top, so updates should run."
                else -> "Updates are paused. Home Dragon reports: $why."
            },
            color = if (!allowed) Palette.TempWarm else Palette.TextLabel,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(24.dp))
        Hairline()
        Spacer(Modifier.height(20.dp))

        Label("State")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DetailRow(
                "Home Dragon link",
                if (connected) "Message received" else "No answer yet",
                if (connected) Palette.Battery else Palette.TempWarm,
            )
            DetailRow("Home Dragon says", if (connected) why else "—")
            DetailRow("Last message", if (lastSignal == 0L) "never" else "${clock(lastSignal)} (${ago(now, lastSignal)})")
            DetailRow("Updates allowed", if (allowed) "Yes" else "No (paused)", if (allowed) Palette.Battery else Palette.TempWarm)
            DetailRow("Widget loop", status)
            DetailRow("Widget updates", liveRate)
            DetailRow("Last update", if (lastUpdate == 0L) "never" else ago(now, lastUpdate))
            DetailRow("Screen", if (screenOn) "On" else "Off")
            DetailRow(
                "Live service",
                if (serviceUp) "Running" else "Not running",
                if (serviceUp) Palette.Battery else Palette.TempWarm,
            )
            DetailRow(
                "Battery setting",
                if (exempt) "No restrictions" else "Optimised (Android may stop the app)",
                if (exempt) Palette.Battery else Palette.TempWarm,
            )
            DetailRow("Top apps source", topApps.source.ifEmpty { "—" })
            DetailRow("Top apps status", if (topApps.busy) "Reading…" else topApps.note.ifEmpty { "OK" })
        }
        if (topApps.detail.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                topApps.detail,
                color = Palette.TextLabel,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        TextButton(onClick = onOpenHomeDragon) {
            Text("Open Home Dragon", color = Palette.Battery)
        }

        Spacer(Modifier.height(24.dp))
        Hairline()
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Messages (newest first)")
            Row {
                TextButton(onClick = { copyLog(ctx, connected, allowed, status, events) }) {
                    Text("Copy", color = Palette.Battery, fontSize = 13.sp)
                }
                TextButton(onClick = { ForegroundGate.clearLog() }) {
                    Text("Clear", color = Palette.TextLabel, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (events.isEmpty()) {
            Text(
                "Nothing yet. Open Home Dragon and go to your home screen.",
                color = Palette.TextLabel,
                fontSize = 13.sp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                events.forEach { EventRow(it) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EventRow(e: SwitchEvent) {
    val color = when {
        e.decision == "RUN" -> Palette.Battery
        e.decision == "PAUSED" -> Palette.TempWarm
        else -> Palette.TextLabel
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(clock(e.time), color = Palette.TextLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(10.dp))
        Text(
            e.pkg,
            color = Palette.TextPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        Text(e.decision, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

private fun ago(now: Long, then: Long): String {
    val s = ((now - then) / 1000L).coerceAtLeast(0L)
    return if (s < 60) "${s}s ago" else "${s / 60}m ${s % 60}s ago"
}

private fun copyLog(
    ctx: Context,
    connected: Boolean,
    allowed: Boolean,
    status: String,
    events: List<SwitchEvent>,
) {
    val text = buildString {
        appendLine("Phone Status smart pause log")
        appendLine("home dragon message received: $connected")
        appendLine("updates allowed: $allowed")
        appendLine("widget loop: $status")
        appendLine("events (newest first):")
        events.forEach { appendLine("${clock(it.time)}  ${it.pkg}  ${it.decision}") }
    }
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("smart pause log", text))
    Toast.makeText(ctx, "Log copied", Toast.LENGTH_SHORT).show()
}

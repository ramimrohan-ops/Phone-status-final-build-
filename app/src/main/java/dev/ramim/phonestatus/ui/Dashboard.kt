package dev.ramim.phonestatus.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ramim.phonestatus.data.BatteryInfo
import dev.ramim.phonestatus.data.RootState
import dev.ramim.phonestatus.data.Stats
import dev.ramim.phonestatus.data.ThermalInfo
import dev.ramim.phonestatus.data.TopApps
import dev.ramim.phonestatus.data.TopAppsState
import dev.ramim.phonestatus.live.ForegroundGate
import dev.ramim.phonestatus.widget.WidgetRenderer

/** The whole app: a plain list of numbers on one flat background, and a short Debug tab. No rings, bars, gradients or animations. */
@Composable
fun Dashboard(
    stats: Stats,
    thermal: ThermalInfo,
    topApps: TopAppsState,
    onRefreshApps: () -> Unit,
    onAllowShizuku: () -> Unit,
    batteryExempt: Boolean,
    onAllowBackground: () -> Unit,
    onAddWidget: () -> Unit,
    onRetryRoot: () -> Unit,
    linked: Boolean,
    onOpenHomeDragon: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                StatusTab(
                    stats, thermal, topApps, onRefreshApps, onAllowShizuku, batteryExempt, onAllowBackground,
                    onAddWidget, onRetryRoot, linked, onOpenHomeDragon,
                )
            } else {
                DebugTab()
            }
        }
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
        ) {
            TabItem("STATUS", tab == 0, Modifier.weight(1f)) { tab = 0 }
            TabItem("DEBUG", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }
    }
}

@Composable
private fun TabItem(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Palette.Accent else Palette.TextLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
        )
    }
}

@Composable
private fun StatusTab(
    stats: Stats,
    thermal: ThermalInfo,
    topApps: TopAppsState,
    onRefreshApps: () -> Unit,
    onAllowShizuku: () -> Unit,
    batteryExempt: Boolean,
    onAllowBackground: () -> Unit,
    onAddWidget: () -> Unit,
    onRetryRoot: () -> Unit,
    linked: Boolean,
    onOpenHomeDragon: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Header()
        Spacer(Modifier.height(20.dp))
        Battery(stats.battery)
        Spacer(Modifier.height(18.dp))
        Hairline()

        Group("Temperature")
        Line("CPU", temp(thermal.cpuC))
        Line("GPU", temp(thermal.gpuC))
        Line("Battery", temp(stats.battery.tempC))
        val mhz = thermal.freq.cpuPeakMhz
        Line("CPU clock", fmtFreq(mhz), if (mhz != null && mhz >= WidgetRenderer.CPU_TOP_MHZ) Palette.Warn else Palette.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Hairline()

        Group("Memory")
        Line("RAM", "${fmtGb(stats.ram.usedBytes)} / ${fmtGb(stats.ram.totalBytes)}")
        Line("Storage", "${fmtGb(stats.storage.usedBytes, 1000.0)} / ${fmtGb(stats.storage.totalBytes, 1000.0)}")
        Spacer(Modifier.height(10.dp))
        Hairline()

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(top = 16.dp)) { Label("Apps") }
            Action(
                if (topApps.busy) "Reading…" else "Refresh",
                onClick = { if (!topApps.busy) onRefreshApps() },
                color = if (topApps.busy) Palette.TextLabel else Palette.Accent,
            )
        }
        Apps(topApps, onAllowShizuku)
        Spacer(Modifier.height(10.dp))
        Hairline()

        Spacer(Modifier.height(8.dp))
        val d = stats.display
        Line("Display", if (d.width > 0) "${d.width}×${d.height} · ${d.dpi} dpi" else "—", Palette.TextLabel)

        Spacer(Modifier.height(4.dp))
        Action("Add widget", onAddWidget)
        if (!batteryExempt) SetupRow("Background running", "Allow", onAllowBackground)
        if (!linked) SetupRow("Home Dragon", "Open", onOpenHomeDragon)
        if (thermal.sensorCount == 0 && thermal.rootState == RootState.DENIED) SetupRow("CPU and GPU sensors", "Retry root", onRetryRoot)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Header() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Phone Status", color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            Text("$maker ${Build.MODEL}", color = Palette.TextDim, fontSize = 12.sp)
        }
        RunState()
    }
}

/** A dot and one word: Running while Home Dragon says "Run", Paused otherwise. Only this piece redraws when it changes. */
@Composable
private fun RunState() {
    val run by ForegroundGate.allowed.collectAsStateWithLifecycle()
    val color = if (run) Palette.Accent else Palette.TextLabel
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (run) "Running" else "Paused", color = color, fontSize = 13.sp)
    }
}

@Composable
private fun Battery(b: BatteryInfo) {
    val low = b.level <= 15 && !b.charging
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${b.level}",
            color = if (low) Palette.Hot else Palette.TextPrimary,
            fontSize = 84.sp,
            fontWeight = FontWeight.ExtraLight,
        )
        Text(
            "%",
            color = Palette.TextLabel,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraLight,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
        )
    }
    val sub = listOfNotNull(
        if (b.charging) "charging" else "on battery",
        b.healthPct?.let { "health $it %" },
    ).joinToString(" · ")
    Text(sub, color = Palette.TextLabel, fontSize = 13.sp)
}

@Composable
private fun Group(title: String) {
    Spacer(Modifier.height(16.dp))
    Label(title)
    Spacer(Modifier.height(4.dp))
}

/** The apps using the most memory. Read once per tap on Refresh, never automatically. */
@Composable
private fun Apps(s: TopAppsState, onAllowShizuku: () -> Unit) {
    if (s.apps.isEmpty()) {
        Text(
            if (s.busy) "Reading…" else s.note.ifEmpty { "Tap Refresh" },
            color = Palette.TextLabel,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    } else {
        s.apps.forEachIndexed { i, a ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                Text(
                    a.label,
                    color = Palette.TextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(TopApps.fmtMb(a.mb), color = Palette.TextLabel, fontSize = 15.sp)
            }
        }
        // Why the last try failed (the old list stays on screen).
        if (!s.busy && s.note.isNotEmpty()) {
            Text(s.note, color = Palette.Warn, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (s.needShizukuPermission && !s.busy) Action("Allow Phone Status in Shizuku", onAllowShizuku)
}

@Composable
private fun SetupRow(label: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.TextLabel, fontSize = 13.sp)
        Action(action, onClick)
    }
}

private fun temp(c: Float?): String = if (c == null) "—" else "${f1(c)} °C"

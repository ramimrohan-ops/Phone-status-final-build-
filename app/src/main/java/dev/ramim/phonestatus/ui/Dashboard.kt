package dev.ramim.phonestatus.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ramim.phonestatus.data.BatteryInfo
import dev.ramim.phonestatus.data.DisplayInfo
import dev.ramim.phonestatus.data.RamInfo
import dev.ramim.phonestatus.data.RootState
import dev.ramim.phonestatus.data.Stats
import dev.ramim.phonestatus.data.StorageInfo
import dev.ramim.phonestatus.data.ThermalInfo
import dev.ramim.phonestatus.data.TopApps
import dev.ramim.phonestatus.data.TopAppsState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Dashboard(
    stats: Stats,
    thermal: ThermalInfo,
    topApps: TopAppsState,
    onRefreshApps: () -> Unit,
    batteryExempt: Boolean,
    onAllowBackground: () -> Unit,
    onAddWidget: () -> Unit,
    onRetryRoot: () -> Unit,
    smartPause: Boolean,
    onOpenHomeDragon: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom))),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                StatusTab(
                    stats, thermal, topApps, onRefreshApps, batteryExempt, onAllowBackground,
                    onAddWidget, onRetryRoot, smartPause, onOpenHomeDragon,
                )
            } else {
                DebugTab(onOpenHomeDragon)
            }
        }
        BottomTabs(selected = tab, onSelect = { tab = it })
    }
}

@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp),
        ) {
            TabItem("STATUS", selected == 0, Modifier.weight(1f)) { onSelect(0) }
            TabItem("DEBUG", selected == 1, Modifier.weight(1f)) { onSelect(1) }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = if (selected) Palette.Battery else Palette.TextLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.Battery else Color.Transparent),
            )
        }
    }
}

@Composable
private fun StatusTab(
    stats: Stats,
    thermal: ThermalInfo,
    topApps: TopAppsState,
    onRefreshApps: () -> Unit,
    batteryExempt: Boolean,
    onAllowBackground: () -> Unit,
    onAddWidget: () -> Unit,
    onRetryRoot: () -> Unit,
    smartPause: Boolean,
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
        Spacer(Modifier.height(24.dp))
        BatterySection(stats.battery)
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))
        MemorySection(stats.ram, stats.storage)
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))
        TopAppsSection(topApps, onRefreshApps)
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))
        DisplaySection(stats.display)
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))
        TempSection(stats.battery.tempC, thermal, onRetryRoot)
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))
        SmartPauseSection(smartPause, onOpenHomeDragon)
        Spacer(Modifier.height(28.dp))
        Hairline()
        Spacer(Modifier.height(24.dp))
        BackgroundSection(batteryExempt, onAllowBackground)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(
            onClick = onAddWidget,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Palette.Battery.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Battery),
        ) {
            Text("Add widget to home screen", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Header() {
    Column {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        Label("$maker ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        Spacer(Modifier.height(4.dp))
        Text(
            "Phone Status",
            color = Palette.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun BatterySection(b: BatteryInfo) {
    val low = b.level <= 15 && !b.charging
    val accent = if (low) Palette.BatteryLow else Palette.Battery

    Label("Battery")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${b.level}",
            color = Palette.TextPrimary,
            fontSize = 76.sp,
            fontWeight = FontWeight.Light,
        )
        Text(
            "%",
            color = Palette.TextLabel,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 2.dp, bottom = 14.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Led(tempColor(b.tempC, 60f))
                Spacer(Modifier.size(8.dp))
                Text("${f1(b.tempC)} °C", color = Palette.TextPrimary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                if (b.charging) "Charging ⚡" else "On battery",
                color = if (b.charging) accent else Palette.TextLabel,
                fontSize = 12.sp,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    ThinBar(b.level / 100f, accent, height = 4.dp)
    Spacer(Modifier.height(22.dp))

    val capacity = b.capacityMah?.let { "~${fmtMah(it)}" } ?: "—"
    val capSub = listOfNotNull(
        b.healthPct?.let { "$it%" },
        b.cycleCount?.let { "$it cyc" },
    ).joinToString(" · ").ifEmpty { null }

    Row(Modifier.fillMaxWidth()) {
        Metric(
            "Current",
            b.currentMa?.let { (if (b.charging) "↑ " else "↓ ") + "$it mA" } ?: "—",
            Modifier.weight(1f),
        )
        Metric(
            "Voltage",
            String.format(java.util.Locale.US, "%.2f V", b.voltageV),
            Modifier.weight(1f),
        )
        Metric("Capacity", capacity, Modifier.weight(1.2f), sub = capSub)
    }

    Spacer(Modifier.height(18.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Health", b.health)
        DetailRow("Source", b.plug)
        b.designMah?.let { DetailRow("Design capacity", fmtMah(it)) }
        b.healthPct?.let { DetailRow("Capacity health", "$it%") }
        b.cycleCount?.let { DetailRow("Charge cycles", "$it") }
    }
}

@Composable
private fun MemorySection(r: RamInfo, s: StorageInfo) {
    Label("Memory")
    Spacer(Modifier.height(14.dp))
    UsageRow(
        name = "RAM",
        fraction = r.fraction,
        detail = "${fmtGb(r.usedBytes)} / ${fmtGb(r.totalBytes)}",
        sub = if (r.swapTotalBytes > 0) "Swap ${fmtGb(r.swapUsedBytes)} / ${fmtGb(r.swapTotalBytes)}" else null,
    )
    Spacer(Modifier.height(22.dp))
    UsageRow(
        name = "Storage",
        fraction = s.fraction,
        detail = "${fmtGb(s.usedBytes, 1000.0)} / ${fmtGb(s.totalBytes, 1000.0)}",
        sub = "${fmtGb(s.freeBytes, 1000.0)} free",
    )
}

@Composable
private fun UsageRow(name: String, fraction: Float, detail: String, sub: String?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(name, color = Palette.TextLabel, fontSize = 13.sp)
            Text(
                "${(fraction * 100).roundToInt()}%",
                color = Palette.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(detail, color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (sub != null) Text(sub, color = Palette.TextLabel, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
    ThinBar(fraction, Palette.SoftWhite)
}

@Composable
private fun DisplaySection(d: DisplayInfo) {
    Label("Display")
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow("Resolution", if (d.width > 0) "${d.width}×${d.height}" else "—")
        DetailRow("Density", if (d.dpi > 0) "${d.dpi} dpi" else "—")
    }
}

/** The five apps using the most memory right now. Read once per tap, never automatically. */
@Composable
private fun TopAppsSection(s: TopAppsState, onRefresh: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.at) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("Top apps · RAM")
        TextButton(onClick = onRefresh, enabled = !s.busy) {
            Text(if (s.busy) "Reading…" else "↻ Refresh", color = if (s.busy) Palette.TextLabel else Palette.Battery)
        }
    }
    Spacer(Modifier.height(6.dp))
    if (s.apps.isEmpty()) {
        Text(
            if (s.busy) "Reading…" else s.note.ifEmpty { "Tap Refresh" },
            color = Palette.TextLabel,
            fontSize = 13.sp,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            s.apps.forEachIndexed { i, a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = Palette.TextLabel, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                    Text(
                        a.label,
                        color = Palette.TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(TopApps.fmtMb(a.mb), color = Palette.TextLabel, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Read ${TopApps.ageText(s.at, now)} ago · via ${s.source}",
            color = Palette.TextLabel,
            fontSize = 11.sp,
        )
    }
    if (s.apps.isEmpty() && !s.busy && s.note == "Grant needed") {
        Spacer(Modifier.height(4.dp))
        Text(
            "Grant DUMP and PACKAGE_USAGE_STATS once from Termux, then tap Refresh.",
            color = Palette.TextLabel,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun BackgroundSection(exempt: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("Run in background")
        Text(
            if (exempt) "ON" else "OFF",
            color = if (exempt) Palette.Battery else Palette.TextLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (exempt) {
            "Android will not stop the live widget when you close this app. On HyperOS also keep Autostart on " +
                "and lock the app in the recents screen."
        } else {
            "Let Phone Status keep running when this app is closed. Tap Allow and choose \"Allow\". On HyperOS also turn " +
                "Autostart on and set Battery saver to No restrictions for this app."
        },
        color = Palette.TextLabel,
        fontSize = 13.sp,
    )
    if (!exempt) {
        TextButton(onClick = onAllow) {
            Text("Allow", color = Palette.Battery)
        }
    }
}

@Composable
private fun TempSection(batteryTempC: Float, t: ThermalInfo, onRetryRoot: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("Temperature & clocks")
        val (text, color) = when {
            t.viaRoot -> "ROOT" to Palette.Battery
            t.sensorCount > 0 -> "NO ROOT" to Palette.TextLabel
            t.rootState == RootState.UNKNOWN -> "CHECKING…" to Palette.TextLabel
            else -> "NO ACCESS" to Palette.TempHot
        }
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
    }
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TempRow("CPU", t.cpuC, 95f)
        TempRow("GPU", t.gpuC, 95f)
        TempRow("Battery", batteryTempC, 60f)
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        t.hottest?.let {
            DetailRow("Hottest sensor", "${it.name} · ${f1(it.celsius)} °C", tempColor(it.celsius, 95f))
        }
        if (t.freq.cpuCoresMhz.isNotEmpty()) {
            DetailRow("CPU clock (peak)", fmtFreq(t.freq.cpuPeakMhz))
            DetailRow(
                "CPU cores (GHz)",
                t.freq.cpuCoresMhz.joinToString(" ") { mhz -> if (mhz == null) "–" else f1(mhz / 1000f) },
            )
        }
    }
    if (t.sensorCount == 0 && t.rootState == RootState.DENIED) {
        Spacer(Modifier.height(8.dp))
        Text(
            "CPU/GPU sensors are blocked. Grant root to Phone Status in your root manager, then retry.",
            color = Palette.TextLabel,
            fontSize = 12.sp,
        )
        TextButton(onClick = onRetryRoot) {
            Text("Retry root access", color = Palette.Battery)
        }
    }
}

@Composable
private fun TempRow(name: String, celsius: Float?, max: Float) {
    val color = tempColor(celsius, max)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Led(color)
            Spacer(Modifier.size(10.dp))
            Text(name, color = Palette.TextLabel, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(
                if (celsius == null) "—" else "${f1(celsius)} °C",
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(8.dp))
        ThinBar((celsius ?: 0f) / max, color)
    }
}

@Composable
private fun SmartPauseSection(on: Boolean, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("Smart pause")
        Text(
            if (on) "ON" else "OFF",
            color = if (on) Palette.Battery else Palette.TextLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = if (on) {
            "Live updates run only while Home Dragon sees your home screen on top, unlocked, with no keyboard or notification shade. " +
                "The Debug tab shows what it reports."
        } else {
            "Updates stay paused until Home Dragon answers. Open Home Dragon and make sure its icon finder is on."
        },
        color = Palette.TextLabel,
        fontSize = 13.sp,
    )
    if (!on) {
        TextButton(onClick = onOpenSettings) {
            Text("Open Home Dragon", color = Palette.Battery)
        }
    }
}

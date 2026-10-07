package dev.ramim.phonestatus.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import dev.ramim.phonestatus.data.BatteryInfo
import dev.ramim.phonestatus.data.DisplayInfo
import dev.ramim.phonestatus.data.RamInfo
import dev.ramim.phonestatus.data.RootState
import dev.ramim.phonestatus.data.Stats
import dev.ramim.phonestatus.data.StorageInfo
import dev.ramim.phonestatus.data.ThermalInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/** Counts real frames the screen shows, so you see 120 / 90 / 60 Hz as it actually runs. */
@Composable
fun rememberLiveHz(): State<Float> {
    val hz = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var windowStart = 0L
        var frames = 0
        while (true) {
            val t = withFrameNanos { it }
            if (windowStart == 0L) {
                windowStart = t
            } else {
                frames++
                val dt = t - windowStart
                if (dt >= 1_000_000_000L) {
                    hz.floatValue = frames * 1_000_000_000f / dt
                    frames = 0
                    windowStart = t
                }
            }
        }
    }
    return hz
}

@Composable
fun Dashboard(
    stats: Stats,
    thermal: ThermalInfo,
    liveHz: Float,
    onAddWidget: () -> Unit,
    onRetryRoot: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.BgBottom))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header()
            BatteryCard(stats.battery)
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RamCard(stats.ram, Modifier.weight(1f).fillMaxHeight())
                StorageCard(stats.storage, Modifier.weight(1f).fillMaxHeight())
            }
            DisplayCard(stats.display, liveHz)
            ThermalCard(stats.battery.tempC, thermal, onRetryRoot)
            Button(
                onClick = onAddWidget,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Palette.Battery,
                    contentColor = Color(0xFF05060C),
                ),
            ) {
                Text("Add widget to home screen", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            "Phone Status",
            color = Palette.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        Text(
            "$maker ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
            color = Palette.TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BatteryCard(b: BatteryInfo) {
    val ringColor = if (b.level <= 15 && !b.charging) Palette.BatteryLow else Palette.Battery
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(fraction = b.level / 100f, color = ringColor, size = 136.dp, stroke = 13.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${b.level}%",
                        color = Palette.TextPrimary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (b.charging) "⚡ Charging" else "Discharging",
                        color = ringColor,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionLabel("BATTERY", Palette.Battery)
                StatLine("Temperature", "${f1(b.tempC)} °C", tempColor(b.tempC, 60f))
                StatLine("Voltage", String.format(java.util.Locale.US, "%.2f V", b.voltageV))
                StatLine(
                    "Current",
                    b.currentMa?.let { (if (b.charging) "↑ " else "↓ ") + "$it mA" } ?: "—",
                )
                StatLine("Health", b.health)
                StatLine("Source", b.plug)
                b.capacityMah?.let { StatLine("Capacity", "~${fmtMah(it)}") }
                b.designMah?.let { StatLine("Design", fmtMah(it)) }
                b.healthPct?.let { StatLine("Capacity health", "$it%") }
                b.cycleCount?.let { StatLine("Charge cycles", "$it") }
            }
        }
    }
}

@Composable
private fun RamCard(r: RamInfo, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        SectionLabel("RAM", Palette.Ram)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingGauge(fraction = r.fraction, color = Palette.Ram, size = 104.dp, stroke = 10.dp) {
                Text(
                    "${(r.fraction * 100).roundToInt()}%",
                    color = Palette.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${fmtGb(r.usedBytes)} / ${fmtGb(r.totalBytes)}",
                color = Palette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            if (r.swapTotalBytes > 0) {
                Text(
                    "Swap ${fmtGb(r.swapUsedBytes)} / ${fmtGb(r.swapTotalBytes)}",
                    color = Palette.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun StorageCard(s: StorageInfo, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        SectionLabel("STORAGE", Palette.Storage)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingGauge(fraction = s.fraction, color = Palette.Storage, size = 104.dp, stroke = 10.dp) {
                Text(
                    "${(s.fraction * 100).roundToInt()}%",
                    color = Palette.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${fmtGb(s.usedBytes, 1000.0)} / ${fmtGb(s.totalBytes, 1000.0)}",
                color = Palette.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${fmtGb(s.freeBytes, 1000.0)} free",
                color = Palette.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DisplayCard(d: DisplayInfo, liveHz: Float) {
    GlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("DISPLAY", Palette.Display)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (liveHz > 0f) "${liveHz.roundToInt()} Hz" else "… Hz",
                    color = Palette.Display,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("live, measured", color = Palette.TextSecondary, fontSize = 12.sp)
            }
            Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatLine("System mode", "${d.currentHz.roundToInt()} Hz")
                StatLine("Resolution", if (d.width > 0) "${d.width}×${d.height}" else "—")
                StatLine("Density", if (d.dpi > 0) "${d.dpi} dpi" else "—")
            }
        }
        if (d.supportedHz.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                d.supportedHz.forEach { hz ->
                    Pill("$hz Hz", Palette.Display, filled = abs(hz - d.currentHz) < 1f)
                }
            }
        }
    }
}

@Composable
private fun ThermalCard(batteryTempC: Float, t: ThermalInfo, onRetryRoot: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("TEMP & CLOCKS", Palette.TempWarm)
            when {
                t.viaRoot -> Pill("ROOT", Palette.Battery, filled = true)
                t.sensorCount > 0 -> Pill("NO ROOT", Palette.TextSecondary)
                else -> Pill(
                    if (t.rootState == RootState.UNKNOWN) "CHECKING…" else "NO ACCESS",
                    Palette.TempHot,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TempBar("CPU", t.cpuC, 95f)
            TempBar("GPU", t.gpuC, 95f)
            TempBar("Battery", batteryTempC, 60f)
            t.hottest?.let {
                StatLine("Hottest sensor", "${it.name} · ${f1(it.celsius)} °C", tempColor(it.celsius, 95f))
            }
            if (t.freq.cpuCoresMhz.isNotEmpty()) {
                StatLine("CPU clock (peak)", fmtFreq(t.freq.cpuPeakMhz), Palette.Display)
                if (t.freq.cpuCoresMhz.isNotEmpty()) {
                    StatLine(
                        "CPU cores (GHz)",
                        t.freq.cpuCoresMhz.joinToString(" ") { mhz ->
                            if (mhz == null) "–" else f1(mhz / 1000f)
                        },
                    )
                }
            }
        }
        if (t.sensorCount == 0 && t.rootState == RootState.DENIED) {
            Spacer(Modifier.height(6.dp))
            Text(
                "CPU/GPU sensors are blocked. Grant root to Phone Status in your root manager, then retry.",
                color = Palette.TextSecondary,
                fontSize = 12.sp,
            )
            TextButton(onClick = onRetryRoot) {
                Text("Retry root access", color = Palette.Display)
            }
        }
    }
}

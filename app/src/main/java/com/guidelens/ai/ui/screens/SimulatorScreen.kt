package com.guidelens.ai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guidelens.ai.engine.TaskEngine
import com.guidelens.ai.model.GuideTask
import com.guidelens.ai.ui.theme.*

@Composable
fun SimulatorScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val taskEngine = TaskEngine.getInstance(context)
    val engineState by taskEngine.engineState.collectAsState()

    var isDarkModeEnabled by remember { mutableStateOf(false) }
    var currentSubPage by remember { mutableStateOf("MAIN_SETTINGS") }
    var selectedTask by remember { mutableStateOf(GuideTask.PREDEFINED_TASKS.first()) }
    var showTaskPicker by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sim_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale_sim"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkModeEnabled) Color(0xFF0F172A) else Color(0xFFF1F5F9))
    ) {

        // ── Top toolbar ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDarkModeEnabled) Color(0xFF1E293B) else Color.White)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                    tint = if (isDarkModeEnabled) Color.White else Color.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Simulyator", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    color = if (isDarkModeEnabled) Color.White else Color.Black)
                Text("Ruxsatlarsiz interaktiv test", fontSize = 11.sp, color = Color.Gray)
            }
            OutlinedButton(
                onClick = { showTaskPicker = !showTaskPicker },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Vazifa", fontSize = 11.sp, color = AccentCyan)
                Spacer(Modifier.width(4.dp))
                Icon(if (showTaskPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = AccentCyan, modifier = Modifier.size(16.dp))
            }
        }

        // ── Task picker ────────────────────────────────────────────────────
        if (showTaskPicker) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkModeEnabled) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(GuideTask.PREDEFINED_TASKS) { task ->
                    val isSelected = task.id == selectedTask.id
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) PrimaryIndigo else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, PrimaryIndigo.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .clickable {
                                selectedTask = task
                                showTaskPicker = false
                                currentSubPage = "MAIN_SETTINGS"
                                isDarkModeEnabled = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            task.titleUz, fontSize = 11.sp,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ── Guidance banner ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaryIndigo)
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🤖 GuideLens AI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                    if (engineState.stepProgressText.isNotBlank()) {
                        Text(engineState.stepProgressText, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                Text(engineState.instructionUz, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (!engineState.isRunning) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { taskEngine.startTask(selectedTask) },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("\"${selectedTask.titleUz}\" Boshlash", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                if (engineState.isRunning && engineState.totalSteps > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (engineState.currentStepIndex + 1).toFloat() / engineState.totalSteps },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = AccentCyan,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // ── Simulated phone screen ─────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(14.dp)) {
            when (currentSubPage) {
                "MAIN_SETTINGS" -> MainSettingsPage(
                    engineState = engineState,
                    isDarkModeEnabled = isDarkModeEnabled,
                    pulseScale = pulseScale,
                    onDisplayClick = {
                        currentSubPage = "DISPLAY_SETTINGS"
                        taskEngine.simulateUserActionClick("Ekran (Display)")
                    }
                )
                "DISPLAY_SETTINGS" -> DisplaySettingsPage(
                    isDarkModeEnabled = isDarkModeEnabled,
                    engineState = engineState,
                    pulseScale = pulseScale,
                    onBack = { currentSubPage = "MAIN_SETTINGS" },
                    onDarkModeToggle = { checked ->
                        isDarkModeEnabled = checked
                        taskEngine.simulateUserActionClick("Qorong'u rejim")
                    }
                )
            }
        }
    }
}

@Composable
private fun MainSettingsPage(
    engineState: com.guidelens.ai.engine.EngineState,
    isDarkModeEnabled: Boolean,
    pulseScale: Float,
    onDisplayClick: () -> Unit
) {
    val items = listOf(
        Triple(Icons.Default.Wifi, "Wi-Fi", "Ulangan: Home_5G"),
        Triple(Icons.Default.Bluetooth, "Bluetooth", "Yoqilgan"),
        Triple(Icons.Default.DisplaySettings, "Ekran (Display)", "Yorqinlik, Dark mode, Shrift"),
        Triple(Icons.AutoMirrored.Filled.VolumeUp, "Ovoz va tebranish", "Media: 80%"),
        Triple(Icons.Default.Notifications, "Bildirishnomalar", "Barcha ilovalar uchun yoqilgan"),
        Triple(Icons.Default.Security, "Xavfsizlik", "Barmoq izi, PIN"),
        Triple(Icons.Default.Language, "Til", "O'zbek"),
        Triple(Icons.Default.Battery5Bar, "Batareya", "76% — tejash rejimi o'chirilgan"),
        Triple(Icons.Default.Storage, "Xotira", "12.4 GB bo'sh")
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.size) { idx ->
            val (icon, title, subtitle) = items[idx]
            val isTarget = engineState.currentStepIndex == 1 && engineState.isRunning &&
                    title == "Ekran (Display)"

            Box(modifier = Modifier.fillMaxWidth()) {
                SimSettingsItem(
                    icon = icon,
                    title = title,
                    subtitle = subtitle,
                    isDark = isDarkModeEnabled,
                    isHighlighted = isTarget
                ) { if (title == "Ekran (Display)") onDisplayClick() }

                if (isTarget) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .scale(pulseScale)
                                .background(WarningRose, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) { Text("👇 SHUNI BOSING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplaySettingsPage(
    isDarkModeEnabled: Boolean,
    engineState: com.guidelens.ai.engine.EngineState,
    pulseScale: Float,
    onBack: () -> Unit,
    onDarkModeToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onBack() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                tint = if (isDarkModeEnabled) Color.White else Color.Black)
            Spacer(Modifier.width(8.dp))
            Text("Ekran Sozlamalari", fontWeight = FontWeight.Bold, fontSize = 17.sp,
                color = if (isDarkModeEnabled) Color.White else Color.Black)
        }

        val isTarget = engineState.currentStepIndex == 2 && engineState.isRunning
        Box(modifier = Modifier.fillMaxWidth()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkModeEnabled) Color(0xFF1E293B) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        if (isTarget) 3.dp else 0.dp,
                        if (isTarget) WarningRose else Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, null, tint = PrimaryIndigo)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Qorong'u rejim (Dark mode)", fontWeight = FontWeight.Bold,
                                color = if (isDarkModeEnabled) Color.White else Color.Black)
                            Text(if (isDarkModeEnabled) "Yoqilgan" else "O'chirilgan",
                                fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    Switch(checked = isDarkModeEnabled, onCheckedChange = onDarkModeToggle)
                }
            }
            if (isTarget) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .scale(pulseScale)
                            .background(WarningRose, RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("👇 TUGMANI YOQING!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }

        // Other display settings (non-interactive)
        listOf("Yorqinlik: 70%", "Shrift o'lchami: O'rta", "Ekran o'chishi: 1 daqiqa").forEach { item ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDarkModeEnabled) Color(0xFF1E293B) else Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(item, modifier = Modifier.padding(14.dp), fontSize = 13.sp,
                    color = if (isDarkModeEnabled) Color.White else Color.Black)
            }
        }
    }
}

@Composable
private fun SimSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDark: Boolean,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1E293B) else Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                if (isHighlighted) 2.dp else 0.dp,
                if (isHighlighted) WarningRose else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(PrimaryIndigo.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color.Black, fontSize = 13.sp)
                Text(subtitle, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

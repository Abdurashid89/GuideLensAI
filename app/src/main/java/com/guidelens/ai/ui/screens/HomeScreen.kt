package com.guidelens.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.guidelens.ai.ai.DiagnosisResult
import com.guidelens.ai.ai.UniversalAIEngine
import com.guidelens.ai.audio.VoiceInputManager
import com.guidelens.ai.engine.TaskEngine
import com.guidelens.ai.engine.UserMode
import com.guidelens.ai.model.GuideTask
import com.guidelens.ai.service.GuideAccessibilityService
import com.guidelens.ai.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(onNavigateToSimulator: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taskEngine = TaskEngine.getInstance(context)
    val engineState by taskEngine.engineState.collectAsState()

    val prefs = remember { context.getSharedPreferences("guidelens_prefs", Context.MODE_PRIVATE) }
    val aiEngine = remember { UniversalAIEngine() }
    val voiceManager = remember { VoiceInputManager(context) }
    val isListening by voiceManager.isListening.collectAsState()
    val transcribedText by voiceManager.transcribedText.collectAsState()

    var userPrompt by remember { mutableStateOf("") }
    var currentDiagnosis by remember { mutableStateOf<DiagnosisResult?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "").orEmpty()) }
    var showApiKeyField by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showTaskGrid by remember { mutableStateOf(false) }

    var isAccessibilityEnabled by remember { mutableStateOf(GuideAccessibilityService.isServiceRunning) }
    var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    // Re-check permissions when user returns from Settings (every 500ms)
    LaunchedEffect(Unit) {
        while (true) {
            isAccessibilityEnabled = GuideAccessibilityService.isServiceRunning
            isOverlayEnabled = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(500)
        }
    }

    // Load saved API key and apply to engine
    LaunchedEffect(Unit) {
        val saved = prefs.getString("gemini_api_key", "").orEmpty()
        if (saved.isNotBlank()) {
            apiKey = saved
            aiEngine.geminiClient.updateApiKey(saved)
            taskEngine.updateApiKey(saved)
        }
    }

    // Sync API key changes
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            prefs.edit().putString("gemini_api_key", apiKey).apply()
            aiEngine.geminiClient.updateApiKey(apiKey)
            taskEngine.updateApiKey(apiKey)
        }
    }

    // Voice transcription handler
    LaunchedEffect(transcribedText) {
        if (transcribedText.isNotBlank()) {
            userPrompt = transcribedText
            isAnalyzing = true
            scope.launch {
                currentDiagnosis = aiEngine.analyzeUserProblem(transcribedText)
                isAnalyzing = false
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) voiceManager.startListening() }

    DisposableEffect(Unit) { onDispose { voiceManager.destroy() } }

    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "mic_scale"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(DarkBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ── Header ─────────────────────────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))))
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartDisplay, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("GuideLens AI", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Telefon Ustasi", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                        Spacer(Modifier.weight(1f))
                        // Mode toggle chip
                        val mode = engineState.userMode
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                .clickable {
                                    taskEngine.setUserMode(
                                        if (mode == UserMode.GUIDE) UserMode.AUTO else UserMode.GUIDE
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                if (mode == UserMode.AUTO) "🤖 AUTO" else "👁️ GUIDE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Muammoni yozing yoki ovoz bilan ayting — AI ekranda nima bosishni ko'rsatadi yoki o'zi bajaradi.",
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        // ── Permissions warning ────────────────────────────────────────────
        if (!isAccessibilityEnabled || !isOverlayEnabled) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1917)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, WarningRose.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, tint = WarningRose, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ruxsatlar kerak", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        if (!isAccessibilityEnabled) {
                            PermissionRow("Accessibility Service", false) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        }
                        if (!isOverlayEnabled) {
                            PermissionRow("Overlay ruxsati (boshqa ilovalar ustida ko'rsatish)", false) {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Xiaomi/Samsung floating window hint ───────────────────────────
        if (isAccessibilityEnabled && isOverlayEnabled) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Xiaomi/Samsung/OPPO telefonlarda: Sozlamalar → Ilovalar → GuideLens AI → Ruxsatlar → Boshqa ilovalar ustida ko'rsatish — yoqib qo'ying.",
                            fontSize = 11.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // ── AI Prompt input ────────────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth().border(1.dp, PrimaryIndigo.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Nima muammo yoki qanday yordam kerak?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = userPrompt,
                            onValueChange = { userPrompt = it },
                            placeholder = { Text("Masalan: Telegram o'rnatishga yordam ber...", color = TextSecondary, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .scale(if (isListening) micScale else 1f)
                                .background(if (isListening) WarningRose else PrimaryIndigo, CircleShape)
                                .clickable {
                                    if (isListening) voiceManager.stopListening()
                                    else {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                                            voiceManager.startListening()
                                        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                null, tint = Color.White, modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Tahlil qilish tugmasi
                    Button(
                        onClick = {
                            if (userPrompt.isNotBlank()) {
                                isAnalyzing = true
                                currentDiagnosis = null
                                scope.launch {
                                    currentDiagnosis = aiEngine.analyzeUserProblem(userPrompt)
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        enabled = userPrompt.isNotBlank() && !isAnalyzing
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isAnalyzing) "AI tahlil qilmoqda..." else "Tahlil Qilish va Yechim Topish",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Agent rejimi tugmasi
                    val agentState by taskEngine.engineState.collectAsState()
                    if (agentState.isAgentMode && agentState.isRunning) {
                        // Agent ishlayotganda — to'xtatish tugmasi
                        Button(
                            onClick = { taskEngine.stopAgent() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                        ) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Agentni To'xtatish", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (userPrompt.isNotBlank()) {
                                    taskEngine.startAgent(userPrompt)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF007AFF),
                                disabledContainerColor = Color(0xFF007AFF).copy(alpha = 0.4f)
                            ),
                            enabled = userPrompt.isNotBlank() && isAccessibilityEnabled
                        ) {
                            Icon(Icons.Default.SmartDisplay, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "🤖 Agent Rejimida Bajar",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        if (!isAccessibilityEnabled) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Agent uchun Accessibility Service kerak",
                                fontSize = 10.sp, color = Color(0xFFF43F5E)
                            )
                        }
                    }

                    if (isListening) {
                        Spacer(Modifier.height(8.dp))
                        Text("🎙️ Gapiring... (eshitilmoqda)", color = WarningRose, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // ── Quick suggestions ──────────────────────────────────────────────
        item {
            val chips = listOf(
                "📱 Telegram o'rnat", "💬 WhatsApp o'rnat",
                "🌙 Dark mode yoq", "📶 Wi-Fi muammo",
                "📡 Bluetooth ulash", "🔥 Hotspot yoq",
                "🔡 Shrift kattalashtir", "🗑️ Xotira tozala",
                "🔔 Bildirishnoma o'chir", "🔒 Parol o'rnat",
                "🔋 Batareya tejash", "▶️ YouTube video yukla"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips) { chip ->
                    Box(
                        modifier = Modifier
                            .background(SurfaceCard, RoundedCornerShape(20.dp))
                            .border(1.dp, PrimaryIndigo.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .clickable {
                                userPrompt = chip
                                isAnalyzing = true
                                currentDiagnosis = null
                                scope.launch {
                                    currentDiagnosis = aiEngine.analyzeUserProblem(chip)
                                    isAnalyzing = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(chip, fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }

        // ── Diagnosis card ─────────────────────────────────────────────────
        currentDiagnosis?.let { diag ->
            item {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, if (diag.isAiGenerated) AccentCyan else Color(0xFF6366F1), RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (diag.isAiGenerated) Icons.Default.AutoAwesome else Icons.Default.Info,
                                    null, tint = if (diag.isAiGenerated) AccentCyan else Color(0xFF818CF8)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(diag.problemTitleUz, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                            }

                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x33F43F5E), RoundedCornerShape(12.dp))
                                    .border(1.dp, WarningRose.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(diag.rootCauseUz, fontSize = 12.sp, color = Color(0xFFFECDD3))
                            }

                            Spacer(Modifier.height(12.dp))
                            Text("Rejalashtirilgan qadamlar:", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            diag.task.steps.forEach { step ->
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(22.dp).background(PrimaryIndigo, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Text("${step.stepId}", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                                    Spacer(Modifier.width(8.dp))
                                    Text(step.titleUz, fontSize = 12.sp, color = TextPrimary)
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { taskEngine.startDynamicTaskWithDiagnosis(diag) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Boshlash", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Predefined task grid header ────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tayyor Vazifalar", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                TextButton(onClick = { showTaskGrid = !showTaskGrid }) {
                    Text(if (showTaskGrid) "Yopish" else "Barchasini Ko'rsatish", color = AccentCyan, fontSize = 12.sp)
                }
            }
        }

        // ── Predefined task grid ───────────────────────────────────────────
        if (showTaskGrid) {
            val tasks = GuideTask.PREDEFINED_TASKS
            items(tasks.size / 2 + tasks.size % 2) { rowIndex ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val idx1 = rowIndex * 2
                    val idx2 = idx1 + 1
                    TaskCard(task = tasks[idx1], modifier = Modifier.weight(1f)) {
                        taskEngine.startTask(tasks[idx1])
                    }
                    if (idx2 < tasks.size) {
                        TaskCard(task = tasks[idx2], modifier = Modifier.weight(1f)) {
                            taskEngine.startTask(tasks[idx2])
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            // Show only first 4 as a quick row
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(GuideTask.PREDEFINED_TASKS.take(6)) { task ->
                        TaskCard(
                            task = task,
                            modifier = Modifier.width(150.dp)
                        ) { taskEngine.startTask(task) }
                    }
                }
            }
        }

        // ── Gemini API Key section ─────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .clickable { showApiKeyField = !showApiKeyField }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (apiKey.isNotBlank()) SuccessEmerald.copy(alpha = 0.2f)
                                else Color(0xFF334155), CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Key, null,
                            tint = if (apiKey.isNotBlank()) SuccessEmerald else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Gemini API Key",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                        )
                        Text(
                            if (apiKey.isNotBlank()) "✅ Ulangan — AI to'liq ishlaydi"
                            else "❌ Ulangan emas — asosiy rejim",
                            fontSize = 11.sp, color = if (apiKey.isNotBlank()) SuccessEmerald else TextSecondary
                        )
                    }
                    Icon(
                        if (showApiKeyField) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = TextSecondary
                    )
                }

                AnimatedVisibility(visible = showApiKeyField) {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("Gemini API Key", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(
                                        if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        null, tint = TextSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "aistudio.google.com da bepul API key oling",
                            fontSize = 11.sp, color = TextSecondary
                        )
                    }
                }
            }
        }

        // ── Simulator card ─────────────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .clickable { onNavigateToSimulator() }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(AccentCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.SmartDisplay, null, tint = AccentCyan) }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interaktiv Simulyator", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Ruxsatlarsiz overlay va pointer'ni sinash", fontSize = 11.sp, color = TextSecondary)
                    }
                    Button(
                        onClick = onNavigateToSimulator,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) { Text("Ochish", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TaskCard(task: GuideTask, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = modifier
            .border(1.dp, PrimaryIndigo.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PrimaryIndigo.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(task.titleUz, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text("${task.steps.size} qadam", fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun PermissionRow(title: String, isGranted: Boolean, onGrantClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = if (isGranted) SuccessEmerald else WarningRose,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, fontSize = 12.sp, color = TextSecondary)
        }
        if (!isGranted) {
            OutlinedButton(
                onClick = onGrantClick,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) { Text("Yoqish", fontSize = 11.sp, color = AccentCyan) }
        }
    }
}

package com.guidelens.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.guidelens.ai.R
import com.guidelens.ai.audio.TTSManager
import com.guidelens.ai.engine.TaskEngine
import com.guidelens.ai.engine.UserMode

class GuideOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager

    // Window 1: full-screen, NOT TOUCHABLE — highlight box & scroll indicator
    private var highlightView: ComposeView? = null

    // Window 2: bottom pill, TOUCHABLE — instruction text, X button
    private var pillView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "guidelens_overlay_channel"
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
        setupHighlightWindow()
        setupPillWindow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "GuideLens Overlay", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GuideLens AI Faol")
            .setContentText("Ko'rsatmalar ekranda ko'rinmoqda...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun windowType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ── Window 1: full-screen, NOT touchable ─────────────────────────────────
    // Highlight box and scroll indicator are purely visual.
    // Touch events pass through so the user can tap Settings/other app elements freely.
    private fun setupHighlightWindow() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        highlightView = buildComposeView {
            MaterialTheme { HighlightContent() }
        }
        try { windowManager.addView(highlightView, params) } catch (e: Exception) { e.printStackTrace() }
    }

    // ── Window 2: bottom pill, TOUCHABLE ─────────────────────────────────────
    // Only the pill area intercepts touches; everything above passes to the app below.
    // IMPORTANT: use a fixed pixel height instead of WRAP_CONTENT — ComposeView has no
    // content size at addView() time so WRAP_CONTENT collapses to 0 px and nothing renders.
    private fun setupPillWindow() {
        val pillHeightPx = (210 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            pillHeightPx,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

        pillView = buildComposeView {
            MaterialTheme { PillContent() }
        }
        try { windowManager.addView(pillView, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun buildComposeView(content: @Composable () -> Unit): ComposeView =
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setViewTreeLifecycleOwner(this@GuideOverlayService)
            setViewTreeViewModelStoreOwner(this@GuideOverlayService)
            setViewTreeSavedStateRegistryOwner(this@GuideOverlayService)
            setContent { content() }
        }

    // ── Highlight window content (no touch) ───────────────────────────────────
    @Composable
    private fun HighlightContent() {
        val taskEngine = TaskEngine.getInstance(this@GuideOverlayService)
        val state by taskEngine.engineState.collectAsState()

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f, targetValue = 1.22f,
            animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scale"
        )
        val scrollArrowOffset by infiniteTransition.animateFloat(
            initialValue = 0f, targetValue = 12f,
            animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "arrow"
        )

        Box(modifier = Modifier.fillMaxSize()) {

            // Target highlight
            val target = state.targetElement
            if (target != null && !state.isCompleted) {
                val density = resources.displayMetrics.density
                val leftDp  = (target.bounds.left  / density).dp
                val topDp   = (target.bounds.top   / density).dp
                val widthDp = (target.bounds.width  / density).dp
                val heightDp= (target.bounds.height / density).dp

                val highlightColor = if (state.userMode == UserMode.AUTO)
                    Color(0xFF007AFF) else Color(0xFFE11D48)

                // Outer glow
                Box(
                    modifier = Modifier
                        .offset(x = leftDp - 4.dp, y = topDp - 4.dp)
                        .width(widthDp + 8.dp)
                        .height(heightDp + 8.dp)
                        .scale(pulseScale)
                        .background(highlightColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                )
                // Border highlight
                Box(
                    modifier = Modifier
                        .offset(x = leftDp, y = topDp)
                        .width(widthDp)
                        .height(heightDp)
                        .border(4.dp, highlightColor, RoundedCornerShape(10.dp))
                        .background(highlightColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                )
                // "Bu yerga bosing" badge
                val badgeText = if (state.userMode == UserMode.AUTO) "🤖 Avtomatik" else "👆 Bu yerga bosing"
                val badgeColor = if (state.userMode == UserMode.AUTO) Color(0xFF1D4ED8) else Color(0xFFDC2626)
                val badgeTopDp = if (topDp > 60.dp) topDp - 44.dp else topDp + heightDp + 6.dp

                Box(
                    modifier = Modifier
                        .offset(
                            x = (leftDp + widthDp / 2 - 85.dp).coerceAtLeast(8.dp),
                            y = badgeTopDp.coerceAtLeast(4.dp)
                        )
                        .background(badgeColor, RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(badgeText, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Scroll indicator
            if (state.shouldScroll && !state.isCompleted) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = scrollArrowOffset.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1D4ED8).copy(alpha = 0.92f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (state.scrollDirection >= 0) Icons.Default.KeyboardArrowDown
                                else Icons.Default.KeyboardArrowUp,
                                null, tint = Color.White, modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (state.scrollDirection >= 0) "Pastga suring" else "Yuqoriga suring",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Completion badge
            if (state.isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xFF059669), RoundedCornerShape(20.dp))
                        .padding(horizontal = 28.dp, vertical = 18.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✅", fontSize = 40.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Bajarildi!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
            }
        }
    }

    // ── Pill window content (touchable) ───────────────────────────────────────
    @Composable
    private fun PillContent() {
        val taskEngine = TaskEngine.getInstance(this@GuideOverlayService)
        val state by taskEngine.engineState.collectAsState()
        val tts = TTSManager.getInstance(this@GuideOverlayService)

        Box(modifier = Modifier.padding(bottom = 20.dp, start = 12.dp, end = 12.dp)) {
            if (state.isAgentMode) {
                AgentPill(state = state, onStop = { taskEngine.stopAgent() })
            } else {
                GuidePill(state = state, tts = tts, onStop = { taskEngine.stopTask() })
            }
        }
    }

    @Composable
    private fun AgentPill(
        state: com.guidelens.ai.engine.EngineState,
        onStop: () -> Unit
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "think")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "dot"
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF2030A1A)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0xFF007AFF).copy(alpha = 0.7f), RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // Header: Agent badge + goal + stop button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF007AFF), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("🤖 AGENT", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        state.agentGoal,
                        color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.agentIteration > 0) {
                        Text(
                            "${state.agentIteration}/25",
                            color = Color(0xFF38BDF8), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(onClick = onStop, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Thinking indicator or action message
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.agentThinking) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF007AFF).copy(alpha = dotAlpha), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        state.instructionUz,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp,
                        maxLines = 3
                    )
                }

                // Progress bar (iteration based)
                if (state.agentIteration > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.agentIteration / 25f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color(0xFF007AFF),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }
    }

    @Composable
    private fun GuidePill(
        state: com.guidelens.ai.engine.EngineState,
        tts: com.guidelens.ai.audio.TTSManager,
        onStop: () -> Unit
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF2080F1C)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // Progress bar
                if (state.totalSteps > 0 && state.isRunning) {
                    val progress = (state.currentStepIndex + 1).toFloat() / state.totalSteps
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.stepProgressText, color = Color(0xFF94A3B8), fontSize = 11.sp)
                        Box(
                            Modifier
                                .background(
                                    if (state.userMode == UserMode.AUTO) Color(0xFF007AFF) else Color(0xFF6366F1),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (state.userMode == UserMode.AUTO) "AUTO" else "GUIDE",
                                color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = Color(0xFF6366F1),
                        trackColor = Color(0xFF1E293B)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Navigation, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.activeTask?.titleUz ?: "GuideLens AI",
                            color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1
                        )
                        Text(
                            state.instructionUz,
                            color = Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, lineHeight = 19.sp, maxLines = 3
                        )
                    }
                    IconButton(
                        onClick = { tts.speak(state.instructionUz, overrideSame = true) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color(0xFFF43F5E), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        try { highlightView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        try { pillView?.let { windowManager.removeView(it) } } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

package com.guidelens.ai.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.guidelens.ai.agent.AgentLoop
import com.guidelens.ai.ai.DiagnosisResult
import com.guidelens.ai.ai.GeminiVisionClient
import com.guidelens.ai.audio.TTSManager
import com.guidelens.ai.model.ActionType
import com.guidelens.ai.model.GuideStep
import com.guidelens.ai.model.GuideTask
import com.guidelens.ai.model.ScreenState
import com.guidelens.ai.model.TargetElement
import com.guidelens.ai.model.TargetRect
import com.guidelens.ai.service.GuideAccessibilityService
import com.guidelens.ai.service.GuideOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UserMode { GUIDE, AUTO }

data class EngineState(
    // ── Guide / Auto task fields ───────────────────────────────────────────
    val activeTask: GuideTask? = null,
    val currentStep: GuideStep? = null,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val targetElement: TargetElement? = null,
    val diagnosisResult: DiagnosisResult? = null,
    val shouldScroll: Boolean = false,
    val scrollDirection: Int = 1,
    val stepProgressText: String = "",
    val isAutoExecuting: Boolean = false,

    // ── Agent mode fields ──────────────────────────────────────────────────
    val isAgentMode: Boolean = false,
    val agentGoal: String = "",
    val agentThinking: Boolean = false,
    val agentIteration: Int = 0,

    // ── Shared fields ──────────────────────────────────────────────────────
    val instructionUz: String = "GuideLens AI tayyor. Buyruq bering yoki mikrofon orqali gapiring.",
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val userMode: UserMode = UserMode.GUIDE
)

class TaskEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TaskEngine"
        private const val PREFS_NAME = "guidelens_prefs"
        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODE = "user_mode"

        @Volatile private var instance: TaskEngine? = null

        fun getInstance(context: Context): TaskEngine {
            return instance ?: synchronized(this) {
                instance ?: TaskEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val ttsManager = TTSManager.getInstance(context)
    private val geminiClient = GeminiVisionClient()
    private val scope = CoroutineScope(Dispatchers.Default)

    private var scrollAttemptCount = 0
    private var agentLoop: AgentLoop? = null

    private val _engineState = MutableStateFlow(EngineState(
        userMode = savedMode()
    ))
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    init {
        val savedKey = prefs().getString(KEY_API, "").orEmpty()
        if (savedKey.isNotBlank()) geminiClient.updateApiKey(savedKey)
    }

    // ── API key management ─────────────────────────────────────────────────

    fun updateApiKey(key: String) {
        geminiClient.updateApiKey(key)
        prefs().edit().putString(KEY_API, key).apply()
    }

    fun setUserMode(mode: UserMode) {
        prefs().edit().putString(KEY_MODE, mode.name).apply()
        _engineState.value = _engineState.value.copy(userMode = mode)
    }

    // ── Task lifecycle ─────────────────────────────────────────────────────

    fun startTask(task: GuideTask) {
        if (task.steps.isEmpty()) return
        scrollAttemptCount = 0
        val firstStep = task.steps.first()
        _engineState.value = EngineState(
            activeTask = task,
            currentStep = firstStep,
            currentStepIndex = 0,
            totalSteps = task.steps.size,
            instructionUz = firstStep.instructionUz,
            stepProgressText = "Qadam 1/${task.steps.size}",
            isRunning = true,
            userMode = _engineState.value.userMode
        )
        ttsManager.speak("Topshiriq boshlandi. ${firstStep.instructionUz}", overrideSame = true)
        openAppIfRequired(firstStep)
        startOverlayService()
    }

    fun startDynamicTaskWithDiagnosis(diagnosis: DiagnosisResult) {
        val task = diagnosis.task
        if (task.steps.isEmpty()) return
        scrollAttemptCount = 0
        val firstStep = task.steps.first()
        _engineState.value = EngineState(
            activeTask = task,
            currentStep = firstStep,
            currentStepIndex = 0,
            totalSteps = task.steps.size,
            instructionUz = firstStep.instructionUz,
            stepProgressText = "Qadam 1/${task.steps.size}",
            diagnosisResult = diagnosis,
            isRunning = true,
            userMode = _engineState.value.userMode
        )
        val voice = "${diagnosis.problemTitleUz}. Qadam 1: ${firstStep.instructionUz}"
        ttsManager.speak(voice, overrideSame = true)
        openAppIfRequired(firstStep)
        startOverlayService()
    }

    fun stopTask() {
        scrollAttemptCount = 0
        agentLoop?.stop()
        agentLoop = null
        _engineState.value = EngineState(userMode = _engineState.value.userMode)
        ttsManager.speak("Topshiriq to'xtatildi.", overrideSame = true)
        stopOverlayService()
    }

    // ── Agent mode ─────────────────────────────────────────────────────────

    fun startAgent(goal: String) {
        if (goal.isBlank()) return

        if (!geminiClient.hasApiKey()) {
            _engineState.value = _engineState.value.copy(
                instructionUz = "❌ Gemini API key kiritilmagan. Asosiy ekranda 'Gemini API Key' bo'limini oching va key qo'shing."
            )
            return
        }

        agentLoop?.stop()

        _engineState.value = EngineState(
            isAgentMode = true,
            agentGoal = goal,
            agentThinking = true,
            instructionUz = "🤖 Agent ishga tushmoqda...",
            isRunning = true,
            userMode = _engineState.value.userMode
        )
        startOverlayService()

        agentLoop = AgentLoop(
            context = context,
            geminiClient = geminiClient,
            onUpdate = { thinking, message, iteration ->
                _engineState.value = _engineState.value.copy(
                    agentThinking = thinking,
                    instructionUz = message,
                    agentIteration = iteration
                )
            },
            onComplete = { success, message ->
                _engineState.value = _engineState.value.copy(
                    agentThinking = false,
                    instructionUz = message,
                    isRunning = false,
                    isCompleted = success,
                    isAgentMode = false
                )
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    stopOverlayService()
                }
            }
        )
        scope.launch { agentLoop?.start(goal) }
    }

    fun stopAgent() {
        agentLoop?.stop()
        agentLoop = null
        _engineState.value = EngineState(userMode = _engineState.value.userMode)
        stopOverlayService()
    }

    // ── Screen state handling ──────────────────────────────────────────────

    fun onNewScreenState(screenState: ScreenState) {
        val state = _engineState.value
        if (!state.isRunning || state.isCompleted || state.currentStep == null) return
        if (state.isAutoExecuting) return  // auto-tap in progress, ignore events briefly

        val step = state.currentStep
        val target = screenState.findElement(*step.targetKeywords.toTypedArray())

        if (target != null) {
            scrollAttemptCount = 0
            _engineState.value = state.copy(
                targetElement = target,
                instructionUz = step.instructionUz,
                shouldScroll = false
            )
            ttsManager.speak(step.instructionUz)

            // AUTO mode: tap or type automatically
            if (state.userMode == UserMode.AUTO) {
                when {
                    step.actionType == ActionType.CLICK_TARGET -> performAutoTap(target)
                    step.actionType == ActionType.INPUT_TEXT && !step.textToType.isNullOrBlank() ->
                        performAutoType(step.textToType)
                }
            }
        } else {
            scrollAttemptCount++
            when {
                scrollAttemptCount <= 2 -> {
                    val msg = "🔍 '${step.titleUz}' qidirilyapti..."
                    _engineState.value = state.copy(
                        targetElement = null,
                        instructionUz = msg,
                        shouldScroll = true,
                        scrollDirection = 1
                    )
                    ttsManager.speak(msg)
                }
                scrollAttemptCount <= 4 -> {
                    val msg = "🔍 Yuqorida ham tekshirilmoqda..."
                    _engineState.value = state.copy(
                        targetElement = null,
                        instructionUz = msg,
                        shouldScroll = true,
                        scrollDirection = -1
                    )
                    ttsManager.speak(msg)
                }
                else -> {
                    scrollAttemptCount = 0
                    tryGeminiFallback(step.instructionUz, screenState, state)
                }
            }
        }
    }

    private fun tryGeminiFallback(
        goalUz: String,
        screenState: ScreenState,
        currentState: EngineState
    ) {
        scope.launch {
            val result = geminiClient.analyzeScreenState(
                goalUz, screenState,
                currentState.currentStepIndex,
                currentState.totalSteps
            )
            withContext(Dispatchers.Main) {
                val altTarget = result.targetText?.let { txt ->
                    screenState.elements.firstOrNull { elem ->
                        elem.displayText().lowercase().contains(txt.lowercase()) ||
                                txt.lowercase().contains(elem.displayText().lowercase())
                    }
                }
                _engineState.value = currentState.copy(
                    targetElement = altTarget,
                    instructionUz = result.instructionUz,
                    shouldScroll = result.shouldScroll && altTarget == null
                )
                ttsManager.speak(result.instructionUz)

                if (altTarget != null && currentState.userMode == UserMode.AUTO &&
                    currentState.currentStep?.actionType == ActionType.CLICK_TARGET) {
                    performAutoTap(altTarget)
                }
            }
        }
    }

    // ── Step completion ────────────────────────────────────────────────────

    fun simulateUserActionClick(@Suppress("UNUSED_PARAMETER") clickedText: String) {
        val state = _engineState.value
        if (!state.isRunning || state.activeTask == null) return
        advanceToNextStep(state)
    }

    private fun advanceToNextStep(state: EngineState) {
        val task = state.activeTask ?: return
        val nextIndex = state.currentStepIndex + 1

        if (nextIndex < task.steps.size) {
            val nextStep = task.steps[nextIndex]
            scrollAttemptCount = 0
            _engineState.value = state.copy(
                currentStep = nextStep,
                currentStepIndex = nextIndex,
                targetElement = null,
                instructionUz = nextStep.instructionUz,
                stepProgressText = "Qadam ${nextIndex + 1}/${task.steps.size}",
                isAutoExecuting = false
            )
            ttsManager.speak("Barakalla! Keyingi qadam: ${nextStep.instructionUz}", overrideSame = true)
            openAppIfRequired(nextStep)
        } else {
            scrollAttemptCount = 0
            _engineState.value = state.copy(
                targetElement = null,
                instructionUz = "Tabriklaymiz! Topshiriq muvaffaqiyatli yakunlandi!",
                stepProgressText = "Bajarildi ✅",
                isCompleted = true,
                isRunning = false,
                isAutoExecuting = false
            )
            ttsManager.speak("Tabriklaymiz! Topshiriq yakunlandi!", overrideSame = true)
            stopOverlayService()
        }
    }

    // ── Auto-tap (AUTO mode) ───────────────────────────────────────────────

    private fun performAutoTap(target: TargetElement) {
        val state = _engineState.value
        _engineState.value = state.copy(isAutoExecuting = true, instructionUz = "🤖 Avtomatik bajarilmoqda...")

        scope.launch {
            kotlinx.coroutines.delay(600)  // brief delay so user sees the highlight
            withContext(Dispatchers.Main) {
                val currentState = _engineState.value
                if (currentState.isAutoExecuting) {
                    // Notify accessibility service to perform the click
                    AutoTapBus.requestTap(target)
                    kotlinx.coroutines.delay(400)
                    advanceToNextStep(currentState)
                }
            }
        }
    }

    private fun performAutoType(text: String) {
        val state = _engineState.value
        _engineState.value = state.copy(isAutoExecuting = true, instructionUz = "🤖 '$text' yozilmoqda...")
        scope.launch {
            kotlinx.coroutines.delay(400)
            withContext(Dispatchers.Main) {
                val typed = GuideAccessibilityService.getInstance()?.performTypeText(text) ?: false
                val currentState = _engineState.value
                if (currentState.isAutoExecuting) {
                    if (typed) {
                        kotlinx.coroutines.delay(300)
                        advanceToNextStep(currentState)
                    } else {
                        _engineState.value = currentState.copy(
                            isAutoExecuting = false,
                            instructionUz = "⌨️ '$text' deb yozing, so'ng davom eting."
                        )
                    }
                }
            }
        }
    }

    // ── Simulator helpers ──────────────────────────────────────────────────

    fun simulateSetTarget(text: String, rect: TargetRect) {
        val state = _engineState.value
        if (!state.isRunning) return
        _engineState.value = state.copy(
            targetElement = TargetElement(
                id = "sim_node", text = text, bounds = rect, isClickable = true
            ),
            shouldScroll = false
        )
    }

    // ── App launch ────────────────────────────────────────────────────────

    fun openAppIfRequired(step: GuideStep) {
        if (step.actionType == ActionType.OPEN_APP && !step.appPackage.isNullOrEmpty()) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(step.appPackage)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent?.let { context.startActivity(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch ${step.appPackage}: ${e.localizedMessage}")
            }
        }
    }

    // ── Overlay service ────────────────────────────────────────────────────

    private fun startOverlayService() {
        val intent = Intent(context, GuideOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopOverlayService() {
        context.stopService(Intent(context, GuideOverlayService::class.java))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun savedMode(): UserMode = try {
        UserMode.valueOf(prefs().getString(KEY_MODE, UserMode.GUIDE.name)!!)
    } catch (_: Exception) { UserMode.GUIDE }
}

// Simple bus for auto-tap requests between TaskEngine and AccessibilityService
object AutoTapBus {
    var pendingTarget: TargetElement? = null

    fun requestTap(target: TargetElement) {
        pendingTarget = target
    }

    fun consumeTap(): TargetElement? {
        val t = pendingTarget
        pendingTarget = null
        return t
    }
}

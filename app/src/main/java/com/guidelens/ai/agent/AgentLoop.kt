package com.guidelens.ai.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.guidelens.ai.ai.GeminiVisionClient
import com.guidelens.ai.service.GuideAccessibilityService
import kotlinx.coroutines.delay

private const val TAG = "AgentLoop"
private const val MAX_ITERATIONS = 25

class AgentLoop(
    private val context: Context,
    private val geminiClient: GeminiVisionClient,
    private val onUpdate: (thinking: Boolean, message: String, iteration: Int) -> Unit,
    private val onComplete: (success: Boolean, message: String) -> Unit
) {

    private val history = mutableListOf<String>()

    @Volatile
    var isRunning = false
        private set

    suspend fun start(goal: String) {
        isRunning = true
        history.clear()
        var iteration = 0
        var lastActionKey = ""
        var repeatCount = 0

        while (isRunning && iteration < MAX_ITERATIONS) {
            iteration++

            val service = GuideAccessibilityService.getInstance()
            if (service == null) {
                onComplete(
                    false,
                    "❌ Accessibility Service ishlamayapti.\nSozlamalar → Maxsus imkoniyatlar → GuideLens yoqing."
                )
                isRunning = false
                return
            }

            // 1. PERCEIVE
            val elements = service.getScreenElements()
            val pkg = service.getCurrentPackage()
            Log.d(TAG, "Iteration $iteration | pkg=$pkg | elements=${elements.size}")

            // 2. PLAN
            onUpdate(true, "🤔 Ekranni o'rganmoqda... ($iteration/$MAX_ITERATIONS)", iteration)

            val action = geminiClient.decideNextAction(goal, elements, pkg, history)

            // null means "no API key" — stop immediately with clear message
            if (action == null) {
                Log.e(TAG, "API key yo'q — agent to'xtatiladi")
                onComplete(false, "❌ Gemini API key kiritilmagan.\nAsosiy ekranda 'Gemini API Key' bo'limini oching.")
                isRunning = false
                return
            }

            Log.d(TAG, "Action: ${action.type} | target=${action.targetText} | reason=${action.reasoning}")

            // DONE?
            if (action.type == AgentActionType.DONE) {
                onComplete(true, action.userMessage.ifBlank { "✅ Vazifa muvaffaqiyatli yakunlandi!" })
                isRunning = false
                return
            }

            // Loop detection: same action 3x in a row → scroll to break
            val actionKey = "${action.type}:${action.targetText ?: action.textToType ?: action.packageName}"
            if (actionKey == lastActionKey) {
                repeatCount++
                if (repeatCount >= 3) {
                    Log.w(TAG, "Loop detected ($actionKey x3), scrolling to break")
                    service.agentScrollDown()
                    repeatCount = 0
                    delay(1500)
                    continue
                }
            } else {
                repeatCount = 0
                lastActionKey = actionKey
            }

            // Record history
            val historyEntry = buildString {
                append(action.type.name)
                append("(")
                append((action.targetText ?: action.textToType ?: action.packageName ?: "").take(35))
                append(")")
            }
            history.add(historyEntry)

            // 3. ACT
            val displayMsg = action.userMessage.ifBlank { "⚡ ${action.type} bajarmoqda..." }
            onUpdate(false, displayMsg, iteration)

            when (action.type) {
                AgentActionType.CLICK -> {
                    val target = action.targetText.orEmpty()
                    val ok = service.clickElementByText(target)
                    Log.d(TAG, "CLICK '$target' → $ok")
                    if (!ok) history.add("FAILED:CLICK($target)")
                    delay(1500)
                }

                AgentActionType.TYPE -> {
                    val text = action.textToType.orEmpty()
                    service.performTypeText(text)
                    Log.d(TAG, "TYPE '$text'")
                    delay(1000)
                }

                AgentActionType.SCROLL_DOWN -> {
                    service.agentScrollDown()
                    delay(1200)
                }

                AgentActionType.SCROLL_UP -> {
                    service.agentScrollUp()
                    delay(1200)
                }

                AgentActionType.OPEN_APP -> {
                    val pkg2 = action.packageName.orEmpty()
                    launchApp(pkg2)
                    Log.d(TAG, "OPEN_APP '$pkg2'")
                    delay(2500)
                }

                AgentActionType.BACK -> {
                    service.performBack()
                    delay(1500)
                }

                AgentActionType.WAIT -> {
                    delay(2500)
                }

                AgentActionType.DONE -> { /* handled above */ }
            }
        }

        if (isRunning) {
            onComplete(false, "⏱️ $MAX_ITERATIONS ta harakatdan so'ng vazifa tugallanmadi. Qayta urining.")
        }
        isRunning = false
    }

    fun stop() {
        isRunning = false
    }

    private fun launchApp(packageName: String) {
        if (packageName.isBlank()) return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchApp $packageName: ${e.localizedMessage}")
        }
    }
}

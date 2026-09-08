package com.guidelens.ai.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.guidelens.ai.agent.AgentAction
import com.guidelens.ai.agent.AgentActionType
import com.guidelens.ai.model.ActionType
import com.guidelens.ai.model.GuideStep
import com.guidelens.ai.model.GuideTask
import com.guidelens.ai.model.ScreenState
import com.guidelens.ai.model.TargetElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiVisionClient(private var apiKey: String = "") {

    companion object {
        private const val TAG = "GeminiVisionClient"
        private const val API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun updateApiKey(key: String) { apiKey = key }
    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    data class GeminiResponse(
        val targetText: String?,
        val instructionUz: String,
        val shouldScroll: Boolean = false
    )

    // Called when keyword matching failed — ask AI to find element on current screen
    suspend fun analyzeScreenState(
        goalUz: String,
        screenState: ScreenState,
        stepIndex: Int = 0,
        totalSteps: Int = 1
    ): GeminiResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext fallbackLocalAnalysis(goalUz, screenState)

        try {
            val clickable = screenState.elements
                .filter { it.isClickable || it.isEditable || it.isScrollable }
                .take(25)
                .map { mapOf("text" to it.displayText(), "type" to it.className?.substringAfterLast(".").orEmpty()) }

            val allTexts = screenState.elements.take(30).map { it.displayText() }

            val prompt = """
                Sen Android telefon yordamchi AI. Faqat JSON javob ber.
                Foydalanuvchi maqsadi: "$goalUz"
                Hozirgi qadam: ${stepIndex + 1}/$totalSteps
                Joriy ilova: ${screenState.packageName}
                Bosiladigan elementlar: ${gson.toJson(clickable)}
                Ekrandagi matnlar: ${gson.toJson(allTexts)}

                Javob formati (faqat JSON):
                {
                  "targetText": "Bosilishi kerak bo'lgan elementning matni yoki null (scroll kerak bo'lsa)",
                  "instructionUz": "O'zbek tilida qisqa ko'rsatma (emoji bilan)",
                  "shouldScroll": true/false
                }
            """.trimIndent()

            callGeminiRaw(prompt)?.let { obj ->
                GeminiResponse(
                    targetText = obj.get("targetText")?.takeUnless { it.isJsonNull }?.asString,
                    instructionUz = obj.get("instructionUz")?.asString ?: "Davom eting",
                    shouldScroll = obj.get("shouldScroll")?.asBoolean ?: false
                )
            } ?: fallbackLocalAnalysis(goalUz, screenState)
        } catch (e: Exception) {
            Log.e(TAG, "analyzeScreenState: ${e.localizedMessage}")
            fallbackLocalAnalysis(goalUz, screenState)
        }
    }

    // Called from UniversalAIEngine for unknown intents — AI generates full task plan
    suspend fun planTaskFromPrompt(userPrompt: String): GuideTask? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        try {
            val prompt = """
                Sen Android telefon yordamchi AI. Foydalanuvchi so'rovi: "$userPrompt"
                Bu so'rov uchun qadam-baqadam vazifa reja tuz. Faqat JSON javob ber:
                {
                  "titleUz": "Vazifa nomi",
                  "descriptionUz": "Qisqa tavsif",
                  "category": "Sozlamalar/Ilovalar/Tarmoq/Xavfsizlik/Media",
                  "steps": [
                    {
                      "stepId": 1,
                      "titleUz": "Qadam nomi",
                      "instructionUz": "O'zbek tilida ko'rsatma (emoji bilan)",
                      "actionType": "OPEN_APP",
                      "targetKeywords": ["keyword1", "keyword2"],
                      "appPackage": "com.android.settings"
                    }
                  ]
                }
                actionType qiymatlari: OPEN_APP, CLICK_TARGET, SCROLL_DOWN, INPUT_TEXT
                Maksimal 6 qadam. appPackage null bo'lishi mumkin.
            """.trimIndent()

            val obj = callGeminiRaw(prompt) ?: return@withContext null

            val stepsArr = obj.getAsJsonArray("steps") ?: return@withContext null
            val steps = stepsArr.mapIndexed { idx, el ->
                val s = el.asJsonObject
                val keywords = s.getAsJsonArray("targetKeywords")?.map { it.asString } ?: listOf("settings")
                val actionType = try {
                    ActionType.valueOf(s.get("actionType")?.asString ?: "CLICK_TARGET")
                } catch (_: Exception) { ActionType.CLICK_TARGET }

                GuideStep(
                    stepId = idx + 1,
                    titleUz = s.get("titleUz")?.asString ?: "Qadam ${idx + 1}",
                    instructionUz = s.get("instructionUz")?.asString ?: "Davom eting",
                    actionType = actionType,
                    targetKeywords = keywords,
                    appPackage = s.get("appPackage")?.takeUnless { it.isJsonNull }?.asString
                )
            }

            GuideTask(
                id = "ai_task_${System.currentTimeMillis()}",
                titleUz = obj.get("titleUz")?.asString ?: userPrompt,
                descriptionUz = obj.get("descriptionUz")?.asString ?: userPrompt,
                category = obj.get("category")?.asString ?: "AI Yordamchi",
                iconResName = "smart_display",
                steps = steps
            )
        } catch (e: Exception) {
            Log.e(TAG, "planTaskFromPrompt: ${e.localizedMessage}")
            null
        }
    }

    // Agent loop: Gemini decides the single best next action given current screen state.
    // Returns null ONLY when apiKey is blank (caller should stop).
    // On network/parse errors, returns an AgentAction with an error userMessage so the
    // loop can decide whether to retry or abort.
    suspend fun decideNextAction(
        goal: String,
        elements: List<TargetElement>,
        currentPackage: String,
        history: List<String>
    ): AgentAction? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.e(TAG, "decideNextAction: API key yo'q")
            return@withContext null
        }

        val elementsList = elements
            .filter { it.isClickable || it.isEditable || it.text.isNotEmpty() || !it.contentDescription.isNullOrEmpty() }
            .take(40)
            .joinToString("\n") { elem ->
                val tag = when {
                    elem.isEditable -> "[INPUT]"
                    elem.isClickable -> "[TAP]"
                    else -> "[TEXT]"
                }
                "$tag \"${elem.displayText().take(70)}\""
            }

        val historyStr = if (history.isEmpty()) "Hali harakat yo'q"
        else history.takeLast(6).joinToString("\n") { "  • $it" }

        val prompt = """
You are an Android phone AI agent. Reply with ONLY a single valid JSON object.

Goal: "$goal"
Current app: $currentPackage

Screen elements:
$elementsList

History:
$historyStr

Output format (one JSON object, NO markdown fences):
{"reasoning":"why","action":"OPEN_APP","targetText":null,"textToType":null,"packageName":"com.android.settings","userMessage":"Sozlamalarga kirmoqdaman"}

Actions: OPEN_APP | CLICK | TYPE | SCROLL_DOWN | SCROLL_UP | BACK | WAIT | DONE
- OPEN_APP: open app by packageName (com.android.settings, com.android.chrome, etc.)
- CLICK: tap [TAP] element — targetText = exact element text shown above
- TYPE: enter text — textToType = text to type; only when [INPUT] field visible
- SCROLL_DOWN / SCROLL_UP: scroll the page
- BACK: press back button
- WAIT: wait for loading
- DONE: goal achieved

Use null for unused fields. userMessage must be in Uzbek.
        """.trimIndent()

        return@withContext try {
            val (obj, rawError) = callGeminiRawWithError(prompt)
            if (obj == null) {
                Log.e(TAG, "Gemini raw error: $rawError")
                AgentAction(
                    type = AgentActionType.WAIT,
                    userMessage = "⚠️ Gemini xatosi: ${rawError?.take(80) ?: "javob kelmadi"}. Qayta urinmoqda..."
                )
            } else {
                val actionStr = obj.get("action")?.asString ?: run {
                    Log.e(TAG, "No 'action' field in Gemini response: $obj")
                    return@withContext AgentAction(AgentActionType.WAIT, userMessage = "⚠️ Javob formati noto'g'ri, qayta urinmoqda...")
                }

                // Tolerant enum parse — handle slight variations
                val actionType = when (actionStr.uppercase().trim()) {
                    "CLICK" -> AgentActionType.CLICK
                    "TYPE" -> AgentActionType.TYPE
                    "SCROLL_DOWN", "SCROLL" -> AgentActionType.SCROLL_DOWN
                    "SCROLL_UP" -> AgentActionType.SCROLL_UP
                    "OPEN_APP", "OPEN" -> AgentActionType.OPEN_APP
                    "BACK" -> AgentActionType.BACK
                    "DONE", "COMPLETE", "FINISHED" -> AgentActionType.DONE
                    "WAIT", "LOADING" -> AgentActionType.WAIT
                    else -> {
                        Log.w(TAG, "Unknown action '$actionStr', defaulting to WAIT")
                        AgentActionType.WAIT
                    }
                }

                AgentAction(
                    type = actionType,
                    targetText = obj.get("targetText")?.takeUnless { it.isJsonNull }?.asString,
                    textToType = obj.get("textToType")?.takeUnless { it.isJsonNull }?.asString,
                    packageName = obj.get("packageName")?.takeUnless { it.isJsonNull }?.asString,
                    userMessage = obj.get("userMessage")?.asString.orEmpty(),
                    reasoning = obj.get("reasoning")?.asString.orEmpty()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "decideNextAction exception: ${e.localizedMessage}")
            AgentAction(AgentActionType.WAIT, userMessage = "⚠️ ${e.localizedMessage?.take(60) ?: "Noma'lum xato"}")
        }
    }

    // Variant of callGeminiRaw that also returns the error string for logging
    private fun callGeminiRawWithError(promptText: String): Pair<JsonObject?, String?> {
        return try {
            val obj = callGeminiRaw(promptText)
            Pair(obj, if (obj == null) "JSON parse yoki API xatosi" else null)
        } catch (e: Exception) {
            Pair(null, e.localizedMessage)
        }
    }

    private fun callGeminiRaw(promptText: String): JsonObject? {
        val body = JsonObject().apply {
            add("contents", gson.toJsonTree(listOf(
                mapOf("parts" to listOf(mapOf("text" to promptText)))
            )))
            add("generationConfig", gson.toJsonTree(mapOf(
                "temperature" to 0.2,
                "maxOutputTokens" to 1024
            )))
        }

        val request = Request.Builder()
            .url("$API_URL?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val raw = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            Log.e(TAG, "Gemini error ${response.code}: $raw")
            return null
        }

        val textOutput = gson.fromJson(raw, JsonObject::class.java)
            .getAsJsonArray("candidates")
            ?.get(0)?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString ?: return null

        // Handle both bare JSON and ```json...``` markdown blocks
        val jsonStr = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(textOutput)?.groupValues?.get(1)
            ?: textOutput.let {
                val s = it.indexOf('{')
                val e = it.lastIndexOf('}')
                if (s in 0..<e) it.substring(s, e + 1) else it
            }

        return try {
            gson.fromJson(jsonStr.trim(), JsonObject::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "JSON parse failed: $jsonStr")
            null
        }
    }

    private fun fallbackLocalAnalysis(goalUz: String, screenState: ScreenState): GeminiResponse {
        val kws = goalUz.lowercase().split(" ").filter { it.length > 2 }
        val matched = screenState.elements.firstOrNull { elem ->
            kws.any { kw -> elem.displayText().lowercase().contains(kw) }
        }
        return if (matched != null) {
            GeminiResponse(matched.displayText(), "'${matched.displayText()}' tugmasini bosing.", false)
        } else {
            GeminiResponse(null, "Mos element topilmadi. Pastga scroll qiling.", true)
        }
    }
}

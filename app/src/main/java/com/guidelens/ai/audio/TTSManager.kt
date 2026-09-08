package com.guidelens.ai.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSManager private constructor(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TTSManager"

        @Volatile private var instance: TTSManager? = null

        fun getInstance(context: Context): TTSManager {
            return instance ?: synchronized(this) {
                instance ?: TTSManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isReady = false
    private var lastSpokenText = ""
    private var pendingText: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed")
            return
        }
        // Try Uzbek → Russian → system default
        val uzResult = tts?.setLanguage(Locale("uz", "UZ"))
        if (uzResult == TextToSpeech.LANG_MISSING_DATA || uzResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val ruResult = tts?.setLanguage(Locale("ru", "RU"))
            if (ruResult == TextToSpeech.LANG_MISSING_DATA || ruResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
        }
        tts?.setSpeechRate(0.85f)
        isReady = true
        pendingText?.let { speak(it, overrideSame = true) }
        pendingText = null
    }

    fun speak(text: String, overrideSame: Boolean = false) {
        // Disabled — instructions shown as text in overlay UI
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        instance = null
    }
}

package com.guidelens.ai.agent

enum class AgentActionType {
    CLICK,        // element bosish
    TYPE,         // matn yozish (klaviatura ochiq bo'lganda)
    SCROLL_DOWN,  // pastga surish
    SCROLL_UP,    // yuqoriga surish
    OPEN_APP,     // ilova ochish (packageName orqali)
    BACK,         // orqaga qaytish
    WAIT,         // yuklanishni kutish
    DONE          // vazifa tugadi
}

data class AgentAction(
    val type: AgentActionType,
    val targetText: String? = null,     // CLICK uchun element matni
    val textToType: String? = null,     // TYPE uchun yoziladigan matn
    val packageName: String? = null,    // OPEN_APP uchun paket nomi
    val userMessage: String = "",       // Overlay da ko'rsatiladigan o'zbek matn
    val reasoning: String = ""          // Gemini nega bu harakatni tanladi
)

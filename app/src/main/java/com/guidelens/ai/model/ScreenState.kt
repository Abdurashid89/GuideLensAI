package com.guidelens.ai.model

import android.graphics.Rect

data class TargetRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2

    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)
}

data class TargetElement(
    val id: String,
    val text: String,
    val contentDescription: String? = null,
    val className: String? = null,
    val bounds: TargetRect,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false
) {
    fun displayText(): String = text.ifBlank { contentDescription.orEmpty() }

    fun matchesKeywords(vararg keywords: String): Boolean {
        val target = displayText().lowercase()
        return keywords.any { kw -> target.contains(kw.lowercase()) }
    }
}

data class ScreenState(
    val packageName: String = "",
    val className: String = "",
    val elements: List<TargetElement> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    fun findElement(vararg keywords: String): TargetElement? {
        val matches = elements.filter { elem -> elem.matchesKeywords(*keywords) }
        // Prefer clickable or editable elements so we don't highlight a label by mistake
        return matches.firstOrNull { it.isClickable || it.isEditable }
            ?: matches.firstOrNull()
    }

    fun containsText(vararg keywords: String): Boolean {
        return findElement(*keywords) != null
    }
}

package com.guidelens.ai.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.guidelens.ai.engine.AutoTapBus
import com.guidelens.ai.engine.TaskEngine
import com.guidelens.ai.model.ActionType
import com.guidelens.ai.model.ScreenState
import com.guidelens.ai.model.TargetElement
import com.guidelens.ai.model.TargetRect

class GuideAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GuideAccessibility"
        private const val SCROLL_COOLDOWN_MS = 1200L

        var isServiceRunning = false
            private set

        private var instance: GuideAccessibilityService? = null
        fun getInstance(): GuideAccessibilityService? = instance
    }

    private var lastScrollTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        Log.d(TAG, "GuideAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // AUTO mode: perform pending tap
        val pendingTap = AutoTapBus.consumeTap()
        if (pendingTap != null) {
            performAutoTap(pendingTap)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // User tapped a real element — check if it matches the current step target.
                // If yes, advance the step automatically (no need to tap the overlay highlight).
                val engine = TaskEngine.getInstance(this)
                val state = engine.engineState.value
                if (state.isRunning && !state.isAutoExecuting && state.currentStep != null &&
                    state.currentStep.actionType == ActionType.CLICK_TARGET
                ) {
                    val clickedText = buildString {
                        event.text?.forEach { append(it).append(" ") }
                        append(event.contentDescription?.toString().orEmpty())
                    }.trim().lowercase()

                    val matched = state.currentStep.targetKeywords.any { kw ->
                        clickedText.contains(kw.lowercase())
                    }
                    if (matched) engine.simulateUserActionClick(clickedText)
                }
                parseCurrentScreenState(event.packageName?.toString().orEmpty())
            }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val pkg = event.packageName?.toString().orEmpty()

                // Auto-scroll: direction comes from engine state
                val state = TaskEngine.getInstance(this).engineState.value
                val now = System.currentTimeMillis()
                if (state.shouldScroll && now - lastScrollTime > SCROLL_COOLDOWN_MS) {
                    val scrolled = if (state.scrollDirection >= 0) {
                        performAutoScrollDown()
                    } else {
                        performAutoScrollUp()
                    }
                    if (scrolled) lastScrollTime = now
                }

                parseCurrentScreenState(pkg)
            }
        }
    }

    private fun performAutoScrollDown(): Boolean {
        val root = rootInActiveWindow ?: return false
        return scrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    private fun performAutoScrollUp(): Boolean {
        val root = rootInActiveWindow ?: return false
        return scrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun scrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            return node.performAction(action)
        }
        for (i in 0 until node.childCount) {
            if (scrollNode(node.getChild(i) ?: continue, action)) return true
        }
        return false
    }

    // Keep old name as alias for backward compat
    private fun scrollForward(node: AccessibilityNodeInfo) =
        scrollNode(node, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

    // ── Agent API (public) ─────────────────────────────────────────────────

    fun getScreenElements(): List<com.guidelens.ai.model.TargetElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<com.guidelens.ai.model.TargetElement>()
        return try {
            traverseNodeTree(root, elements)
            elements
        } catch (_: Exception) { elements }
    }

    fun getCurrentPackage(): String =
        rootInActiveWindow?.packageName?.toString().orEmpty()

    fun clickElementByText(text: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            // Try exact match first, then fuzzy
            val node = findNodeByText(root, text) ?: findNodeByTextFuzzy(root, text)
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "clickElementByText: ${e.localizedMessage}")
            false
        }
    }

    fun agentScrollDown(): Boolean = performAutoScrollDown()
    fun agentScrollUp(): Boolean = performAutoScrollUp()

    // AUTO mode: tap target element
    private fun performAutoTap(target: TargetElement) {
        try {
            val root = rootInActiveWindow ?: return
            val node = findNodeByBounds(root, target.bounds)
                ?: findNodeByText(root, target.displayText())
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-tap failed: ${e.localizedMessage}")
        }
    }

    fun performTypeText(text: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val editNode = findFirstEditable(root) ?: return false
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "Type text failed: ${e.localizedMessage}")
            false
        }
    }

    fun performBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    // ── Screen parsing ─────────────────────────────────────────────────────

    private fun parseCurrentScreenState(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val elements = mutableListOf<TargetElement>()
        try {
            traverseNodeTree(rootNode, elements)
            val screenState = ScreenState(
                packageName = packageName.ifBlank { rootNode.packageName?.toString().orEmpty() },
                className = rootNode.className?.toString().orEmpty(),
                elements = elements
            )
            TaskEngine.getInstance(this).onNewScreenState(screenState)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing tree: ${e.localizedMessage}")
        }
    }

    private fun traverseNodeTree(node: AccessibilityNodeInfo?, list: MutableList<TargetElement>) {
        if (node == null || !node.isVisibleToUser) return
        val boundsRect = Rect()
        node.getBoundsInScreen(boundsRect)
        val textStr = node.text?.toString().orEmpty().trim()
        val descStr = node.contentDescription?.toString().orEmpty().trim()
        if ((textStr.isNotEmpty() || descStr.isNotEmpty()) && boundsRect.width() > 0 && boundsRect.height() > 0) {
            list.add(
                TargetElement(
                    id = node.viewIdResourceName ?: "node_${list.size}",
                    text = textStr,
                    contentDescription = descStr,
                    className = node.className?.toString().orEmpty(),
                    bounds = TargetRect(boundsRect.left, boundsRect.top, boundsRect.right, boundsRect.bottom),
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable
                )
            )
        }
        for (i in 0 until node.childCount) {
            traverseNodeTree(node.getChild(i), list)
        }
    }

    // ── Node finders ───────────────────────────────────────────────────────

    private fun findNodeByBounds(root: AccessibilityNodeInfo, bounds: TargetRect): AccessibilityNodeInfo? {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (rect.left == bounds.left && rect.top == bounds.top &&
            rect.right == bounds.right && rect.bottom == bounds.bottom
        ) return root
        for (i in 0 until root.childCount) {
            val r = findNodeByBounds(root.getChild(i) ?: continue, bounds)
            if (r != null) return r
        }
        return null
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = (root.text?.toString().orEmpty() + root.contentDescription?.toString().orEmpty())
        if (nodeText.lowercase().contains(text.lowercase()) && root.isClickable) return root
        for (i in 0 until root.childCount) {
            val r = findNodeByText(root.getChild(i) ?: continue, text)
            if (r != null) return r
        }
        return null
    }

    // Fuzzy match: any word from target text appears in node text
    private fun findNodeByTextFuzzy(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val words = text.lowercase().split(" ", ",", ".", "-").filter { it.length > 2 }
        val nodeText = (root.text?.toString().orEmpty() + " " + root.contentDescription?.toString().orEmpty()).lowercase()
        if (root.isClickable && words.any { nodeText.contains(it) }) return root
        for (i in 0 until root.childCount) {
            val r = findNodeByTextFuzzy(root.getChild(i) ?: continue, text)
            if (r != null) return r
        }
        return null
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val r = findFirstEditable(root.getChild(i) ?: continue)
            if (r != null) return r
        }
        return null
    }

    override fun onInterrupt() { Log.w(TAG, "Service interrupted") }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        instance = null
    }
}

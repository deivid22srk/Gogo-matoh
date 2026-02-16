package com.hydra.gamesearch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.os.Handler
import android.os.Looper

class GogoAccessibilityService : AccessibilityService() {

    private var botEnabled = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        var instance: GogoAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("GogoBot", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We use a polling approach in playMove instead of reacting to every event
    }

    override fun onInterrupt() {}

    fun toggleBot() {
        botEnabled = !botEnabled
        if (botEnabled) {
            Log.d("GogoBot", "Bot Enabled")
            playMove()
        } else {
            Log.d("GogoBot", "Bot Disabled")
            handler.removeCallbacksAndMessages(null)
        }
    }

    private fun playMove() {
        if (!botEnabled) return

        val root = rootInActiveWindow
        if (root == null) {
            Log.d("GogoBot", "Root null, retrying...")
            scheduleNextMove(2000)
            return
        }

        val items = mutableListOf<AccessibilityNodeInfo>()
        findClickableItems(root, items)

        Log.d("GogoBot", "Found ${items.size} clickable items")

        val move = findBestMove(items)
        if (move != null) {
            Log.d("GogoBot", "Performing move: ${move.first} -> ${move.second}")
            performDrag(move.first, move.second)
            scheduleNextMove(1200) // Wait for animation
        } else {
            Log.d("GogoBot", "No move found, looking for generators...")
            val generator = findGenerator(items)
            if (generator != null) {
                Log.d("GogoBot", "Clicking generator")
                performClick(generator)
                scheduleNextMove(800)
            } else {
                Log.d("GogoBot", "Nothing to do, retrying in 2s")
                scheduleNextMove(2000)
            }
        }
    }

    private fun scheduleNextMove(delay: Long) {
        handler.postDelayed({
            playMove()
        }, delay)
    }

    private fun findClickableItems(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClickableItems(child, list)
        }
    }

    private fun findBestMove(items: List<AccessibilityNodeInfo>): Pair<Rect, Rect>? {
        // Filter items with content description (likely game items)
        val gameItems = items.filter { it.contentDescription != null }

        for (i in gameItems.indices) {
            val item1 = gameItems[i]
            val desc1 = item1.contentDescription.toString()
            if (desc1.isBlank()) continue

            for (j in i + 1 until gameItems.size) {
                val item2 = gameItems[j]
                val desc2 = item2.contentDescription.toString()

                if (desc1 == desc2) {
                    val rect1 = Rect()
                    item1.getBoundsInScreen(rect1)
                    val rect2 = Rect()
                    item2.getBoundsInScreen(rect2)

                    // Basic sanity check: don't "merge" the same physical location
                    if (rect1 != rect2) {
                        return Pair(rect1, rect2)
                    }
                }
            }
        }
        return null
    }

    private fun findGenerator(items: List<AccessibilityNodeInfo>): Rect? {
        // Generators often have descriptions like "Generator" or "Tap to produce"
        // Or sometimes they are just specific clickable views
        // For now, we'll try to find any item that has a description containing "gera" (Portuguese for generate) or "produce"
        for (item in items) {
            val desc = item.contentDescription?.toString()?.lowercase() ?: continue
            if (desc.contains("gera") || desc.contains("produce") || desc.contains("tap")) {
                val rect = Rect()
                item.getBoundsInScreen(rect)
                return rect
            }
        }
        return null
    }

    private fun performClick(rect: Rect) {
        val path = Path()
        path.moveTo(rect.centerX().toFloat(), rect.centerY().toFloat())
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performDrag(from: Rect, to: Rect) {
        val path = Path()
        path.moveTo(from.centerX().toFloat(), from.centerY().toFloat())
        path.lineTo(to.centerX().toFloat(), to.centerY().toFloat())

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
            }
        }, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        handler.removeCallbacksAndMessages(null)
    }
}

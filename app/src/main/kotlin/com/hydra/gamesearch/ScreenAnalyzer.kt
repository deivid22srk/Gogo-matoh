package com.hydra.gamesearch

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs

class ScreenAnalyzer {

    data class Move(val fromX: Int, val fromY: Int, val toX: Int, val toY: Int)

    fun findBestMove(bitmap: Bitmap): Move? {
        val width = bitmap.width
        val height = bitmap.height

        // Define the game area (heuristic for Merge Boss on most screens)
        // Usually items are in the bottom 60% of the screen
        val gridTop = (height * 0.4).toInt()
        val gridBottom = (height * 0.9).toInt()
        val gridLeft = (width * 0.05).toInt()
        val gridRight = (width * 0.95).toInt()

        val rows = 9
        val cols = 7

        val cellWidth = (gridRight - gridLeft) / cols
        val cellHeight = (gridBottom - gridTop) / rows

        val cellHashes = mutableListOf<Triple<Int, Int, Long>>()

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val centerX = gridLeft + col * cellWidth + cellWidth / 2
                val centerY = gridTop + row * cellHeight + cellHeight / 2

                // Sample a few pixels around the center to create a simple "fingerprint"
                val hash = sampleCell(bitmap, centerX, centerY)
                if (hash != 0L) {
                    cellHashes.add(Triple(col, row, hash))
                }
            }
        }

        Log.d("HydraAnalyzer", "Found ${cellHashes.size} active cells")

        // Find matches
        for (i in cellHashes.indices) {
            for (j in i + 1 until cellHashes.size) {
                val c1 = cellHashes[i]
                val c2 = cellHashes[j]

                if (c1.third == c2.third && c1.third != -1L) {
                    // Convert grid coords to screen pixels for the move
                    val x1 = gridLeft + c1.first * cellWidth + cellWidth / 2
                    val y1 = gridTop + c1.second * cellHeight + cellHeight / 2
                    val x2 = gridLeft + c2.first * cellWidth + cellWidth / 2
                    val y2 = gridTop + c2.second * cellHeight + cellHeight / 2

                    return Move(x1, y1, x2, y2)
                }
            }
        }

        return null
    }

    private fun sampleCell(bitmap: Bitmap, cx: Int, cy: Int): Long {
        if (cx < 10 || cy < 10 || cx > bitmap.width - 10 || cy > bitmap.height - 10) return 0L

        // Check if it's likely a game item and not background
        // Simple heuristic: get center color and check if it's not a generic background color
        val centerColor = bitmap.getPixel(cx, cy)
        if (isBackgroundColor(centerColor)) return -1L

        // Simple hash: combine color values of 5 points
        var h = 0L
        val points = arrayOf(
            Pair(0, 0), Pair(-4, -4), Pair(4, 4), Pair(-4, 4), Pair(4, -4)
        )

        for (p in points) {
            val pixel = bitmap.getPixel(cx + p.first, cy + p.second)
            h = h * 31 + pixel.toLong()
        }
        return h
    }

    private fun isBackgroundColor(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        // Merge Boss background is often light gray or light blue
        // These colors have very low saturation
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = if (max == 0) 0 else (max - min) * 100 / max

        return saturation < 5 // Very low saturation is likely background or empty cell
    }
}

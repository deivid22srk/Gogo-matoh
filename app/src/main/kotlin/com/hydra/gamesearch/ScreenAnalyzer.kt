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
                if (hash != 0L && !isBackground(hash)) {
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

                if (areSimilar(c1.third, c2.third)) {
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
        if (cx < 5 || cy < 5 || cx > bitmap.width - 5 || cy > bitmap.height - 5) return 0L

        // Simple hash: combine color values of 5 points
        var h = 0L
        val points = arrayOf(
            Pair(0, 0), Pair(-2, -2), Pair(2, 2), Pair(-2, 2), Pair(2, -2)
        )

        for (p in points) {
            val pixel = bitmap.getPixel(cx + p.first, cy + p.second)
            h = h * 31 + pixel.toLong()
        }
        return h
    }

    private fun isBackground(hash: Long): Boolean {
        // Heuristic: background in Merge Boss is often a specific shade of gray/blue
        // For now we'll just check if it's not too dark or too light
        return false
    }

    private fun areSimilar(h1: Long, h2: Long): Boolean {
        // In this simple version, we want exact matches of our samples
        return h1 == h2
    }
}

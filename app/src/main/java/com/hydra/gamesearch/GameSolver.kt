package com.hydra.gamesearch

import android.graphics.Bitmap
import android.util.Log

object GameSolver {
    private const val TAG = "GameSolver"

    data class Match(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    fun findMatches(bitmap: Bitmap): List<Match> {
        val matches = mutableListOf<Match>()

        // Simplified grid analysis for the screenshot provided (AliExpress Merge Boss style)
        // Usually, these games have a grid. Let's assume a standard search.
        val rows = 8
        val cols = 7

        // Dynamic grid detection attempt based on common proportions
        val startX = bitmap.width * 0.08f
        val startY = bitmap.height * 0.35f
        val endX = bitmap.width * 0.92f
        val endY = bitmap.height * 0.85f

        val gridWidth = endX - startX
        val gridHeight = endY - startY
        val cellW = gridWidth / cols
        val cellH = gridHeight / rows

        val grid = Array(rows) { r ->
            IntArray(cols) { c ->
                val x = (startX + c * cellW + cellW / 2).toInt()
                val y = (startY + r * cellH + cellH / 2).toInt()
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    bitmap.getPixel(x, y)
                } else {
                    0
                }
            }
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = grid[r][c]
                if (color == 0) continue

                // Check right
                if (c + 1 < cols && isSimilar(color, grid[r][c + 1])) {
                    matches.add(Match(
                        startX + c * cellW + cellW / 2, startY + r * cellH + cellH / 2,
                        startX + (c + 1) * cellW + cellW / 2, startY + r * cellH + cellH / 2
                    ))
                }
                // Check down
                if (r + 1 < rows && isSimilar(color, grid[r + 1][c])) {
                    matches.add(Match(
                        startX + c * cellW + cellW / 2, startY + r * cellH + cellH / 2,
                        startX + c * cellW + cellW / 2, startY + (r + 1) * cellH + cellH / 2
                    ))
                }
            }
        }

        return matches
    }

    private fun isSimilar(c1: Int, c2: Int): Boolean {
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        val dist = Math.sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble())
        return dist < 35
    }
}

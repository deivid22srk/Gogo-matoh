package com.hydra.gamesearch

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import kotlin.coroutines.resume

object GameSolver {
    private const val TAG = "GameSolver"
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                try {
                    solve()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in solve loop", e)
                }
                delay(2000) // Wait between moves
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun solve() {
        val service = GogoAccessibilityService.instance ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val screenshot = takeScreenshot(service) ?: return

            // Analyze grid
            // Based on the image, the grid is roughly in the middle
            // Let's assume a 7x7 grid for this example
            val grid = analyzeGrid(screenshot)
            val move = findBestMove(grid)

            if (move != null) {
                service.performSwipe(move.x1, move.y1, move.x2, move.y2)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshot(service: GogoAccessibilityService): Bitmap? = suspendCancellableCoroutine { cont ->
        service.takeScreenshot(0, service.mainExecutor, object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                // Need to copy to software bitmap to use getPixel
                val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                cont.resume(softwareBitmap)
            }

            override fun onFailure(errorCode: Int) {
                Log.e(TAG, "Screenshot failed: $errorCode")
                cont.resume(null)
            }
        })
    }

    private fun analyzeGrid(bitmap: Bitmap): Grid {
        // Simplified grid analysis
        // Assume grid starts at (50, 400) and each cell is 140x140
        val cols = 7
        val rows = 8
        val startX = 50
        val startY = 400
        val cellSize = 140

        val cells = Array(rows) { r ->
            IntArray(cols) { c ->
                val x = startX + c * cellSize + cellSize / 2
                val y = startY + r * cellSize + cellSize / 2
                if (x < bitmap.width && y < bitmap.height) {
                    bitmap.getPixel(x, y)
                } else {
                    0
                }
            }
        }
        return Grid(rows, cols, cells, startX, startY, cellSize)
    }

    private fun findBestMove(grid: Grid): Move? {
        // Simple matching logic: find two adjacent identical (ish) colors
        // In a real app, we'd use more complex matching
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val color = grid.cells[r][c]
                if (color == 0) continue

                // Check right
                if (c + 1 < grid.cols && isSimilar(color, grid.cells[r][c + 1])) {
                    return Move(
                        grid.getCellCenterX(r, c), grid.getCellCenterY(r, c),
                        grid.getCellCenterX(r, c + 1), grid.getCellCenterY(r, c + 1)
                    )
                }
                // Check down
                if (r + 1 < grid.rows && isSimilar(color, grid.cells[r + 1][c])) {
                    return Move(
                        grid.getCellCenterX(r, c), grid.getCellCenterY(r, c),
                        grid.getCellCenterX(r + 1, c), grid.getCellCenterY(r + 1, c)
                    )
                }
            }
        }
        return null
    }

    private fun isSimilar(c1: Int, c2: Int): Boolean {
        // Simple color similarity
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        val dist = Math.sqrt(((r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)).toDouble())
        return dist < 30 // Threshold
    }

    data class Grid(val rows: Int, val cols: Int, val cells: Array<IntArray>, val startX: Int, val startY: Int, val cellSize: Int) {
        fun getCellCenterX(r: Int, c: Int) = (startX + c * cellSize + cellSize / 2).toFloat()
        fun getCellCenterY(r: Int, c: Int) = (startY + r * cellSize + cellSize / 2).toFloat()
    }

    data class Move(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
}

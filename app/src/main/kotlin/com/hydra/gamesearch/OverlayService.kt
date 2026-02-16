package com.hydra.gamesearch

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.*
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var floatingView: ComposeView? = null
    private var moveOverlayView: ComposeView? = null

    private lateinit var captureHelper: ScreenCaptureHelper
    private val analyzer = ScreenAnalyzer()

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreValue = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreValue
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        // Initialize SavedStateRegistry BEFORE moving to CREATED state
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        captureHelper = ScreenCaptureHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        if (intent?.action == "START_CAPTURE") {
            val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            val data = intent.getParcelableExtra<Intent>("data")
            if (data != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                } else {
                    startForeground(1, createNotification())
                }
                captureHelper.startCapture(resultCode, data)
                showFloatingButton()
            }
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hydra_bot", "Hydra Bot Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "hydra_bot")
            .setContentTitle("Hydra Bot Ativo")
            .setContentText("Analisando tela do jogo...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun showFloatingButton() {
        if (floatingView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 500
        }

        floatingView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides this@OverlayService,
                        LocalViewModelStoreOwner provides this@OverlayService,
                        LocalSavedStateRegistryOwner provides this@OverlayService
                    ) {
                        var isScanning by remember { mutableStateOf(false) }
                        var offsetX by remember { mutableStateOf(100f) }
                        var offsetY by remember { mutableStateOf(500f) }

                        Card(
                            modifier = Modifier
                                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                        params.x = offsetX.roundToInt()
                                        params.y = offsetY.roundToInt()
                                        windowManager.updateViewLayout(this@apply, params)
                                    }
                                }
                                .size(64.dp),
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isScanning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = {
                                    isScanning = true
                                    captureAndAnalyze {
                                        isScanning = false
                                    }
                                }) {
                                    if (isScanning) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        windowManager.addView(floatingView, params)
    }

    private fun captureAndAnalyze(onComplete: () -> Unit) {
        captureHelper.captureBitmap { bitmap ->
            if (bitmap != null) {
                val move = analyzer.findBestMove(bitmap)
                if (move != null) {
                    showMoveOverlay(move)
                }
            }
            onComplete()
        }
    }

    private fun showMoveOverlay(move: ScreenAnalyzer.Move) {
        if (moveOverlayView != null) {
            try {
                windowManager.removeView(moveOverlayView)
            } catch (e: Exception) {}
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        moveOverlayView = ComposeView(this).apply {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides this@OverlayService,
                        LocalViewModelStoreOwner provides this@OverlayService,
                        LocalSavedStateRegistryOwner provides this@OverlayService
                    ) {
                        val alpha by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = tween(500)
                        )

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val color = Color.Magenta
                            drawCircle(color, radius = 40f, center = Offset(move.fromX.toFloat(), move.fromY.toFloat()), alpha = alpha)
                            drawLine(
                                color = color,
                                start = Offset(move.fromX.toFloat(), move.fromY.toFloat()),
                                end = Offset(move.toX.toFloat(), move.toY.toFloat()),
                                strokeWidth = 10f,
                                alpha = alpha
                            )
                            drawCircle(color, radius = 20f, center = Offset(move.toX.toFloat(), move.toY.toFloat()), alpha = alpha)
                        }
                    }
                }
            }
        }

        windowManager.addView(moveOverlayView, params)

        // Hide after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (moveOverlayView != null) {
                try {
                    windowManager.removeView(moveOverlayView)
                } catch (e: Exception) {}
                moveOverlayView = null
            }
        }, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (floatingView != null) windowManager.removeView(floatingView)
        if (moveOverlayView != null) {
            try {
                windowManager.removeView(moveOverlayView)
            } catch (e: Exception) {}
        }
        captureHelper.stopCapture()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

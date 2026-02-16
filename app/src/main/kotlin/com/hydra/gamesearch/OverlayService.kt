package com.hydra.gamesearch

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSavedStateRegistryOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
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

            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("data", Intent::class.java)
            } else {
                intent.getParcelableExtra("data")
            }

            if (data != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
                    } else {
                        startForeground(1, createNotification())
                    }

                    Handler(Looper.getMainLooper()).post {
                        captureHelper.startCapture(resultCode, data)
                        showFloatingButton()
                    }
                } catch (e: Exception) {
                    Log.e("HydraBot", "Failed to start foreground service or capture", e)
                }
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun applyComposeOwners(view: View) {
        try {
            // Set LifecycleOwner
            val lifecycleClass = try {
                Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            } catch (e: ClassNotFoundException) {
                Class.forName("androidx.lifecycle.runtime.ViewTreeLifecycleOwner")
            }
            val setLifecycle = lifecycleClass.getMethod("set", View::class.java, LifecycleOwner::class.java)
            setLifecycle.invoke(null, view, this)

            // Set ViewModelStoreOwner
            val viewModelClass = try {
                Class.forName("androidx.lifecycle.viewmodel.ViewTreeViewModelStoreOwner")
            } catch (e: ClassNotFoundException) {
                Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner") // Fallback same but retry
            }
            val setViewModel = viewModelClass.getMethod("set", View::class.java, ViewModelStoreOwner::class.java)
            setViewModel.invoke(null, view, this)

            // Set SavedStateRegistryOwner
            val savedStateClass = try {
                Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            } catch (e: ClassNotFoundException) {
                Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner") // Fallback same
            }
            val setSavedState = savedStateClass.getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
            setSavedState.invoke(null, view, this)
        } catch (e: Exception) {
            Log.e("HydraBot", "Error setting ViewTree owners via reflection: ${e.message}")

            // Extreme fallback: try to set tags by resource name if class setting fails
            try {
                val lifecycleId = resources.getIdentifier("view_tree_lifecycle_owner", "id", packageName)
                if (lifecycleId != 0) view.setTag(lifecycleId, this)

                val viewModelId = resources.getIdentifier("view_tree_view_model_store_owner", "id", packageName)
                if (viewModelId != 0) view.setTag(viewModelId, this)

                val savedStateId = resources.getIdentifier("view_tree_saved_state_registry_owner", "id", packageName)
                if (savedStateId != 0) view.setTag(savedStateId, this)
            } catch (ex: Exception) {
                Log.e("HydraBot", "Fallback tag setting failed", ex)
            }
        }
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
            applyComposeOwners(this)
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
            applyComposeOwners(this)
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
        if (floatingView != null) {
            try {
                windowManager.removeView(floatingView)
            } catch (e: Exception) {}
        }
        if (moveOverlayView != null) {
            try {
                windowManager.removeView(moveOverlayView)
            } catch (e: Exception) {}
        }
        captureHelper.stopCapture()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

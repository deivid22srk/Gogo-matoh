package com.hydra.gamesearch

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GameAnalyzerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameAnalyzerScreen() {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var matches by remember { mutableStateOf<List<GameSolver.Match>>(emptyList()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val selectedBitmap = if (Build.VERSION.SDK_INT < 28) {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            }
            bitmap = selectedBitmap
            matches = GameSolver.findMatches(selectedBitmap)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gogo Match Analyzer") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Selecionar Foto") },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val currentBitmap = bitmap
                    if (currentBitmap != null) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val imageBitmap = currentBitmap.asImageBitmap()

                            val scaleX = maxWidth.value / currentBitmap.width
                            val scaleY = maxHeight.value / currentBitmap.height
                            val scale = minOf(scaleX, scaleY)

                            val drawnWidth = currentBitmap.width * scale
                            val drawnHeight = currentBitmap.height * scale

                            Box(modifier = Modifier.size(drawnWidth.dp, drawnHeight.dp)) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Game Screenshot",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    matches.forEach { match ->
                                        val startX = match.x1 * scale
                                        val startY = match.y1 * scale
                                        val endX = match.x2 * scale
                                        val endY = match.y2 * scale

                                        drawCircle(
                                            color = Color.Yellow,
                                            radius = 15f * scale,
                                            center = Offset(startX, startY),
                                            style = Stroke(width = 3f * scale)
                                        )
                                        drawCircle(
                                            color = Color.Yellow,
                                            radius = 15f * scale,
                                            center = Offset(endX, endY),
                                            style = Stroke(width = 3f * scale)
                                        )
                                        drawLine(
                                            color = Color.Yellow,
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                            strokeWidth = 2f * scale
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Nenhuma imagem selecionada", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

package com.codingwithnobody.ronaldo


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize


/**
 * ═══════════════════════════════════════════════════════════════
 * GAME OVERLAY COMPOSABLE (Enhanced with Effects & Physics)
 * ═══════════════════════════════════════════════════════════════
 *
 * Render game overlay bằng Compose Canvas
 * Đây là lớp mà USER NHÌN THẤY (interactive preview)
 *
 * Features:
 * - Physics-based bullet rendering (bounce, rotation)
 * - Gun recoil animation
 * - Visual effects (muzzle flash, explosion, spark)
 * - Procedural effect rendering (fallback khi không có sprite sheets)
 */

@Composable
fun GameOverlay(
    gameState: GameState,
    gameAssets: GameAssets, // Truyền Assets vào đây
    onTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val gameRenderer = remember(gameAssets) { GameRenderer(gameAssets) }
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Coordinate mapper
    val coordinateMapper = remember { CoordinateMapper() }

    // Canvas size
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }


    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    coordinateMapper.updateScreenSize(size.width.toFloat(), size.height.toFloat())
                }
                .pointerInput(gameState.phase) {
                    if (gameState.phase == GamePhase.PLAYING) {
                        detectTapGestures { offset ->
                            // Convert screen tap to normalized coordinates
                            val normalized = coordinateMapper.screenToNormalized(offset.x, offset.y)
                            onTap(normalized.x, normalized.y)
                        }
                    }
                }
        ) {
            drawIntoCanvas { composeCanvas ->
                val nativeCanvas = composeCanvas.nativeCanvas

                // Gọi cùng 1 hàm vẽ với Media3
                gameRenderer.draw(
                    nativeCanvas,
                    gameState,
                    size.width.toInt(),
                    size.height.toInt()
                )
            }
        }
    }
}











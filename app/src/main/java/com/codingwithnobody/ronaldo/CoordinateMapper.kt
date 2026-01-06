package com.codingwithnobody.ronaldo


import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * ═══════════════════════════════════════════════════════════════
 * COORDINATE MAPPER
 * ═══════════════════════════════════════════════════════════════
 *
 * Chuyển đổi tọa độ giữa:
 * - Screen coordinates (pixels on device screen)
 * - Normalized coordinates (0..1, used in GameState)
 * - Video coordinates (pixels in video frame)
 * - OpenGL coordinates (-1..1)
 *
 * Tất cả game logic sử dụng normalized coordinates để đảm bảo
 * consistency giữa các kích thước màn hình khác nhau.
 */
class CoordinateMapper(
    var screenSize: Size = Size.Zero,
    var videoSize: Size = Size(1080f, 1920f)  // Default video size
) {


    /**
     * Convert screen tap position to normalized (0..1)
     */
    fun screenToNormalized(screenX: Float, screenY: Float): Offset {
        if (screenSize == Size.Zero) return Offset(0f, 0f)

        return Offset(
            x = (screenX / screenSize.width).coerceIn(0f, 1f),
            y = (screenY / screenSize.height).coerceIn(0f, 1f)
        )
    }

    /**
     * Convert normalized position to screen coordinates
     */
    fun normalizedToScreen(normalizedX: Float, normalizedY: Float): Offset {
        return Offset(
            x = normalizedX * screenSize.width,
            y = normalizedY * screenSize.height
        )
    }

    /**
     * Scale size from normalized to screen
     */
    fun normalizedSizeToScreen(normalizedWidth: Float, normalizedHeight: Float): Size {
        return Size(
            width = normalizedWidth * screenSize.width,
            height = normalizedHeight * screenSize.height
        )
    }

    // ══════════════════════════════════════════════════════════════
    // NORMALIZED <-> VIDEO
    // ══════════════════════════════════════════════════════════════

    /**
     * Convert normalized position to video coordinates
     */
    fun normalizedToVideo(normalizedX: Float, normalizedY: Float): Offset {
        return Offset(
            x = normalizedX * videoSize.width,
            y = normalizedY * videoSize.height
        )
    }

    /**
     * Scale size from normalized to video
     */
    fun normalizedSizeToVideo(normalizedWidth: Float, normalizedHeight: Float): Size {
        return Size(
            width = normalizedWidth * videoSize.width,
            height = normalizedHeight * videoSize.height
        )
    }

    // ══════════════════════════════════════════════════════════════
    // VIDEO <-> OPENGL
    // ══════════════════════════════════════════════════════════════

    /**
     * Convert video coordinates to OpenGL normalized device coordinates (-1..1)
     * OpenGL origin is at center, Y-axis is flipped
     */
    fun videoToOpenGL(videoX: Float, videoY: Float): Offset {
        return Offset(
            x = (videoX / videoSize.width) * 2f - 1f,
            y = 1f - (videoY / videoSize.height) * 2f  // Y flipped
        )
    }

    /**
     * Convert normalized (0..1) directly to OpenGL (-1..1)
     */
    fun normalizedToOpenGL(normalizedX: Float, normalizedY: Float): Offset {
        return Offset(
            x = normalizedX * 2f - 1f,
            y = 1f - normalizedY * 2f  // Y flipped
        )
    }

    // ══════════════════════════════════════════════════════════════
    // UTILITY
    // ══════════════════════════════════════════════════════════════

    /**
     * Update screen size (called when Compose layout changes)
     */
    fun updateScreenSize(width: Float, height: Float) {
        screenSize = Size(width, height)
    }

    /**
     * Update video size (called when video recording starts)
     */
    fun updateVideoSize(width: Float, height: Float) {
        videoSize = Size(width, height)
    }
}
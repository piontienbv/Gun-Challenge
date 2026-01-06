package com.codingwithnobody.ronaldo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.opengl.GLES20
import android.opengl.GLUtils
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.TextureOverlay
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave


/**
 * ═══════════════════════════════════════════════════════════════
 * GAME TEXTURE OVERLAY (Enhanced with Effects & Physics)
 * ═══════════════════════════════════════════════════════════════
 *
 * Render game vào video stream sử dụng TextureOverlay (GPU optimized)
 *
 * Features:
 * - Physics-based bullet rendering (bounce, rotation)
 * - Gun recoil animation
 * - Visual effects (muzzle flash, explosion, spark)
 * - Cached barrier texture for performance
 */
@UnstableApi
class GameTextureOverlay(
    private val gameAssets: GameAssets,
    private val getGameState: (presentationTimeUs: Long) -> GameState
) : TextureOverlay() {
    private val gameRenderer = GameRenderer(gameAssets)
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var textureId: Int = -1

    // Main Bitmap & Canvas (Reused)
    private var overlayBitmap: Bitmap? = null
    private var canvas: Canvas? = null


    private var recordingStartTimeUs: Long = -1L

    // ══════════════════════════════════════════════════════════════════
    // TEXTURE OVERLAY INTERFACE
    // ══════════════════════════════════════════════════════════════════

    override fun configure(videoSize: Size) {
        videoWidth = videoSize.width
        videoHeight = videoSize.height

        // Create OpenGL texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        overlayBitmap = createBitmap(videoWidth, videoHeight)
        canvas = Canvas(overlayBitmap!!)
    }

    override fun getTextureId(presentationTimeUs: Long): Int {
        // 1. Setup thời gian
        if (recordingStartTimeUs < 0) {
            recordingStartTimeUs = presentationTimeUs
        }
        val gameTimeMs = (presentationTimeUs - recordingStartTimeUs) / 1000
        val gameState = getGameState(gameTimeMs)

        // 2. Clear canvas
        canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // 3. VẼ VÀ BIẾN ĐỔI MA TRẬN
        canvas?.withSave {
            val centerX = videoWidth / 2f
            val centerY = videoHeight / 2f

            // BƯỚC A: Dời gốc tọa độ về TÂM màn hình
            translate(centerX, centerY)

            // BƯỚC B: Xử lý xoay ngang/dọc (Quan trọng)
            // Nếu videoWidth > videoHeight (Landscape input) -> Cần xoay -90 độ để dựng game dậy
            val isRotated = videoWidth > videoHeight
            if (isRotated) {
                rotate(-90f)
            }

            // BƯỚC C: Lật ngược trục Y để khớp với OpenGL (Fix lỗi lộn ngược)
            // scale(1f, -1f) nghĩa là giữ nguyên X, lật ngược Y
            scale(1f, -1f)

            // [Tùy chọn]: Nếu camera trước bị ngược chữ (Mirrored),
            // bạn có thể cần thêm dòng này: scale(-1f, 1f)

            // BƯỚC D: Xác định kích thước logic của Game
            // Nếu đã xoay -90 độ, thì Chiều Rộng vẽ = Chiều Cao video và ngược lại
            val drawWidth = if (isRotated) videoHeight else videoWidth
            val drawHeight = if (isRotated) videoWidth else videoHeight

            // BƯỚC E: Dời gốc tọa độ từ TÂM về lại GÓC TRÊN-TRÁI (để Renderer vẽ đúng)
            translate(-drawWidth / 2f, -drawHeight / 2f)

            // 4. Gọi Renderer vẽ
            if (canvas != null) {
                gameRenderer.draw(this, gameState, drawWidth, drawHeight)
            }
        }

        // 5. Upload lên GPU
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, overlayBitmap, 0)

        return textureId
    }

    override fun getTextureSize(presentationTimeUs: Long): Size {
        return Size(videoWidth, videoHeight)
    }


    override fun release() {
        if (textureId != -1) {
            val textures = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textures, 0)
            textureId = -1
        }

        overlayBitmap?.recycle()
        overlayBitmap = null
        canvas = null
        gameAssets.release()
    }

    // ══════════════════════════════════════════════════════════════════
    // RENDERING LOGIC
    // ══════════════════════════════════════════════════════════════════


    fun resetRecordingTime() {
        recordingStartTimeUs = -1L
    }
}
package com.codingwithnobody.ronaldo


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * ═══════════════════════════════════════════════════════════════
 * SPRITE SHEET SYSTEM
 * ═══════════════════════════════════════════════════════════════
 *
 * Hệ thống render sprite sheet animation - Industry Standard
 * cho game 2D. Thay vì dùng Lottie (vector → bitmap mỗi frame),
 * ta dùng pre-rendered frames trong một tấm ảnh lớn.
 *
 * Ưu điểm:
 * - Performance tối ưu (chỉ drawBitmap với srcRect)
 * - Hoạt động tốt trên cả Compose và TextureOverlay
 * - Dễ đồng bộ giữa preview và video
 *
 * Sprite Sheet Format hỗ trợ:
 * - Horizontal strip: [F0][F1][F2][F3]...
 * - Grid: [F0][F1][F2]
 *         [F3][F4][F5]
 *         [F6][F7][F8]
 */

// ═══════════════════════════════════════════════════════════════
// SPRITE SHEET DATA
// ═══════════════════════════════════════════════════════════════

/**
 * Định nghĩa một Sprite Sheet
 */
data class SpriteSheetDef(
    val id: String,
    val resourceId: Int,           // R.drawable.xxx
    val frameWidth: Int,           // Chiều rộng mỗi frame (px)
    val frameHeight: Int,          // Chiều cao mỗi frame (px)
    val frameCount: Int,           // Tổng số frames
    val columns: Int,              // Số cột trong sheet (1 = horizontal strip)
    val frameDurationMs: Long = 50 // Thời gian mỗi frame (ms)
) {
    val rows: Int get() = (frameCount + columns - 1) / columns
    val totalDurationMs: Long get() = frameCount * frameDurationMs
}

/**
 * Sprite Sheet đã được load vào memory
 */
class SpriteSheet(
    val def: SpriteSheetDef,
    val bitmap: Bitmap
) {
    // Pre-calculate source rects cho từng frame
    private val sourceRects: Array<Rect> = Array(def.frameCount) { index ->
        val col = index % def.columns
        val row = index / def.columns
        Rect(
            col * def.frameWidth,
            row * def.frameHeight,
            (col + 1) * def.frameWidth,
            (row + 1) * def.frameHeight
        )
    }

    /**
     * Lấy source rect cho frame index
     */
    fun getSourceRect(frameIndex: Int): Rect {
        val safeIndex = frameIndex.coerceIn(0, def.frameCount - 1)
        return sourceRects[safeIndex]
    }

    /**
     * Tính frame index từ thời gian animation đã chạy
     * @param elapsedMs Thời gian đã trôi qua (ms)
     * @param loop True = loop animation, False = clamp to last frame
     */
    fun getFrameIndex(elapsedMs: Long, loop: Boolean = false): Int {
        if (elapsedMs < 0) return 0

        val frameIndex = (elapsedMs / def.frameDurationMs).toInt()

        return if (loop) {
            frameIndex % def.frameCount
        } else {
            frameIndex.coerceAtMost(def.frameCount - 1)
        }
    }

    /**
     * Kiểm tra animation đã hoàn thành chưa
     */
    fun isComplete(elapsedMs: Long): Boolean {
        return elapsedMs >= def.totalDurationMs
    }

    /**
     * Vẽ frame hiện tại lên Canvas
     */
    fun drawFrame(
        canvas: Canvas,
        frameIndex: Int,
        destRect: RectF,
        paint: Paint? = null
    ) {
        val srcRect = getSourceRect(frameIndex)
        canvas.drawBitmap(bitmap, srcRect, destRect, paint)
    }

    /**
     * Vẽ frame tại thời điểm cụ thể
     */
    fun drawAtTime(
        canvas: Canvas,
        elapsedMs: Long,
        destRect: RectF,
        loop: Boolean = false,
        paint: Paint? = null
    ) {
        val frameIndex = getFrameIndex(elapsedMs, loop)
        drawFrame(canvas, frameIndex, destRect, paint)
    }

    /**
     * Vẽ frame với center position và size
     */
    fun drawCentered(
        canvas: Canvas,
        frameIndex: Int,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        paint: Paint? = null
    ) {
        val destRect = RectF(
            centerX - width / 2,
            centerY - height / 2,
            centerX + width / 2,
            centerY + height / 2
        )
        drawFrame(canvas, frameIndex, destRect, paint)
    }

    fun release() {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SPRITE ANIMATION INSTANCE
// ═══════════════════════════════════════════════════════════════

/**
 * Instance của một animation đang chạy
 * Dùng để track state của từng effect riêng biệt
 */
data class SpriteAnimation(
    val id: String,
    val spriteSheetId: String,
    val x: Float,                  // Center X (normalized 0..1)
    val y: Float,                  // Center Y (normalized 0..1)
    val width: Float,              // Width (normalized)
    val height: Float,             // Height (normalized)
    val startTimeMs: Long,         // Thời điểm bắt đầu (game time)
    val loop: Boolean = false,
    val rotation: Float = 0f,      // Rotation in degrees
    val alpha: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
) {
    /**
     * Tính elapsed time
     */
    fun getElapsedMs(currentTimeMs: Long): Long {
        return currentTimeMs - startTimeMs
    }
}

// ═══════════════════════════════════════════════════════════════
// SPRITE SHEET MANAGER
// ═══════════════════════════════════════════════════════════════

/**
 * Manager để load và cache sprite sheets
 */
class SpriteSheetManager(private val context: Context) {

    private val loadedSheets = mutableMapOf<String, SpriteSheet>()

    /**
     * Load sprite sheet vào memory
     */
    fun loadSpriteSheet(def: SpriteSheetDef): SpriteSheet {
        // Check cache
        loadedSheets[def.id]?.let { return it }

        // Load bitmap
        val options = BitmapFactory.Options().apply {
            inScaled = false  // Don't scale, we handle it ourselves
        }
        val bitmap = BitmapFactory.decodeResource(context.resources, def.resourceId, options)
            ?: throw IllegalArgumentException("Cannot load sprite sheet: ${def.id}")

        val sheet = SpriteSheet(def, bitmap)
        loadedSheets[def.id] = sheet

        return sheet
    }

    /**
     * Lấy sprite sheet đã load
     */
    fun getSpriteSheet(id: String): SpriteSheet? {
        return loadedSheets[id]
    }

    /**
     * Load nhiều sprite sheets cùng lúc
     */
    fun loadAll(defs: List<SpriteSheetDef>) {
        defs.forEach { loadSpriteSheet(it) }
    }

    /**
     * Release tất cả resources
     */
    fun releaseAll() {
        loadedSheets.values.forEach { it.release() }
        loadedSheets.clear()
    }

    /**
     * Release một sprite sheet cụ thể
     */
    fun release(id: String) {
        loadedSheets.remove(id)?.release()
    }
}

// ═══════════════════════════════════════════════════════════════
// SPRITE RENDERER
// ═══════════════════════════════════════════════════════════════

/**
 * Helper class để render sprites với transformations
 */
class SpriteRenderer(
    private val spriteSheetManager: SpriteSheetManager
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Render một sprite animation instance
     */
    fun render(
        canvas: Canvas,
        animation: SpriteAnimation,
        currentTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ): Boolean {
        val sheet = spriteSheetManager.getSpriteSheet(animation.spriteSheetId)
            ?: return false

        val elapsed = animation.getElapsedMs(currentTimeMs)

        // Check if animation is complete (for non-looping)
        if (!animation.loop && sheet.isComplete(elapsed)) {
            return false // Animation finished, should be removed
        }

        val frameIndex = sheet.getFrameIndex(elapsed, animation.loop)

        // Calculate destination rect in canvas coordinates
        val destWidth = animation.width * canvasWidth * animation.scaleX
        val destHeight = animation.height * canvasHeight * animation.scaleY
        val centerX = animation.x * canvasWidth
        val centerY = animation.y * canvasHeight

        // Apply alpha
        paint.alpha = (animation.alpha * 255).toInt()

        // Save canvas state
        canvas.save()

        // Apply transformations
        canvas.translate(centerX, centerY)
        if (animation.rotation != 0f) {
            canvas.rotate(animation.rotation)
        }

        // Draw
        val destRect = RectF(
            -destWidth / 2,
            -destHeight / 2,
            destWidth / 2,
            destHeight / 2
        )
        sheet.drawFrame(canvas, frameIndex, destRect, paint)

        // Restore canvas state
        canvas.restore()

        // Reset paint
        paint.alpha = 255

        return true // Animation still running
    }

    /**
     * Render tất cả animations, trả về list những animation còn active
     */
    fun renderAll(
        canvas: Canvas,
        animations: List<SpriteAnimation>,
        currentTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ): List<SpriteAnimation> {
        return animations.filter { anim ->
            render(canvas, anim, currentTimeMs, canvasWidth, canvasHeight)
        }
    }
}
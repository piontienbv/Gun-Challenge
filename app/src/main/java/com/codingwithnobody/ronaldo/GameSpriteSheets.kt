package com.codingwithnobody.ronaldo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.core.graphics.createBitmap
import kotlin.math.cos
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════
 * GAME ASSETS
 * ═══════════════════════════════════════════════════════════════
 *
 * Centralized asset management cho game.
 * Load và cache tất cả PNG sprites và sprite sheets.
 *
 * Assets structure trong res/drawable:
 * - gun.png              : Sprite súng
 * - monster.png          : Sprite mục tiêu
 * - wood_barrier.png     : Texture vật chắn gỗ
 * - bullet.png           : Sprite đạn
 * - muzzle_flash.png     : Sprite sheet tia lửa (horizontal strip)
 * - explosion.png        : Sprite sheet nổ (horizontal strip)
 * - spark.png            : Sprite sheet tia lửa va chạm
 */

// ═══════════════════════════════════════════════════════════════
// SPRITE SHEET DEFINITIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Định nghĩa tất cả sprite sheets trong game
 *
 * NOTE: Bạn cần tạo các file PNG tương ứng trong res/drawable
 * hoặc sử dụng placeholder cho đến khi có assets thật
 */
object GameSpriteSheets {

    /**
     * Muzzle Flash - Tia lửa khi bắn súng
     * Format: Horizontal strip, 6 frames
     * Suggested size: 384x64 (64x64 per frame)
     */
    fun muzzleFlash(resourceId: Int) = SpriteSheetDef(
        id = "muzzle_flash",
        resourceId = resourceId,
        frameWidth = 64,
        frameHeight = 64,
        frameCount = 6,
        columns = 6,
        frameDurationMs = 25  // ~240fps, very fast
    )

    /**
     * Explosion - Nổ khi trúng mục tiêu
     * Format: Horizontal strip, 12 frames
     * Suggested size: 1152x96 (96x96 per frame)
     */
    fun explosion(resourceId: Int) = SpriteSheetDef(
        id = "explosion",
        resourceId = resourceId,
        frameWidth = 96,
        frameHeight = 96,
        frameCount = 12,
        columns = 12,
        frameDurationMs = 42  // ~24fps
    )

    /**
     * Spark - Tia lửa khi đạn va chạm vật chắn
     * Format: Horizontal strip, 5 frames
     * Suggested size: 160x32 (32x32 per frame)
     */
    fun spark(resourceId: Int) = SpriteSheetDef(
        id = "spark",
        resourceId = resourceId,
        frameWidth = 32,
        frameHeight = 32,
        frameCount = 5,
        columns = 5,
        frameDurationMs = 33  // ~30fps
    )

    /**
     * Gun Recoil - Animation giật súng (optional, có thể dùng tween thay thế)
     * Format: Horizontal strip, 4 frames
     */
    fun gunRecoil(resourceId: Int) = SpriteSheetDef(
        id = "gun_recoil",
        resourceId = resourceId,
        frameWidth = 128,
        frameHeight = 64,
        frameCount = 4,
        columns = 4,
        frameDurationMs = 40
    )
}

// ═══════════════════════════════════════════════════════════════
// STATIC SPRITE DEFINITIONS
// ═══════════════════════════════════════════════════════════════

/**
 * Định nghĩa static sprites (không animate)
 */
data class StaticSpriteDef(
    val id: String,
    val resourceId: Int,
    val defaultWidth: Float = 0.1f,   // Normalized width
    val defaultHeight: Float = 0.1f   // Normalized height
)

// ═══════════════════════════════════════════════════════════════
// GAME ASSETS MANAGER
// ═══════════════════════════════════════════════════════════════

/**
 * Main asset manager cho game
 */
class GameAssets(private val context: Context) {

    // Sprite sheet manager
    val spriteSheetManager = SpriteSheetManager(context)

    // Sprite renderer
    val spriteRenderer = SpriteRenderer(spriteSheetManager)

    // Static sprites cache
    private val staticSprites = mutableMapOf<String, Bitmap>()

    // Asset loaded flag
    var isLoaded = false
        private set

    /**
     * Load tất cả assets
     * Gọi hàm này trong onCreate hoặc khi khởi tạo game
     */
    fun loadAssets(
        gunResId: Int? = null,
        monsterResId: Int? = null,
        barrierResId: Int? = null,
        bulletResId: Int? = null,
        muzzleFlashResId: Int? = null,
        explosionResId: Int? = null,
        sparkResId: Int? = null
    ) {
        // Load static sprites
        gunResId?.let { loadStaticSprite("gun", it) }
        monsterResId?.let { loadStaticSprite("monster", it) }
        barrierResId?.let { loadStaticSprite("barrier", it) }
        bulletResId?.let { loadStaticSprite("bullet", it) }

        // Load sprite sheets
        muzzleFlashResId?.let {
            spriteSheetManager.loadSpriteSheet(GameSpriteSheets.muzzleFlash(it))
        }
        explosionResId?.let {
            spriteSheetManager.loadSpriteSheet(GameSpriteSheets.explosion(it))
        }
        sparkResId?.let {
            spriteSheetManager.loadSpriteSheet(GameSpriteSheets.spark(it))
        }

        isLoaded = true
    }

    /**
     * Load một static sprite
     */
    fun loadStaticSprite(id: String, @DrawableRes resourceId: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
            bitmap?.let { staticSprites[id] = it }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lấy static sprite
     */
    fun getStaticSprite(id: String): Bitmap? {
        return staticSprites[id]
    }

    /**
     * Lấy sprite sheet
     */
    fun getSpriteSheet(id: String): SpriteSheet? {
        return spriteSheetManager.getSpriteSheet(id)
    }

    /**
     * Check xem có sprite sheet không
     */
    fun hasSpriteSheet(id: String): Boolean {
        return spriteSheetManager.getSpriteSheet(id) != null
    }

    /**
     * Check xem có static sprite không
     */
    fun hasStaticSprite(id: String): Boolean {
        return staticSprites.containsKey(id)
    }

    /**
     * Release tất cả resources
     */
    fun release() {
        spriteSheetManager.releaseAll()
        staticSprites.values.forEach {
            if (!it.isRecycled) it.recycle()
        }
        staticSprites.clear()
        isLoaded = false
    }
}

// ═══════════════════════════════════════════════════════════════
// PLACEHOLDER GENERATOR (for testing without real assets)
// ═══════════════════════════════════════════════════════════════

/**
 * Tạo placeholder sprites để test khi chưa có assets thật
 */
object PlaceholderGenerator {

    /**
     * Tạo placeholder sprite sheet cho explosion
     * Mỗi frame là một vòng tròn với radius tăng dần và alpha giảm dần
     */
    fun createExplosionPlaceholder(
        frameCount: Int = 12,
        frameSize: Int = 96
    ): Bitmap {
        val width = frameCount * frameSize
        val height = frameSize

        val bitmap = createBitmap(width, height)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until frameCount) {
            val progress = i.toFloat() / (frameCount - 1)
            val centerX = i * frameSize + frameSize / 2f
            val centerY = frameSize / 2f

            // Outer glow (orange/yellow)
            val radius = frameSize * 0.2f + frameSize * 0.3f * progress
            val alpha = ((1f - progress * 0.7f) * 255).toInt()

            paint.color = android.graphics.Color.argb(alpha, 255, 200, 50)
            canvas.drawCircle(centerX, centerY, radius, paint)

            // Inner core (white/yellow)
            val innerRadius = radius * 0.5f * (1f - progress)
            paint.color = android.graphics.Color.argb(alpha, 255, 255, 200)
            canvas.drawCircle(centerX, centerY, innerRadius, paint)
        }

        return bitmap
    }

    /**
     * Tạo placeholder sprite sheet cho muzzle flash
     */
    fun createMuzzleFlashPlaceholder(
        frameCount: Int = 6,
        frameSize: Int = 64
    ): Bitmap {
        val width = frameCount * frameSize
        val height = frameSize

        val bitmap = createBitmap(width, height)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until frameCount) {
            val progress = i.toFloat() / (frameCount - 1)
            val centerX = i * frameSize + frameSize * 0.3f
            val centerY = frameSize / 2f

            // Flash cone
            val alpha = ((1f - progress) * 255).toInt()
            val length = frameSize * 0.6f * (1f - progress * 0.5f)

            paint.color = android.graphics.Color.argb(alpha, 255, 255, 100)
            paint.style = android.graphics.Paint.Style.FILL

            val path = android.graphics.Path()
            path.moveTo(centerX, centerY - frameSize * 0.15f)
            path.lineTo(centerX + length, centerY)
            path.lineTo(centerX, centerY + frameSize * 0.15f)
            path.close()

            canvas.drawPath(path, paint)

            // Core
            paint.color = android.graphics.Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(centerX, centerY, frameSize * 0.1f * (1f - progress * 0.5f), paint)
        }

        return bitmap
    }

    /**
     * Tạo placeholder sprite sheet cho spark
     */
    fun createSparkPlaceholder(
        frameCount: Int = 5,
        frameSize: Int = 32
    ): Bitmap {
        val width = frameCount * frameSize
        val height = frameSize

        val bitmap = createBitmap(width, height)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until frameCount) {
            val progress = i.toFloat() / (frameCount - 1)
            val centerX = i * frameSize + frameSize / 2f
            val centerY = frameSize / 2f

            val alpha = ((1f - progress) * 255).toInt()

            // Sparks
            paint.color = android.graphics.Color.argb(alpha, 255, 200, 50)
            paint.strokeWidth = 2f
            paint.style = android.graphics.Paint.Style.STROKE

            val sparkCount = 6
            val spreadRadius = frameSize * 0.3f * (0.3f + progress * 0.7f)

            for (j in 0 until sparkCount) {
                val angle = j * Math.PI * 2 / sparkCount + progress * 0.5
                val endX = centerX + (spreadRadius * cos(angle)).toFloat()
                val endY = centerY + (spreadRadius * sin(angle)).toFloat()
                canvas.drawLine(centerX, centerY, endX, endY, paint)
            }
        }

        return bitmap
    }
}
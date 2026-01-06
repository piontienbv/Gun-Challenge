package com.codingwithnobody.ronaldo


import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withTranslation

/**
 * ═══════════════════════════════════════════════════════════════
 * GAME RENDERER (SHARED)
 * ═══════════════════════════════════════════════════════════════
 *
 * "Họa sĩ" duy nhất của game.
 * Chịu trách nhiệm vẽ GameState lên Android Canvas.
 * Được sử dụng bởi cả:
 * 1. GameTextureOverlay (cho Video Recording)
 * 2. GameOverlay (cho Compose Preview thông qua drawIntoCanvas)
 */
class GameRenderer(private val assets: GameAssets) {

    // Reusable objects để tránh GC (Garbage Collection)
    private val destRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 60f
        isFakeBoldText = true
        setShadowLayer(4f, 2f, 2f, Color.BLACK) // Thêm bóng đổ cho dễ đọc
    }

    /**
     * Hàm vẽ chính
     */
    fun draw(canvas: Canvas, gameState: GameState, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Vẽ Barrier (Vật chắn)
        drawBarrier(canvas, gameState.barrier, w, h)

        // 2. Vẽ Target (Mục tiêu)
        if (gameState.target.isVisible) {
            drawTarget(canvas, gameState.target, w, h, gameState.timestamp)
        }

        // 3. Vẽ Bullets (Đạn)
        gameState.bullets.forEach { bullet ->
            drawBullet(canvas, bullet, w, h)
        }

        // 4. Vẽ Gun (Súng)
        drawGun(canvas, gameState.gun, w, h)

        // 5. Vẽ Effects (Nổ, Tia lửa) - Dùng Sprite System
        gameState.effects.forEach { effect ->
            drawEffect(canvas, effect, gameState.timestamp, w, h)
        }

        // 6. Vẽ UI (Điểm số)
        drawUI(canvas, gameState, w, h)
    }

    private fun drawBarrier(c: Canvas, barrier: Barrier, w: Float, h: Float) {
        val barrierX = barrier.x * w
        val barrierW = barrier.width * w

        // Lấy texture gỗ từ Assets
        val woodTexture = assets.getStaticSprite("barrier")

        var lastY = 0f
        barrier.gaps.sortedBy { it.startY }.forEach { gap ->
            val gapStartY = gap.startY * h
            val gapEndY = gap.endY * h

            if (lastY < gapStartY) {
                destRect.set(barrierX, lastY, barrierX + barrierW, gapStartY)
                if (woodTexture != null) {
                    // Vẽ ảnh gỗ (nếu có)
                    c.drawBitmap(woodTexture, null, destRect, paint)
                } else {
                    // Fallback: Vẽ màu nâu nếu chưa có ảnh
                    paint.color = Color.rgb(205, 133, 63)
                    c.drawRect(destRect, paint)
                }
            }
            lastY = gapEndY
        }
        // Vẽ đoạn cuối
        if (lastY < h) {
            destRect.set(barrierX, lastY, barrierX + barrierW, h)
            if (woodTexture != null) {
                c.drawBitmap(woodTexture, null, destRect, paint)
            } else {
                paint.color = Color.rgb(205, 133, 63)
                c.drawRect(destRect, paint)
            }
        }
    }

    private fun drawTarget(c: Canvas, target: Target, w: Float, h: Float, currentTime: Long) {
        // Nếu đang nổ thì không vẽ target (vì effect nổ sẽ che đi)
        if (target.isExploding) return

        val cx = target.x * w + (target.width * w) / 2
        val cy = target.y * h
        val size = target.width * w // Giả sử target hình vuông/tròn

        // Lấy sprite monster
        val monsterSprite = assets.getStaticSprite("monster")

        if (monsterSprite != null) {
            destRect.set(cx - size/2, cy - size/2, cx + size/2, cy + size/2)
            c.drawBitmap(monsterSprite, null, destRect, paint)
        } else {
            // Fallback: Vẽ hình tròn xanh
            paint.color = Color.rgb(78, 205, 196)
            c.drawCircle(cx, cy, size/2, paint)
        }
    }

    private fun drawGun(c: Canvas, gun: Gun, w: Float, h: Float) {
        val gunX = (gun.x + gun.recoilOffsetX) * w
        val gunY = (gun.y + gun.recoilOffsetY) * h

        // Kích thước súng (tùy chỉnh theo asset)
        val gunW = 180f
        val gunH = 120f

        c.withTranslation(gunX, gunY) {
            rotate(gun.recoilRotation)

            val gunSprite = assets.getStaticSprite("gun")
            if (gunSprite != null) {
                // Vẽ ảnh súng (căn chỉnh tâm cho đúng vị trí tay cầm)
                destRect.set(-gunW * 0.8f, -gunH / 2, gunW * 0.2f, gunH / 2)
                drawBitmap(gunSprite, null, destRect, paint)
            } else {
                // Fallback
                paint.color = Color.DKGRAY
                drawRect(-100f, -30f, 0f, 30f, paint)
            }
        }
    }

    private fun drawBullet(c: Canvas, bullet: Bullet, w: Float, h: Float) {
        val bx = bullet.x * w
        val by = bullet.y * h

        val bulletSprite = assets.getStaticSprite("bullet")
        if (bulletSprite != null) {
            c.withTranslation(bx, by) {
                rotate(bullet.rotation) // Xoay đạn nếu nảy
                // Vẽ đạn
                destRect.set(-15f, -15f, 15f, 15f)
                drawBitmap(bulletSprite, null, destRect, paint)
            }
        } else {
            // Fallback
            paint.color = Color.YELLOW
            c.drawCircle(bx, by, 10f, paint)
        }
    }

    private fun drawEffect(c: Canvas, effect: VisualEffect, currentTime: Long, w: Float, h: Float) {
        // 1. Map EffectType sang SpriteSheet ID
        val spriteSheetId = when (effect.type) {
            EffectType.MUZZLE_FLASH -> "muzzle_flash"
            EffectType.EXPLOSION -> "explosion"
            EffectType.SPARK -> "spark"
            else -> return
        }

        // 2. [FIX]: Định nghĩa kích thước chuẩn (Normalized 0..1) cho từng hiệu ứng
        // Bạn có thể tinh chỉnh số này to/nhỏ tùy ý
        var baseW = 0.1f // Mặc định 10% chiều rộng màn hình
        var baseH = 0.1f

        when (effect.type) {
            EffectType.MUZZLE_FLASH -> {
                baseW = 0.15f
                // Giữ tỷ lệ khung hình (Aspect Ratio) dựa trên w/h của màn hình
                // Giả sử flash hình vuông hoặc chữ nhật dài, cần chỉnh baseH tương ứng
                baseH = baseW * (w / h) * 0.5f // 0.5f vì flash thường dẹt
            }
            EffectType.EXPLOSION -> {
                baseW = 0.25f // Nổ to (25% màn hình)
                baseH = baseW * (w / h) // Giữ hình vuông (nếu màn hình là chữ nhật đứng)
            }
            EffectType.SPARK -> {
                baseW = 0.08f // Tia lửa nhỏ
                baseH =  baseW * (w / h) * 0.5f
            }
            else -> {}
        }

        // 3. Tạo Animation Object với kích thước đã tính
        val anim = SpriteAnimation(
            id = effect.id,
            spriteSheetId = spriteSheetId,
            x = effect.x,
            y = effect.y,
            width = baseW,
            height = baseH,
            startTimeMs = effect.startTime,
            rotation = effect.rotation,
            scaleX = effect.scale,
            scaleY = effect.scale
        )

        // 4. Vẽ
        assets.spriteRenderer.render(c, anim, currentTime, w, h)
    }

    private fun drawUI(c: Canvas, gameState: GameState, w: Float, h: Float) {
        val scoreText = "${gameState.hits} Hits"
        val timeText = "${gameState.timeRemainingSeconds}s"

        // Vẽ Score (Góc phải)
        textPaint.textAlign = Paint.Align.RIGHT
        c.drawText(scoreText, w - 50f, 100f, textPaint)

        // Vẽ Time (Ở giữa)
        textPaint.textAlign = Paint.Align.CENTER
        if (gameState.timeRemainingSeconds <= 5) textPaint.color = Color.RED else textPaint.color = Color.WHITE
        c.drawText(timeText, w / 2, 100f, textPaint)

        // Countdown to (khi bắt đầu)
        if (gameState.phase == GamePhase.COUNTDOWN) {
            textPaint.textSize = 200f
            c.drawText(gameState.countdownValue.toString(), w/2, h/2, textPaint)
            textPaint.textSize = 60f // Reset size
        }
    }
}
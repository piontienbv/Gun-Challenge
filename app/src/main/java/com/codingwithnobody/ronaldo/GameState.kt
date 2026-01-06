package com.codingwithnobody.ronaldo


/**
 * ═══════════════════════════════════════════════════════════════
 * GAME STATE - Single Source of Truth (Enhanced Version)
 * ═══════════════════════════════════════════════════════════════
 *
 * Tất cả positions được lưu theo VIDEO COORDINATES (normalized 0..1)
 * để đảm bảo consistency giữa Compose preview và Media3 overlay
 *
 * Version 2.0 additions:
 * - Physics-based bullets (vx, vy, gravity, bounce)
 * - Gun recoil animation
 * - Visual effects (muzzle flash, explosion, spark)
 */
/**
 * Gun với recoil animation
 */
data class Gun(
    val x: Float = 0.95f,           // Vị trí X (normalized 0..1) - bên phải màn hình
    val y: Float = 0.5f,            // Vị trí Y (normalized 0..1) - di chuyển lên xuống
    val direction: Int = 1,          // 1 = đi xuống, -1 = đi lên
    val speed: Float = 0.35f,         // Tốc độ di chuyển (units per second)

    val recoilOffsetX: Float = 0f,   // Offset X khi giật (positive = sang phải)
    val recoilOffsetY: Float = 0f,   // Offset Y khi giật (negative = lên trên)
    val recoilRotation: Float = 0f,  // Rotation khi giật (negative = xoay lên)
    val isRecoiling: Boolean = false,
    val recoilProgress: Float = 0f   // 0 = start, 1 = end
)

/**
 * Bullet state cho physics
 */
enum class BulletState {
    FLYING,      // Đang bay thẳng về phía target
    BOUNCING,    // Đã nảy ra khỏi barrier, đang rơi
    HIT_TARGET,  // Đã trúng target
    DESTROYED    // Đã ra khỏi màn hình hoặc bị hủy
}

/**
 * Bullet với physics-based movement
 */
data class Bullet(
    val id: String,
    val x: Float,                    // Vị trí X hiện tại (normalized)
    val y: Float,                    // Vị trí Y hiện tại (normalized)
    val vx: Float,                   // Velocity X (normalized units per second)
    val vy: Float,                   // Velocity Y (normalized units per second)
    val rotation: Float = 0f,        // Rotation khi rơi (degrees)
    val state: BulletState = BulletState.FLYING,
    val bounceCount: Int = 0         // Số lần đã nảy
) {
    // Legacy support
    val startX: Float get() = x
    val startY: Float get() = y
    val speed: Float get() = kotlin.math.sqrt(vx * vx + vy * vy)
    val isActive: Boolean get() = state != BulletState.DESTROYED
}

data class Target(
    val id: String = "monster",
    val x: Float = 0f,
    val y: Float = 0.5f,
    val width: Float = 0.3f,
    val height: Float = 0.45f,

    // Hit & Explosion state
    val isHit: Boolean = false,
    val isExploding: Boolean = false,
    val explosionStartTime: Long = 0, // Game time khi bắt đầu nổ
    val hitAnimationProgress: Float = 0f,

    // Respawn
    val isVisible: Boolean = true
)

/**
 * Barrier (cây gỗ) với các khe hở
 * Gap được định nghĩa bằng vị trí Y bắt đầu và kết thúc (normalized)
 */
data class Barrier(
    val x: Float = 0.25f,            // Vị trí X của barrier
    val width: Float = 0.08f,        // Độ rộng barrier
    val gaps: List<Gap> = listOf(    // Danh sách các khe hở
        Gap(startY = 0.35f, endY = 0.40f),  // Khe hở ở giữa
        Gap(startY = 0.75f, endY = 0.80f)   // Khe hở ở dưới
    )
)

data class Gap(
    val startY: Float,
    val endY: Float
)

/**
 * Types of visual effects
 */
enum class EffectType {
    MUZZLE_FLASH,   // Tia lửa đầu nòng súng
    EXPLOSION,      // Nổ khi trúng target
    SPARK,          // Tia lửa khi đạn chạm barrier
    BULLET_TRAIL    // Vệt đạn (optional)
}

/**
 * Visual effect instance
 */
data class VisualEffect(
    val id: String,
    val type: EffectType,
    val x: Float,                    // Center X (normalized)
    val y: Float,                    // Center Y (normalized)
    val startTime: Long,             // Game time when effect started
    val rotation: Float = 0f,        // Rotation in degrees
    val scale: Float = 1f,           // Scale multiplier
    val data: Map<String, Any> = emptyMap() // Additional data
) {
    companion object {
        // Effect durations (ms)
        const val MUZZLE_FLASH_DURATION = 150L
        const val EXPLOSION_DURATION = 500L
        const val SPARK_DURATION = 165L
    }

    fun getDuration(): Long = when (type) {
        EffectType.MUZZLE_FLASH -> MUZZLE_FLASH_DURATION
        EffectType.EXPLOSION -> EXPLOSION_DURATION
        EffectType.SPARK -> SPARK_DURATION
        EffectType.BULLET_TRAIL -> 100L
    }

    fun isExpired(currentTime: Long): Boolean {
        return currentTime - startTime >= getDuration()
    }

    fun getProgress(currentTime: Long): Float {
        val elapsed = currentTime - startTime
        return (elapsed.toFloat() / getDuration()).coerceIn(0f, 1f)
    }
}


sealed class GameEvent {
    abstract val timestamp: Long

    data class Tap(
        override val timestamp: Long,
        val x: Float,
        val y: Float
    ) : GameEvent()

    data class BulletFired(
        override val timestamp: Long,
        val bulletId: String,
        val fromX: Float,
        val fromY: Float
    ) : GameEvent()

    data class TargetHit(
        override val timestamp: Long,
        val targetId: String
    ) : GameEvent()

    data class BulletBlocked(
        override val timestamp: Long,
        val bulletId: String
    ) : GameEvent()
}


enum class GamePhase {
    READY,          // Chờ user bấm Record
    COUNTDOWN,      // Đếm ngược 3-2-1
    PLAYING,        // Đang chơi game
    FINISHED,       // Hết thời gian
    EXPORTING       // Đang export video
}


data class GameState(
    // Thời gian
    val timestamp: Long = 0L,                    // Thời gian game đã chạy (ms)
    val gameDuration: Long = 30_000L,            // Tổng thời gian game (30s)
    val countdownValue: Int = 3,                 // Giá trị đếm ngược

    // Game phase
    val phase: GamePhase = GamePhase.READY,

    // Game objects
    val gun: Gun = Gun(),
    val bullets: List<Bullet> = emptyList(),
    val target: Target = Target(y = 0.375f),
    val barrier: Barrier = Barrier(),

    // Visual Effects (NEW)
    val effects: List<VisualEffect> = emptyList(),

    // Score
    val hits: Int = 0,
    val totalShots: Int = 0,

    // Recording
    val isRecording: Boolean = false,
    val recordingStartTime: Long = 0L
) {
    val timeRemaining: Long
        get() = maxOf(0, gameDuration - timestamp)

    val timeRemainingSeconds: Int
        get() = (timeRemaining / 1000).toInt()

    val accuracy: Float
        get() = if (totalShots > 0) hits.toFloat() / totalShots else 0f
}


data class TimestampedGameState(
    val timestamp: Long,
    val state: GameState
)


data class GameReplayData(
    val events: List<GameEvent>,
    val duration: Long,
    val finalScore: Int
)
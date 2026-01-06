package com.codingwithnobody.ronaldo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt


/**
 * ═══════════════════════════════════════════════════════════════
 * GAME ENGINE (Enhanced Version with Physics & Effects)
 * ═══════════════════════════════════════════════════════════════
 *
 * Xử lý:
 * - Game loop (60fps)
 * - Physics (gravity, bounce, ricochet)
 * - Collision detection
 * - Visual effects spawning
 * - Gun recoil animation
 * - Event recording for replay
 */
class GameEngine(
    private val scope: CoroutineScope
) {
    companion object {
        private const val FRAME_TIME_MS = 16L  // ~60fps
        private const val COUNTDOWN_DURATION = 3

        // Physics constants
        const val GRAVITY = 0.8f              // Gravity acceleration (normalized units/s²)
        const val BULLET_SPEED = 1.2f         // Initial bullet speed (normalized units/s)
        const val BOUNCE_COEFFICIENT = 0.6f   // Velocity retention after bounce (0-1)
        const val BOUNCE_RANDOM_FACTOR = 0.3f // Random variance in bounce direction
        const val MAX_BOUNCES = 3             // Max bounces before bullet disappears
        const val FLOOR_Y = 1.1f              // Y position of "floor" (off screen)

        // Gun recoil
        const val RECOIL_OFFSET_X = 0.02f     // Max recoil offset X
        const val RECOIL_OFFSET_Y = -0.015f   // Max recoil offset Y (negative = up)
        const val RECOIL_ROTATION = -8f       // Max recoil rotation (degrees)
        const val RECOIL_DURATION_MS = 150L   // Recoil animation duration

        // Target respawn
        const val TARGET_RESPAWN_DELAY_MS = 800L
    }

    // ══════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _events = mutableListOf<GameEvent>()
    val recordedEvents: List<GameEvent> get() = _events.toList()

    // State history for Media3 overlay (ring buffer)
    private val stateHistory = ArrayDeque<TimestampedGameState>(180) // ~3 seconds at 60fps

    private var gameLoopJob: Job? = null
    private var gameStartTime: Long = 0L

    // Random for physics variance
    private val random = java.util.Random()

    // ══════════════════════════════════════════════════════════════
    // PUBLIC METHODS
    // ══════════════════════════════════════════════════════════════

    /**
     * Bắt đầu countdown và game
     */
    fun startGame() {
        if (_gameState.value.phase != GamePhase.READY) return

        _events.clear()
        stateHistory.clear()

        // Bắt đầu countdown
        _gameState.update { it.copy(phase = GamePhase.COUNTDOWN, countdownValue = COUNTDOWN_DURATION) }

        scope.launch {
            // Countdown 3-2-1
            for (i in COUNTDOWN_DURATION downTo 1) {
                _gameState.update { it.copy(countdownValue = i) }
                delay(1000)
            }

            // Bắt đầu game
            gameStartTime = System.currentTimeMillis()
            _gameState.update {
                it.copy(
                    phase = GamePhase.PLAYING,
                    timestamp = 0L,
                    isRecording = true,
                    recordingStartTime = gameStartTime
                )
            }

            // Bắt đầu game loop
            startGameLoop()
        }
    }

    /**
     * User tap để bắn
     */
    fun onTap(normalizedX: Float, normalizedY: Float) {
        val currentState = _gameState.value

        if (currentState.phase != GamePhase.PLAYING) return
        if (currentState.gun.isRecoiling) return // Prevent spam shooting

        // Record event
        val timestamp = currentState.timestamp
        _events.add(GameEvent.Tap(timestamp, normalizedX, normalizedY))

        // Tạo đạn từ vị trí súng với physics
        val gun = currentState.gun
        val bulletId = UUID.randomUUID().toString()

        val bullet = Bullet(
            id = bulletId,
            x = gun.x - 0.08f,  // Bắt đầu từ đầu nòng súng
            y = gun.y + gun.recoilOffsetY,
            vx = -BULLET_SPEED,  // Bay về bên trái
            vy = 0f,             // Không có velocity Y ban đầu
            state = BulletState.FLYING
        )

        // Record bullet fired event
        _events.add(GameEvent.BulletFired(timestamp, bulletId, bullet.x, bullet.y))

        // Create muzzle flash effect
        val muzzleFlash = VisualEffect(
            id = UUID.randomUUID().toString(),
            type = EffectType.MUZZLE_FLASH,
            x = gun.x - 0.06f,
            y = gun.y,
            startTime = timestamp,
            rotation = 0f
        )

        // Cập nhật state với bullet, effect, và gun recoil
        _gameState.update { state ->
            state.copy(
                bullets = state.bullets + bullet,
                effects = state.effects + muzzleFlash,
                totalShots = state.totalShots + 1,
                gun = state.gun.copy(
                    isRecoiling = true,
                    recoilProgress = 0f
                )
            )
        }
    }

    /**
     * Dừng game
     */
    fun stopGame() {
        gameLoopJob?.cancel()
        _gameState.update { it.copy(phase = GamePhase.FINISHED, isRecording = false) }
    }

    /**
     * Reset game về trạng thái ban đầu
     */
    fun resetGame() {
        gameLoopJob?.cancel()
        _events.clear()
        stateHistory.clear()
        _gameState.value = GameState()
    }

    /**
     * Lấy state tại một thời điểm cụ thể (cho Media3 overlay)
     */
    fun getStateAtTimestamp(timestampMs: Long): GameState {
        synchronized(stateHistory) {
            // Tìm state gần nhất
            return stateHistory
                .minByOrNull { abs(it.timestamp - timestampMs) }
                ?.state
                ?: _gameState.value
        }
    }

    /**
     * Lấy replay data để export
     */
    fun getReplayData(): GameReplayData {
        val state = _gameState.value
        return GameReplayData(
            events = recordedEvents,
            duration = state.gameDuration,
            finalScore = state.hits
        )
    }

    // ══════════════════════════════════════════════════════════════
    // GAME LOOP
    // ══════════════════════════════════════════════════════════════

    private fun startGameLoop() {
        gameLoopJob = scope.launch {
            var lastFrameTime = System.currentTimeMillis()

            while (isActive && _gameState.value.phase == GamePhase.PLAYING) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = (currentTime - lastFrameTime) / 1000f  // seconds
                lastFrameTime = currentTime

                val gameTime = currentTime - gameStartTime

                // Kiểm tra hết giờ
                if (gameTime >= _gameState.value.gameDuration) {
                    stopGame()
                    break
                }

                // Update game state
                updateGameState(deltaTime, gameTime)

                // Lưu vào history cho replay
                saveStateToHistory(gameTime)

                delay(FRAME_TIME_MS)
            }
        }
    }

    private fun updateGameState(deltaTime: Float, gameTime: Long) {
        _gameState.update { state ->
            var newState = state.copy(timestamp = gameTime)

            // 1. Update Gun position (di chuyển lên xuống)
            newState = updateGunPosition(newState, deltaTime)

            // 2. Update Gun recoil animation
            newState = updateGunRecoil(newState, deltaTime)

            // 3. Update Bullets with physics
            newState = updateBullets(newState, deltaTime)

            // 4. Check collisions
            newState = checkCollisions(newState)

            // 5. Update visual effects
            newState = updateEffects(newState, gameTime)

            // 6. Update target (explosion, respawn)
            newState = updateTarget(newState, gameTime)

            newState
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GUN UPDATES
    // ══════════════════════════════════════════════════════════════

    private fun updateGunPosition(state: GameState, deltaTime: Float): GameState {
        val gun = state.gun
        var newY = gun.y + (gun.direction * gun.speed * deltaTime)
        var newDirection = gun.direction

        // Bounce at boundaries (giữ trong khoảng 0.15 - 0.85)
        if (newY >= 0.85f) {
            newY = 0.85f
            newDirection = -1
        } else if (newY <= 0.15f) {
            newY = 0.15f
            newDirection = 1
        }

        return state.copy(
            gun = gun.copy(y = newY, direction = newDirection)
        )
    }

    private fun updateGunRecoil(state: GameState, deltaTime: Float): GameState {
        val gun = state.gun

        if (!gun.isRecoiling) return state

        // Update recoil progress
        val progressDelta = deltaTime * 1000 / RECOIL_DURATION_MS
        val newProgress = (gun.recoilProgress + progressDelta).coerceAtMost(1f)

        // Recoil curve: quick snap back, then ease out to rest
        // Using a simple ease-out curve
        val recoilCurve = if (newProgress < 0.3f) {
            // Quick snap to max recoil (0 -> 0.3 progress)
            newProgress / 0.3f
        } else {
            // Ease back to rest (0.3 -> 1.0 progress)
            1f - ((newProgress - 0.3f) / 0.7f)
        }

        val offsetX = RECOIL_OFFSET_X * recoilCurve
        val offsetY = RECOIL_OFFSET_Y * recoilCurve
        val rotation = RECOIL_ROTATION * recoilCurve

        val isStillRecoiling = newProgress < 1f

        return state.copy(
            gun = gun.copy(
                recoilOffsetX = offsetX,
                recoilOffsetY = offsetY,
                recoilRotation = rotation,
                recoilProgress = newProgress,
                isRecoiling = isStillRecoiling
            )
        )
    }

    // ══════════════════════════════════════════════════════════════
    // BULLET PHYSICS
    // ══════════════════════════════════════════════════════════════

    private fun updateBullets(state: GameState, deltaTime: Float): GameState {
        val updatedBullets = state.bullets.mapNotNull { bullet ->
            when (bullet.state) {
                BulletState.FLYING -> updateFlyingBullet(bullet, deltaTime)
                BulletState.BOUNCING -> updateBouncingBullet(bullet, deltaTime)
                BulletState.HIT_TARGET, BulletState.DESTROYED -> null
            }
        }

        return state.copy(bullets = updatedBullets)
    }

    private fun updateFlyingBullet(bullet: Bullet, deltaTime: Float): Bullet? {
        // Di chuyển đạn theo velocity
        val newX = bullet.x + bullet.vx * deltaTime
        val newY = bullet.y + bullet.vy * deltaTime

        // Remove nếu ra khỏi màn hình bên trái
        if (newX < -0.1f) return null

        return bullet.copy(x = newX, y = newY)
    }

    private fun updateBouncingBullet(bullet: Bullet, deltaTime: Float): Bullet? {
        // Apply gravity
        val newVy = bullet.vy + GRAVITY * deltaTime

        // Update position
        val newX = bullet.x + bullet.vx * deltaTime
        val newY = bullet.y + newVy * deltaTime

        // Update rotation based on velocity direction
        val newRotation = bullet.rotation + bullet.vx * 500 * deltaTime

        // Check if bullet is off screen
        if (newY > FLOOR_Y || newX < -0.1f || newX > 1.1f) {
            return null
        }

        return bullet.copy(
            x = newX,
            y = newY,
            vy = newVy,
            rotation = newRotation
        )
    }

    // ══════════════════════════════════════════════════════════════
    // COLLISION DETECTION
    // ══════════════════════════════════════════════════════════════

    private fun checkCollisions(state: GameState): GameState {
        var newState = state
        val barrier = state.barrier
        val target = state.target
        val newEffects = state.effects.toMutableList()

        val updatedBullets = state.bullets.map { bullet ->
            if (bullet.state != BulletState.FLYING) return@map bullet

            // Check collision với barrier
            val barrierLeft = barrier.x
            val barrierRight = barrier.x + barrier.width

            if (bullet.x <= barrierRight && bullet.x >= barrierLeft - 0.02f) {
                // Đạn đang trong vùng barrier, check gap
                val isInGap = barrier.gaps.any { gap ->
                    bullet.y >= gap.startY && bullet.y <= gap.endY
                }

                if (!isInGap) {
                    // Đạn chạm barrier - BOUNCE!
                    _events.add(GameEvent.BulletBlocked(state.timestamp, bullet.id))

                    // Create spark effect
                    val spark = VisualEffect(
                        id = UUID.randomUUID().toString(),
                        type = EffectType.SPARK,
                        x = barrierRight,
                        y = bullet.y,
                        startTime = state.timestamp
                    )
                    newEffects.add(spark)

                    // Calculate bounce
                    if (bullet.bounceCount < MAX_BOUNCES) {
                        val bounceVx = -bullet.vx * BOUNCE_COEFFICIENT *
                                (0.8f + random.nextFloat() * 0.4f)
                        val bounceVy = (random.nextFloat() - 0.5f) * BOUNCE_RANDOM_FACTOR

                        return@map bullet.copy(
                            x = barrierRight + 0.01f,  // Push out of barrier
                            vx = bounceVx,
                            vy = bounceVy,
                            state = BulletState.BOUNCING,
                            bounceCount = bullet.bounceCount + 1
                        )
                    } else {
                        return@map bullet.copy(state = BulletState.DESTROYED)
                    }
                }
            }

            // Check collision với target (only if target is visible and not exploding)
            if (target.isVisible && !target.isExploding) {
                val targetCenterX = target.x + target.width / 2
                val targetCenterY = target.y
                val hitRadius = target.width / 2

                val dx = bullet.x - targetCenterX
                val dy = bullet.y - targetCenterY
                val distance = sqrt(dx * dx + dy * dy)

                if (distance < hitRadius) {
                    // HIT!
                    _events.add(GameEvent.TargetHit(state.timestamp, target.id))

                    // Create explosion effect
                    val explosion = VisualEffect(
                        id = UUID.randomUUID().toString(),
                        type = EffectType.EXPLOSION,
                        x = targetCenterX,
                        y = targetCenterY,
                        startTime = state.timestamp,
                        scale = 1.5f
                    )
                    newEffects.add(explosion)

                    // Update target to exploding state
                    newState = newState.copy(
                        hits = newState.hits + 1,
                        target = target.copy(
                            isHit = true,
                            isExploding = true,
                            explosionStartTime = state.timestamp,
                            isVisible = false
                        )
                    )

                    return@map bullet.copy(state = BulletState.HIT_TARGET)
                }
            }

            bullet
        }

        return newState.copy(
            bullets = updatedBullets.filter { it.state != BulletState.DESTROYED && it.state != BulletState.HIT_TARGET },
            effects = newEffects
        )
    }

    // ══════════════════════════════════════════════════════════════
    // EFFECTS & TARGET UPDATES
    // ══════════════════════════════════════════════════════════════

    private fun updateEffects(state: GameState, gameTime: Long): GameState {
        // Remove expired effects
        val activeEffects = state.effects.filter { !it.isExpired(gameTime) }
        return state.copy(effects = activeEffects)
    }

    private fun updateTarget(state: GameState, gameTime: Long): GameState {
        val target = state.target
        if (!target.isExploding) return state

        val explosionElapsed = gameTime - target.explosionStartTime
        if (explosionElapsed >= VisualEffect.EXPLOSION_DURATION + TARGET_RESPAWN_DELAY_MS) {

            // --- LOGIC MỚI BẮT ĐẦU TẠI ĐÂY ---

            // 1. Lấy danh sách các khe hở từ Barrier
            val gaps = state.barrier.gaps

            // 2. Chọn ngẫu nhiên 1 khe hở
            val selectedGap = gaps[random.nextInt(gaps.size)]

            // 3. Tính toán vị trí Y để Target nằm gọn trong hoặc "lấp ló" sau Gap
            // Cách A: Target nằm chính giữa Gap
            val gapCenterY = (selectedGap.startY + selectedGap.endY) / 2

            // Cách B: Target "lấp ló" (Ngẫu nhiên một điểm trong khoảng của Gap)
            // Chúng ta giới hạn để tâm của Target không đi quá xa khỏi khe hở
            val targetHeight = state.target.height
            val offset = (random.nextFloat() - 0.5f) * (targetHeight * 0.4f)
            val newY = gapCenterY + offset

            return state.copy(
                target = Target(
                    y = newY, // Vị trí Y mới dựa trên Gap
                    isVisible = true,
                    isExploding = false
                )
            )
            // --- KẾT THÚC LOGIC MỚI ---
        }
        return state
    }

    private fun saveStateToHistory(timestamp: Long) {
        synchronized(stateHistory) {
            stateHistory.addLast(TimestampedGameState(timestamp, _gameState.value))

            // Giữ tối đa 3 giây
            while (stateHistory.size > 180) {
                stateHistory.removeFirst()
            }
        }
    }
}
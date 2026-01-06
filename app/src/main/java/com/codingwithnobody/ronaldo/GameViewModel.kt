package com.codingwithnobody.ronaldo


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow

/**
 * ═══════════════════════════════════════════════════════════════
 * GAME VIEWMODEL
 * ═══════════════════════════════════════════════════════════════
 *
 * Kết nối GameEngine với Compose UI
 * Cung cấp interface cho cả preview (Compose) và recording (Media3)
 */
class GameViewModel : ViewModel() {

    private val gameEngine = GameEngine(viewModelScope)

    // State cho UI observe
    val gameState: StateFlow<GameState> = gameEngine.gameState

    // ══════════════════════════════════════════════════════════════
    // UI ACTIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Bắt đầu game (countdown + play)
     */
    fun startGame() {
        gameEngine.startGame()
    }

    /**
     * User tap màn hình để bắn
     * @param normalizedX X position (0..1)
     * @param normalizedY Y position (0..1)
     */
    fun onTap(normalizedX: Float, normalizedY: Float) {
        gameEngine.onTap(normalizedX, normalizedY)
    }

    /**
     * Dừng game sớm
     */
    fun stopGame() {
        gameEngine.stopGame()
    }

    /**
     * Reset game
     */
    fun resetGame() {
        gameEngine.resetGame()
    }

    // ══════════════════════════════════════════════════════════════
    // FOR MEDIA3 OVERLAY
    // ══════════════════════════════════════════════════════════════

    /**
     * Lấy state tại thời điểm cụ thể (cho TextureOverlay)
     */
    fun getStateAtTimestamp(timestampMs: Long): GameState {
        return gameEngine.getStateAtTimestamp(timestampMs)
    }

    /**
     * Lấy replay data để export video
     */
    fun getReplayData(): GameReplayData {
        return gameEngine.getReplayData()
    }

    /**
     * Lấy danh sách events đã record
     */
    fun getRecordedEvents(): List<GameEvent> {
        return gameEngine.recordedEvents
    }

    override fun onCleared() {
        super.onCleared()
        gameEngine.stopGame()
    }
}
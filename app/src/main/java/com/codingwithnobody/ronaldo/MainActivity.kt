package com.codingwithnobody.ronaldo

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import com.google.common.collect.ImmutableList
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.effect.TextureOverlay

/**
 * ═══════════════════════════════════════════════════════════════
 * MAIN ACTIVITY
 * ═══════════════════════════════════════════════════════════════
 *
 * Kết hợp:
 * - CameraX Preview + VideoCapture
 * - Media3 Effect (với GameBitmapOverlay)
 * - Compose UI (GameOverlay cho preview)
 *
 * Architecture:
 * - GameViewModel quản lý game state (Single Source of Truth)
 * - GameOverlay (Compose) - what user sees on screen
 * - GameBitmapOverlay (Media3) - what gets recorded to video
 * - Cả hai đều read từ cùng GameState
 */
@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var gameAssets: GameAssets

    companion object {
        private const val TAG = "GunChallenge"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    // ViewModel
    private val gameViewModel: GameViewModel by viewModels()

    // Camera components
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var previewView: PreviewView? = null

    // Overlay for video
    private var gameTextureOverlay: GameTextureOverlay? = null

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions
        if (!allPermissionsGranted()) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
        gameAssets = GameAssets(this)
        gameAssets.loadAssets(
            gunResId = R.drawable.gun,
            monsterResId = R.drawable.monster,
            barrierResId = R.drawable.wood_barrier,
            bulletResId = R.drawable.bullet,
            muzzleFlashResId = R.drawable.muzzle_flash,
            explosionResId = R.drawable.explosion,
            sparkResId = R.drawable.muzzle_flash
        )
        val explosionBitmap = PlaceholderGenerator.createExplosionPlaceholder()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    GunChallengeScreen(
                        viewModel = gameViewModel,
                        onStartRecording = { startRecording() },
                        gameAssets = gameAssets,
                        onStopRecording = { stopRecording() },
                        setupCamera = { preview -> setupCamera(preview) }
                    )
                }
            }
        }
    }

    private fun setupCamera(preview: PreviewView) {
        this.previewView = preview

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // 1. Preview UseCase
                val previewUseCase = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = preview.surfaceProvider }

                // 2. Video Capture UseCase
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                // 3. Khởi tạo GameTextureOverlay
                // Lưu ý: Khởi tạo trước khi đưa vào Effect
                gameTextureOverlay = GameTextureOverlay(
                    gameAssets = gameAssets,
                    getGameState = { timestampMs ->
                        gameViewModel.getStateAtTimestamp(timestampMs)
                    }
                )

                // 4. Tạo OverlayEffect
                val overlayEffect = OverlayEffect(
                    ImmutableList.of<TextureOverlay>(gameTextureOverlay!!)
                )

                // 5. Tạo danh sách Effects
                val effects = ImmutableList.of<Effect>(overlayEffect)

                // 6. Tạo Media3Effect (Cầu nối CameraX - Media3)
                // Lưu ý: Kiểm tra lại constructor của Media3Effect trong phiên bản alpha04
                // Thường thì ta dùng Media3Effect.create(...) hoặc constructor tùy bản.
                // Giả sử constructor này đúng với bản bạn đang dùng:
                val media3Effect = Media3Effect(
                    application,
                    CameraEffect.VIDEO_CAPTURE,
                    ContextCompat.getMainExecutor(application)
                ) { error -> Log.e(TAG, "Media3Effect error: $error") }

                // Gắn effects vào Media3Effect
                // Lưu ý: Hàm setEffects có thể khác nhau tùy version, hãy đảm bảo API đúng
                media3Effect.setEffects(effects)

                // 7. Build UseCaseGroup
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(previewUseCase)
                    .addUseCase(videoCapture!!)
                    .addEffect(media3Effect) // Gắn Effect vào pipeline
                    .build()

                // 8. Bind to Lifecycle
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    useCaseGroup
                )
                Log.d(TAG, "Camera bound successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        // Reset overlay timing
        gameTextureOverlay?.resetRecordingTime()

        // Create output file
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "GunChallenge_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/GunChallenge")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        // Start recording
        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d(TAG, "Recording started")
                        // Start game khi recording bắt đầu
                        gameViewModel.startGame()
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            val msg = "Video saved: ${event.outputResults.outputUri}"
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            // In lỗi chi tiết ra Logcat
                            val errorCode = event.error
                            val errorMsg = event.cause?.message ?: "Unknown error"
                            Log.e(TAG, "RECORDING FAILED: Code=$errorCode, Msg=$errorMsg")

                            Toast.makeText(this, "Save failed: $errorCode", Toast.LENGTH_LONG).show()
                        }
                        gameViewModel.resetGame()
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        gameViewModel.stopGame()
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        gameTextureOverlay?.release()
    }
}


@Composable
fun GunChallengeScreen(
    viewModel: GameViewModel,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    gameAssets: GameAssets,
    setupCamera: (PreviewView) -> Unit
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var isRecording by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { preview ->
                    setupCamera(preview)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Game Overlay (Compose - what user sees)
        GameOverlay(
            gameState = gameState,
            gameAssets = gameAssets,
            onTap = { x, y ->
                viewModel.onTap(x, y)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 3: Control UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Record/Stop button
            RecordButton(
                isRecording = isRecording,
                isEnabled = gameState.phase == GamePhase.READY ||
                        gameState.phase == GamePhase.PLAYING ||
                        gameState.phase == GamePhase.FINISHED,
                onClick = {
                    if (isRecording) {
                        onStopRecording()
                        isRecording = false
                    } else {
                        onStartRecording()
                        isRecording = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status text
            Text(
                text = when (gameState.phase) {
                    GamePhase.READY -> "Tap to Record & Play"
                    GamePhase.COUNTDOWN -> "Get Ready..."
                    GamePhase.PLAYING -> "Tap to Shoot!"
                    GamePhase.FINISHED -> "Game Over! Score: ${gameState.hits}"
                    GamePhase.EXPORTING -> "Exporting..."
                },
                color = Color.White,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun RecordButton(
    isRecording: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) Color.Red else Color(0xFF8B0000),
            disabledContainerColor = Color.Gray
        )
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isRecording) {
                // Stop icon (square)
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.White)
                )
            } else {
                // Record icon (circle)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}
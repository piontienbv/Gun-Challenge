package com.codingwithnobody.myandroidprojectgaussianblur

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.MediaStore
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview
import androidx.camera.core.CameraSelector
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.CameraEffect.IMAGE_CAPTURE
import androidx.camera.core.CameraEffect.PREVIEW
import androidx.camera.core.CameraEffect.VIDEO_CAPTURE
import androidx.camera.core.UseCaseGroup
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.PermissionChecker
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.DrawableOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import com.google.common.collect.ImmutableList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.createBitmap
import androidx.media3.effect.BitmapOverlay

@UnstableApi
class MainActivity : AppCompatActivity() {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    private lateinit var viewFinder: PreviewView
    private lateinit var button: Button

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewFinder = findViewById(R.id.viewFinder)
        button = findViewById(R.id.video_capture_button)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        button.setOnClickListener {
            captureVideo()
        }
    }

    // Implements VideoCapture use case, including start and stop capturing.
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        button.isEnabled = false

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        button.apply {
                            text = "Stop"
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        button.apply {
                            text = "Start"
                            isEnabled = true
                        }
                    }
                }
            }
    }


    private fun startCamera() {



        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = viewFinder.surfaceProvider
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val useCaseGroupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(videoCapture!!)

            val media3Effect = Media3Effect(
                application,
                PREVIEW or VIDEO_CAPTURE or IMAGE_CAPTURE,
                ContextCompat.getMainExecutor(application),
            ) {}


            val effects = ImmutableList.Builder<Effect>()
            val overlay = createOverlayEffectFromBundle()
            overlay?.let { effects.add(it) }

            media3Effect.setEffects(effects.build())
            useCaseGroupBuilder.addEffect(media3Effect)

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
//                cameraProvider.bindToLifecycle(
//                    this, cameraSelector, preview, videoCapture)
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, useCaseGroupBuilder.build())

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    /*private fun createOverlayEffectFromBundle(): OverlayEffect? {
        val overlaysBuilder = ImmutableList.Builder<TextureOverlay>()
//        //1
        val logoSettings =
            StaticOverlaySettings.Builder() // Place the logo in the bottom left corner of the screen with some padding from the
                .setRotationDegrees(90f)
                .setScale(5f,5f)
                // edges.
                .setOverlayFrameAnchor(0f, 0f) // Top-left
                .setBackgroundFrameAnchor(0.05f, 0.05f)
                .build()
        val logo: Drawable = try {
            AppCompatResources.getDrawable(this,R.drawable.ic_hello_summer_2)!!
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException(e)
        }
        logo.setBounds( *//* left= *//*
            0,  *//* top= *//*0, logo.intrinsicWidth, logo.intrinsicHeight
        )
        val logoOverlay: TextureOverlay =
            DrawableOverlay.createStaticDrawableOverlay(logo, logoSettings)
       // val timerOverlay: TextureOverlay = TimerOverlay()
        overlaysBuilder.add(logoOverlay)

        val overlays = overlaysBuilder.build()
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }*/
    private fun createOverlayEffectFromBundle(): OverlayEffect? {
        // 1. Inflate layout XML của bạn thành một đối tượng View
        // Đảm bảo bạn đã tạo file layout tên là "video_overlay_layout.xml"
        val overlayView = layoutInflater.inflate(R.layout.template_daily_1, null)

        // 2. Chuyển View thành Bitmap bằng hàm tiện ích
        val overlayBitmap = createBitmapFromView(overlayView)

        // 3. Sử dụng BitmapOverlay để tạo lớp phủ
        val overlaySettings =
            StaticOverlaySettings.Builder()
                // Xoay 90 độ để khớp với hướng video dọc
                .setRotationDegrees(90f)
                // Đặt vị trí của lớp phủ. Ví dụ: đặt ở góc dưới bên trái
                //.setOverlayFrameAnchor(0f, 0f) // Mỏ neo ở góc dưới-trái của bitmap
               // .setBackgroundFrameAnchor(0.05f, 0.95f) // Đặt mỏ neo đó ở vị trí 5% từ trái, 95% từ trên
                .build()

        val bitmapOverlay: TextureOverlay =
            BitmapOverlay.createStaticBitmapOverlay(overlayBitmap, overlaySettings)

        val overlays = ImmutableList.of(bitmapOverlay)
        return OverlayEffect(overlays)
    }
    private fun createBitmapFromView(view: View): Bitmap {
        // Cập nhật dữ liệu động cho các TextView
        val timeTextView = view.findViewById<TextView>(R.id.tvTime)
        val dateTextView = view.findViewById<TextView>(R.id.tvDate)
        val sdfTime = SimpleDateFormat("h:mm a", Locale.getDefault())
        val sdfDate = SimpleDateFormat("EEEE, dd-MMM-yyyy", Locale.getDefault())

        // Bạn có thể cập nhật các view khác ở đây (nhiệt độ, vị trí,...)
        timeTextView.text = sdfTime.format(Date())
        dateTextView.text = sdfDate.format(Date())

        // Đo đạc và layout cho View để nó có kích thước thật
        // Ở đây ta giả định chiều rộng của video là 1080px để đo đạc cho chính xác
        val widthSpec = View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        val measuredWidth = view.measuredWidth
        val measuredHeight = view.measuredHeight
        view.layout(0, 0, measuredWidth, measuredHeight)

        // Tạo Bitmap và vẽ View lên đó
        val bitmap = createBitmap(measuredWidth, measuredHeight)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
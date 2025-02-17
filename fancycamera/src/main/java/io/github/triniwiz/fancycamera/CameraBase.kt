package io.github.triniwiz.fancycamera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.OrientationEventListener
import android.widget.FrameLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors


abstract class CameraBase @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    var enableAudio: Boolean = true
    abstract var retrieveLatestImage: Boolean
    internal var latestImage: Bitmap? = null
    var processEveryNthFrame: Int = 0
    internal var currentFrame: Int = 0
    abstract var pause: Boolean
    abstract var whiteBalance: WhiteBalance
    abstract var position: CameraPosition
    abstract var rotation: CameraOrientation
    abstract var flashMode: CameraFlashMode
    abstract var allowExifRotation: Boolean
    abstract var autoSquareCrop: Boolean
    abstract var autoFocus: Boolean
    abstract var saveToGallery: Boolean
    abstract var maxAudioBitRate: Int
    abstract var maxVideoBitrate: Int
    abstract var maxVideoFrameRate: Int
    abstract var disableHEVC: Boolean
    abstract var quality: Quality
    abstract val db: Double
    abstract val amplitude: Double
    abstract val amplitudeEMA: Double
    abstract var isAudioLevelsEnabled: Boolean
    abstract val numberOfCameras: Int
    abstract var detectorType: DetectorType
    var overridePhotoWidth: Int = -1
    var overridePhotoHeight: Int = -1
    abstract fun stop()
    abstract fun release()
    abstract fun startPreview()
    abstract fun stopPreview()
    abstract fun startRecording()
    abstract fun stopRecording()
    abstract fun takePhoto()
    abstract fun hasFlash(): Boolean
    abstract fun cameraRecording(): Boolean
    abstract fun toggleCamera()
    abstract fun getSupportedRatios(): Array<String>
    abstract fun getAvailablePictureSizes(ratio: String): Array<Size>
    abstract var displayRatio: String
    abstract var pictureSize: String
    abstract var enablePinchZoom: Boolean
    abstract var zoom: Float
    internal var onBarcodeScanningListener: ImageAnalysisCallback? = null
    internal var onFacesDetectedListener: ImageAnalysisCallback? = null
    internal var onImageLabelingListener: ImageAnalysisCallback? = null
    internal var onObjectDetectedListener: ImageAnalysisCallback? = null
    internal var onPoseDetectedListener: ImageAnalysisCallback? = null
    internal var onTextRecognitionListener: ImageAnalysisCallback? = null
    internal var onSurfaceUpdateListener: SurfaceUpdateListener? = null
    internal var onSelfieSegmentationListener: ImageAnalysisCallback? = null

    internal fun resetCurrentFrame() {
        if (isProcessingEveryNthFrame()) {
            currentFrame = 0
        }
    }

    internal fun isProcessingEveryNthFrame(): Boolean {
        return processEveryNthFrame > 0
    }

    internal fun incrementCurrentFrame() {
        if (isProcessingEveryNthFrame()) {
            currentFrame++
        }
    }

    fun setonSurfaceUpdateListener(callback: SurfaceUpdateListener?) {
        onSurfaceUpdateListener = callback
    }

    fun setOnBarcodeScanningListener(callback: ImageAnalysisCallback?) {
        onBarcodeScanningListener = callback
    }

    fun setOnFacesDetectedListener(callback: ImageAnalysisCallback?) {
        onFacesDetectedListener = callback
    }

    fun setOnImageLabelingListener(callback: ImageAnalysisCallback?) {
        onImageLabelingListener = callback
    }

    fun setOnObjectDetectedListener(callback: ImageAnalysisCallback?) {
        onObjectDetectedListener = callback
    }

    fun setOnPoseDetectedListener(callback: ImageAnalysisCallback?) {
        onPoseDetectedListener = callback
    }

    fun setOnTextRecognitionListener(callback: ImageAnalysisCallback?) {
        onTextRecognitionListener = callback
    }

    fun setOnSelfieSegmentationListener(callback: ImageAnalysisCallback?) {
        onSelfieSegmentationListener = callback
    }

    internal fun stringSizeToSize(value: String): Size {
        val size = value.split("x")
        val width = size[0].toIntOrNull(10) ?: 0
        val height = size[1].toIntOrNull() ?: 0
        return Size(width, height)
    }

    fun toggleFlash() {
        flashMode = if (flashMode == CameraFlashMode.OFF) {
            CameraFlashMode.ON
        } else {
            CameraFlashMode.OFF
        }
    }

    abstract val previewSurface: Any

    internal val mainHandler = Handler(Looper.getMainLooper())
    internal var analysisExecutor = Executors.newCachedThreadPool()

    internal var barcodeScannerOptions: Any? = null
    fun setBarcodeScannerOptions(value: Any) {
        if (!isBarcodeScanningSupported) return
        if (barcodeScannerOptions != null) {
            val BarcodeScannerOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner\$Options")
            if (!BarcodeScannerOptionsClazz.isInstance(value)) return
            barcodeScannerOptions = value
        }
    }


    internal var faceDetectionOptions: Any? = null
    fun setFaceDetectionOptions(value: Any) {
        if (!isFaceDetectionSupported) return
        if (faceDetectionOptions != null) {
            val FaceDetectionOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection\$Options")
            if (!FaceDetectionOptionsClazz.isInstance(value)) return
            faceDetectionOptions = value
        }
    }

    internal var imageLabelingOptions: Any? = null
    fun setImageLabelingOptions(value: Any) {
        if (!isImageLabelingSupported) return
        if (imageLabelingOptions != null) {
            val ImageLabelingOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling\$Options")
            if (!ImageLabelingOptionsClazz.isInstance(value)) return
            imageLabelingOptions = value
        }
    }

    internal var objectDetectionOptions: Any? = null
    fun setObjectDetectionOptions(value: Any) {
        if (!isObjectDetectionSupported) return
        if (objectDetectionOptions != null) {
            val ObjectDetectionOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection\$Options")
            if (!ObjectDetectionOptionsClazz.isInstance(value)) return
            objectDetectionOptions = value
        }
    }

    internal var selfieSegmentationOptions: Any? = null
    fun setSelfieSegmentationOptions(value: Any) {
        if (!isSelfieSegmentationSupported) return
        if (selfieSegmentationOptions != null) {
            val SelfieSegmentationOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation\$Options")
            if (!SelfieSegmentationOptionsClazz.isInstance(value)) return
            selfieSegmentationOptions = value
        }
    }

    internal fun initOptions() {
        if (isBarcodeScanningSupported) {
            Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner")
            val BarcodeScannerOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner\$Options")
            barcodeScannerOptions = BarcodeScannerOptionsClazz.newInstance()
        }


        if (isFaceDetectionSupported) {
            val FaceDetectionOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection\$Options")
            faceDetectionOptions = FaceDetectionOptionsClazz.newInstance()
        }

        if (isImageLabelingSupported) {
            val ImageLabelingOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling\$Options")
            imageLabelingOptions = ImageLabelingOptionsClazz.newInstance()
        }

        if (isPoseDetectionSupported) {
            // noop
        }


        if (isObjectDetectionSupported) {
            val ObjectDetectionOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection\$Options")
            objectDetectionOptions = ObjectDetectionOptionsClazz.newInstance()
        }

        if (isSelfieSegmentationSupported) {
            val SelfieSegmentationOptionsClazz =
                Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation\$Options")
            selfieSegmentationOptions = SelfieSegmentationOptionsClazz.newInstance()
        }

        if (isTextRecognitionSupported) {
            // noop
        }
    }


    /** Device orientation in degrees 0-359 */
    var currentOrientation: Int = OrientationEventListener.ORIENTATION_UNKNOWN

    abstract fun orientationUpdated();

    internal val VIDEO_RECORDER_PERMISSIONS_REQUEST = 868
    internal val VIDEO_RECORDER_PERMISSIONS =
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)

    internal var mTimer: Timer? = null
    internal var mTimerTask: TimerTask? = null

    internal var isGettingAudioLevels = false
    private var mEMA = 0.0

    val duration: Long
        get() {
            return mDuration
        }

    internal var recorder: MediaRecorder = MediaRecorder()

    internal var listener: CameraEventListener? = null

    private val orientationEventListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation > -1) {
                val newOrientation = when (orientation) {
                    in 45 until 135 -> 270
                    in 135 until 225 -> 180
                    in 225 until 315 -> 90
                    else -> 0
                }

                if (newOrientation != currentOrientation) {
                    currentOrientation = newOrientation
                    orientationUpdated()
                }
            }
        }
    }

    init {
        orientationEventListener.enable()
    }

    @Synchronized
    @Throws(Throwable::class)
    protected fun finalize() {
        orientationEventListener.disable()
    }


    private var timerLock: Any = Any()

    @Volatile
    internal var mDuration = 0L
    internal fun startDurationTimer() {
        mTimer = Timer()
        mTimerTask = object : TimerTask() {
            override fun run() {
                synchronized(timerLock) {
                    mDuration += 1
                }
            }
        }
        mTimer?.schedule(mTimerTask, 0, 1000)
    }

    internal fun stopDurationTimer() {
        mTimerTask?.cancel()
        mTimer?.cancel()
        mDuration = 0
    }


    internal fun initListener(instance: MediaRecorder? = null) {
        if (isAudioLevelsEnabled) {
            if (!hasPermission()) {
                return
            }
            deInitListener()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile("/dev/null")
            try {
                recorder.prepare()
                recorder.start()
                isGettingAudioLevels = true
                mEMA = 0.0
            } catch (e: IOException) {
            } catch (e: Exception) {
            }

        }
    }

    internal fun deInitListener() {
        if (isAudioLevelsEnabled && isGettingAudioLevels) {
            try {
                recorder.stop()
                recorder.reset()
                isGettingAudioLevels = false
            } catch (e: Exception) {
            }

        }
    }

    internal fun getCamcorderProfile(quality: Quality): CamcorderProfile {
        var profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
        when (quality) {
            Quality.MAX_480P -> profile =
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
                    CamcorderProfile.get(CamcorderProfile.QUALITY_480P)
                } else {
                    getCamcorderProfile(Quality.QVGA)
                }
            Quality.MAX_720P -> profile =
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
                    CamcorderProfile.get(CamcorderProfile.QUALITY_720P)
                } else {
                    getCamcorderProfile(Quality.MAX_480P)
                }
            Quality.MAX_1080P -> profile =
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
                    CamcorderProfile.get(CamcorderProfile.QUALITY_1080P)
                } else {
                    getCamcorderProfile(Quality.MAX_720P)
                }
            Quality.MAX_2160P -> profile = try {
                CamcorderProfile.get(CamcorderProfile.QUALITY_2160P)
            } catch (e: Exception) {
                getCamcorderProfile(Quality.HIGHEST)
            }

            Quality.HIGHEST -> profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
            Quality.LOWEST -> profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW)
            Quality.QVGA -> profile =
                if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA)) {
                    CamcorderProfile.get(CamcorderProfile.QUALITY_QVGA)
                } else {
                    getCamcorderProfile(Quality.LOWEST)
                }
        }
        return profile
    }

    internal val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        public override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy:MM:dd", Locale.US)
        }
    }
    internal val TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        public override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("HH:mm:ss", Locale.US)
        }
    }
    internal val DATETIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        public override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        }
    }

    internal fun convertToExifDateTime(timestamp: Long): String {
        return DATETIME_FORMAT.get()!!.format(Date(timestamp))
    }

    @Throws(ParseException::class)
    internal fun convertFromExifDateTime(dateTime: String): Date {
        return DATETIME_FORMAT.get()!!.parse(dateTime)!!
    }

    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            true
        } else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            868
        )
    }

    fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.CAMERA),
            VIDEO_RECORDER_PERMISSIONS_REQUEST
        )
    }

    fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            context as Activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            VIDEO_RECORDER_PERMISSIONS_REQUEST
        )
    }

    fun requestPermission() {
        ActivityCompat.requestPermissions(
            context as Activity,
            VIDEO_RECORDER_PERMISSIONS,
            VIDEO_RECORDER_PERMISSIONS_REQUEST
        )
    }

    fun hasPermission(): Boolean {
        return hasCameraPermission() && hasAudioPermission()
    }

    fun hasCameraPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            true
        } else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT < 23) {
            true
        } else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        internal val EMA_FILTER = 0.6
        internal var isBarcodeScanningSupported = false
        internal var isFaceDetectionSupported = false
        internal var isImageLabelingSupported = false
        internal var isObjectDetectionSupported = false
        internal var isPoseDetectionSupported = false
        internal var isTextRecognitionSupported = false
        internal var isSelfieSegmentationSupported = false
        internal val isMLSupported: Boolean
            get() {
                return isFaceDetectionSupported || isTextRecognitionSupported || isBarcodeScanningSupported ||
                        isPoseDetectionSupported || isImageLabelingSupported || isObjectDetectionSupported
            }

        internal fun detectSupport() {
            isBarcodeScanningSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.barcodescanning.BarcodeScanner")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

            isFaceDetectionSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.facedetection.FaceDetection")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

            isImageLabelingSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.imagelabeling.ImageLabeling")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

            isPoseDetectionSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.posedetection.PoseDetection")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

            isObjectDetectionSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.objectdetection.ObjectDetection")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

            isSelfieSegmentationSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.selfiesegmentation.SelfieSegmentation")
                true
            } catch (e: ClassNotFoundException) {
                false
            }


            isTextRecognitionSupported = try {
                Class.forName("io.github.triniwiz.fancycamera.textrecognition.TextRecognition")
                true
            } catch (e: ClassNotFoundException) {
                false
            }

        }

        init {
            detectSupport()
        }
    }
}
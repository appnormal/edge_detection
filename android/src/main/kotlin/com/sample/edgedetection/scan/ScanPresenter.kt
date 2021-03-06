package com.sample.edgedetection.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.hardware.Camera.ShutterCallback
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import com.sample.edgedetection.R
import com.sample.edgedetection.REQUEST_CODE
import com.sample.edgedetection.SourceManager
import com.sample.edgedetection.crop.CropActivity
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.processPicture
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ScanPresenter constructor(private val context: Context, private val iView: IScanView.Proxy, private val bundleExtras: Bundle?)
    : SurfaceHolder.Callback, Camera.PictureCallback, Camera.PreviewCallback {
    private val TAG: String = "ScanPresenter"
    private var mCamera: Camera? = null
    private val mSurfaceHolder: SurfaceHolder = iView.getSurfaceView().holder
    private val executor: ExecutorService
    private val proxySchedule: Scheduler
    private var busy: Boolean = false
    private var soundSilence: MediaPlayer = MediaPlayer()

    private val disposables = CompositeDisposable()
    private val frameProcessor: PublishSubject<Frame> = PublishSubject.create()
    private val cornerDetector: PublishSubject<Mat> = PublishSubject.create()

    init {
        mSurfaceHolder.addCallback(this)
        executor = Executors.newSingleThreadExecutor()
        proxySchedule = Schedulers.from(executor)
        soundSilence = MediaPlayer.create(this.context, R.raw.silence);
    }

    fun start() {
        mCamera?.startPreview() ?: Log.i(TAG, "camera null")
    }

    fun stop() {
        mCamera?.stopPreview() ?: Log.i(TAG, "camera null")
    }

    fun shut() {
        busy = true
        Log.i(TAG, "try to focus")
        mCamera?.autoFocus { b, _ ->
            Log.i(TAG, "focus result: " + b)
            mCamera?.takePicture(ShutterCallback {
                soundSilence.start()
            }, null, this)
            mCamera?.enableShutterSound(false)
        }
    }

    fun updateCamera() {
        if (null == mCamera) {
            return
        }
        mCamera?.stopPreview()
        try {
            mCamera?.setPreviewDisplay(mSurfaceHolder)
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }
        mCamera?.setPreviewCallback(this)
        mCamera?.startPreview()
    }

    fun initCamera() {
        try {
            mCamera = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
        } catch (e: RuntimeException) {
            e.stackTrace
            Toast.makeText(context, "cannot open camera, please grant camera", Toast.LENGTH_SHORT).show()
            return
        }


        val param = mCamera?.parameters
        val size = getMaxResolution()
        param?.setPreviewSize(size?.width ?: 1920, size?.height ?: 1080)
        val display = iView.getDisplay()
        val point = Point()
        display.getRealSize(point)
        val displayWidth = minOf(point.x, point.y)
        val displayHeight = maxOf(point.x, point.y)
        val displayRatio = displayWidth.div(displayHeight.toFloat())
        val previewRatio = size?.height?.toFloat()?.div(size.width.toFloat()) ?: displayRatio
        if (displayRatio > previewRatio) {
            val surfaceParams = iView.getSurfaceView().layoutParams
            surfaceParams.height = (displayHeight / displayRatio * previewRatio).toInt()
            iView.getSurfaceView().layoutParams = surfaceParams
        }

        val supportPicSize = mCamera?.parameters?.supportedPictureSizes
        supportPicSize?.sortByDescending { it.width.times(it.height) }
        var pictureSize = supportPicSize?.find { it.height.toFloat().div(it.width.toFloat()) - previewRatio < 0.01 }

        if (null == pictureSize) {
            pictureSize = supportPicSize?.get(0)
        }

        if (null == pictureSize) {
            Log.e(TAG, "can not get picture size")
        } else {
            param?.setPictureSize(pictureSize.width, pictureSize.height)
        }
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_AUTOFOCUS)) {
            param?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            Log.d(TAG, "enabling autofocus")
        } else {
            Log.d(TAG, "autofocus not available")
        }
        param?.flashMode = Camera.Parameters.FLASH_MODE_AUTO

        mCamera?.parameters = param
        mCamera?.setDisplayOrientation(90)

        setupProcessor()
    }

    override fun surfaceCreated(p0: SurfaceHolder?) {
        initCamera()
    }

    override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) {
        updateCamera()
    }

    override fun surfaceDestroyed(p0: SurfaceHolder?) {
        synchronized(this) {
            mCamera?.stopPreview()
            mCamera?.setPreviewCallback(null)
            mCamera?.release()
            mCamera = null
        }
    }

    override fun onPictureTaken(p0: ByteArray?, p1: Camera?) {
        Log.i(TAG, "on picture taken")
        disposables.add(Observable.just(p0)
                .subscribeOn(proxySchedule)
                .subscribe {
                    val pictureSize = p1?.parameters?.pictureSize
                    Log.i(TAG, "picture size: " + pictureSize.toString())
                    val mat = Mat(Size(pictureSize?.width?.toDouble() ?: 1920.toDouble(),
                            pictureSize?.height?.toDouble() ?: 1080.toDouble()), CvType.CV_8U)
                    mat.put(0, 0, p0)
                    val pic = Imgcodecs.imdecode(mat, Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)
                    Core.rotate(pic, pic, Core.ROTATE_90_CLOCKWISE)
                    mat.release()
                    SourceManager.corners = processPicture(pic)
                    Imgproc.cvtColor(pic, pic, Imgproc.COLOR_RGB2BGRA)
                    SourceManager.pic = pic
                    val intent = Intent(context, CropActivity::class.java).apply {
                        bundleExtras?.let { putExtras(it) }
                    }
                    (context as Activity).startActivityForResult(intent, REQUEST_CODE)
                    busy = false
                })
    }

    private fun setupProcessor() {
        Log.i(TAG, "Setup RX")
        disposables.add(frameProcessor
                .toFlowable(BackpressureStrategy.DROP)
                .onBackpressureDrop()
                .observeOn(Schedulers.io(), false, 1)
                .subscribeOn(Schedulers.io())
                .subscribe({

                    Log.i(TAG, "Process frame!")

                    val parameters = it.p1.parameters
                    val width = parameters?.previewSize?.width
                    val height = parameters?.previewSize?.height
                    val yuv = YuvImage(it.p0, parameters?.previewFormat ?: 0, width ?: 320, height
                            ?: 480, null)
                    val out = ByteArrayOutputStream()
                    yuv.compressToJpeg(Rect(0, 0, width ?: 320, height ?: 480), 100, out)
                    val bytes = out.toByteArray()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    val img = Mat()
                    Utils.bitmapToMat(bitmap, img)
                    bitmap.recycle()
                    Core.rotate(img, img, Core.ROTATE_90_CLOCKWISE)
                    try {
                        out.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    cornerDetector.onNext(img)

                }, { throwable -> Log.e(TAG, throwable.message) }))

        disposables.add(
                cornerDetector
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .map {
                            Log.i(TAG, "Process image!")
                            CornerResult(processPicture(it))
                        }
                        .subscribe({
                            Log.i(TAG, "Update corners!")
                            updateCorners(it.corners)
                        }, {

                            Log.w(TAG, it.message)

                            (context as Activity).runOnUiThread {
                                iView.getPaperRect().onCornersNotDetected()
                            }
                        }))
    }

    override fun onPreviewFrame(p0: ByteArray?, p1: Camera?) {
        p1?.let { camera ->
            p0?.let { bytes -> frameProcessor.onNext(Frame(bytes, camera)) }
        }
    }

    private fun getMaxResolution(): Camera.Size? = mCamera?.parameters?.supportedPreviewSizes?.maxBy { it.width }


    private fun updateCorners(corners: Corners?) {
        if (corners != null) {
            iView.getPaperRect().onCornersDetected(corners)
        } else {
            iView.getPaperRect().onCornersNotDetected()
        }
    }

    fun dispose() {
        disposables.dispose()
    }
}

data class CornerResult(
        val corners: Corners?
)

data class Frame(
        val p0: ByteArray,
        val p1: Camera
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (!p0.contentEquals(other.p0)) return false
        if (p1 != other.p1) return false

        return true
    }

    override fun hashCode(): Int {
        var result = p0.contentHashCode()
        result = 31 * result + p1.hashCode()
        return result
    }
}
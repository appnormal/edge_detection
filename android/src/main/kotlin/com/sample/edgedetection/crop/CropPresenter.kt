package com.sample.edgedetection.crop

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.sample.edgedetection.SourceManager
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.TAG
import com.sample.edgedetection.processor.cropPicture
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream


const val IMAGES_DIR = "smart_scanner"

class CropPresenter(val context: Context, private val iCropView: ICropView.Proxy) {
    private val picture: Mat? = SourceManager.pic

    private val corners: Corners? = SourceManager.corners
    private var croppedPicture: Mat? = null
    private var croppedBitmap: Bitmap? = null

    private val disposables = CompositeDisposable()

    init {
        iCropView.getPaperRect().onCorners2Crop(corners, picture?.size())
        val bitmap = Bitmap.createBitmap(picture?.width() ?: 1080, picture?.height()
                ?: 1920, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(picture, bitmap, true)
        iCropView.getPaper().setImageBitmap(bitmap)
    }

    fun addImageToGallery(filePath: String, context: Context) {

        val values = ContentValues()

        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        values.put(MediaStore.MediaColumns.DATA, filePath)

        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun cropAndSave(callback: (path: String?) -> Unit) {
        if (picture == null) {
            Log.i(TAG, "picture null?")
            return
        }

        if (croppedBitmap != null) {
            Log.i(TAG, "already cropped")
            return
        }

        disposables.add(Observable.create<Mat> {
            it.onNext(cropPicture(picture, iCropView.getPaperRect().getCorners2Crop()))
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { pc ->
                    Log.i(TAG, "cropped picture: " + pc.toString())
                    croppedPicture = pc
                    croppedBitmap = Bitmap.createBitmap(pc.width(), pc.height(), Bitmap.Config.ARGB_8888)
                    Utils.matToBitmap(pc, croppedBitmap)
                    iCropView.getCroppedPaper().setImageBitmap(croppedBitmap)
                    iCropView.getPaper().visibility = View.GONE
                    iCropView.getPaperRect().visibility = View.GONE

                    callback(save())
                })

    }


    private fun save(): String? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Please grant write file permission and try again", Toast.LENGTH_SHORT).show()
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), IMAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val cropPic = croppedBitmap
            if (null != cropPic) {
                val file = File(dir, "crop_${SystemClock.currentThreadTimeMillis()}.jpeg")
                val outStream = FileOutputStream(file)
                cropPic.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                outStream.flush()
                outStream.close()

                Log.i(TAG, "CroppedBitmap Saved")

                return file.absolutePath

                //addImageToGallery(file.absolutePath, this.context) Commented as we don't want the images in the gallery.
                //Toast.makeText(context, "picture saved, path: ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        }
        return null
    }

    fun dispose(){
        disposables.dispose()
    }
}
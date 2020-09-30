package com.sample.edgedetection.scan

import android.Manifest.permission.CAMERA
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Display
import android.view.MenuItem
import android.view.SurfaceView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sample.edgedetection.*
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle
import kotlinx.android.synthetic.main.activity_scan.*
import org.opencv.android.OpenCVLoader

const val REQUEST_PERMISSIONS = 0

class ScanActivity : BaseActivity(), IScanView.Proxy {

    private lateinit var mPresenter: ScanPresenter

    override fun provideContentViewId(): Int = R.layout.activity_scan

    override fun initPresenter() {
        val strings = (intent?.extras?.getSerializable(OPTION_STRINGS)
                ?: hashMapOf<String, String>()) as HashMap<*, *>

        strings["scanning"]?.let { title = it as String }

        intent?.extras?.getLong(OPTION_COLOR)?.let {
            supportActionBar?.setBackgroundDrawable(ColorDrawable(it.toInt()))
            window.statusBarColor = it.toInt()
        }

        mPresenter = ScanPresenter(this, this, intent?.extras)
    }

    override fun prepare() {
        if (!OpenCVLoader.initDebug()) {
            finish()
        }

        val noCameraPermission = ContextCompat.checkSelfPermission(this, CAMERA) != PackageManager.PERMISSION_GRANTED
        val noStoragePermission = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED

        if (noCameraPermission && noStoragePermission) {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA, WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSIONS)
        } else if (noCameraPermission) {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA), REQUEST_PERMISSIONS)
        } else if (noStoragePermission) {
            ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSIONS)
        }

        shut.setOnClickListener {
            mPresenter.shut()
        }
    }


    override fun onStart() {
        super.onStart()
        mPresenter.start()
    }

    override fun onStop() {
        super.onStop()
        mPresenter.stop()
    }

    override fun exit() {
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val cameraGranted = grantResults[permissions.indexOf(CAMERA)] == PackageManager.PERMISSION_GRANTED

        if (requestCode == REQUEST_PERMISSIONS && cameraGranted) {
            showMessage(R.string.camera_grant)
            mPresenter.initCamera()
            mPresenter.updateCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun getDisplay(): Display = windowManager.defaultDisplay

    override fun getSurfaceView(): SurfaceView = surface

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (null != data && null != data.extras) {
                    val path = data.extras!!.getString(SCANNED_RESULT)
                    setResult(Activity.RESULT_OK, Intent().putExtra(SCANNED_RESULT, path))
                    finish()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
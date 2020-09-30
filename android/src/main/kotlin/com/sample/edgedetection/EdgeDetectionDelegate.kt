package com.sample.edgedetection

import android.app.Activity
import android.content.Intent
import com.sample.edgedetection.scan.ScanActivity
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

const val OPTION_STRINGS = "strings"
const val OPTION_COLOR = "color"

class EdgeDetectionDelegate(private var activity: Activity) : PluginRegistry.ActivityResultListener {

    private var result: MethodChannel.Result? = null
    private var methodCall: MethodCall? = null


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {

        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (null != data && null != data.extras) {
                    val filePath = data.extras!!.getString(SCANNED_RESULT)
                    finishWithSuccess(filePath)
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                finishWithSuccess(null)
            }
            return true
        }

        return false
    }

    fun openCameraActivity(call: MethodCall, result: MethodChannel.Result) {

        if (!setPendingMethodCallAndResult(call, result)) {
            finishWithAlreadyActiveError()
            return
        }
        val strings = call.argument<HashMap<String, String>>(OPTION_STRINGS) ?: hashMapOf()
        val color = call.argument<Long>(OPTION_COLOR) ?: 0L

        val intent = Intent(Intent(activity.applicationContext, ScanActivity::class.java)).apply {
            putExtra(OPTION_STRINGS, strings)
            putExtra(OPTION_COLOR, color)
        }

        activity.startActivityForResult(intent, REQUEST_CODE)
    }

    private fun setPendingMethodCallAndResult(methodCall: MethodCall, result: MethodChannel.Result): Boolean {
        if (this.result != null) {
            return false
        }

        this.methodCall = methodCall
        this.result = result
        return true
    }

    private fun finishWithAlreadyActiveError() {
        result?.error("already_active", "Edge detection is already active", null)
        clearMethodCallAndResult()
    }

    private fun finishWithSuccess(imagePath: String?) {
        result?.success(imagePath)
        clearMethodCallAndResult()
    }

    private fun clearMethodCallAndResult() {
        methodCall = null
        result = null
    }

}
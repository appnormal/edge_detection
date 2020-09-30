package com.sample.edgedetection.crop

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import com.sample.edgedetection.OPTION_COLOR
import com.sample.edgedetection.OPTION_STRINGS
import com.sample.edgedetection.R
import com.sample.edgedetection.SCANNED_RESULT
import com.sample.edgedetection.base.BaseActivity
import com.sample.edgedetection.view.PaperRectangle
import kotlinx.android.synthetic.main.activity_crop.*


class CropActivity : BaseActivity(), ICropView.Proxy {


    private lateinit var mPresenter: CropPresenter

    override fun prepare() {
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop


    override fun initPresenter() {

        val strings = (intent?.extras?.getSerializable(OPTION_STRINGS)
                ?: hashMapOf<String, String>()) as HashMap<*, *>

        strings["cropping"]?.let { title = it as String }

        intent?.extras?.getLong(OPTION_COLOR)?.let {
            supportActionBar?.setBackgroundDrawable(ColorDrawable(it.toInt()))
            window.statusBarColor = it.toInt()
        }


        mPresenter = CropPresenter(this, this)
    }

    override fun getPaper(): ImageView = paper

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun getCroppedPaper(): ImageView = picture_cropped

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.crop_activity_menu, menu)

        menu.findItem(R.id.action_label)?.let {
            it.title = applicationContext.getString(R.string.done)
            it.icon = applicationContext.getDrawable(R.drawable.ic_done)
        }

        val strings = (intent?.extras?.getSerializable(OPTION_STRINGS)
                ?: hashMapOf<String, String>()) as HashMap<*, *>
        strings["done"]?.let {
            menu.findItem(R.id.action_label).setTitle(it as String)
        }

        return super.onCreateOptionsMenu(menu)
    }


    // handle button activities
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }

        if (item.itemId == R.id.action_label) {
            Log.e(classTag, "Saved touched!")

            mPresenter.cropAndSave {
                Log.e(classTag, "Saved touched! $it")

                setResult(Activity.RESULT_OK, Intent().putExtra(SCANNED_RESULT, it))
                mPresenter.dispose()
                finish()
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
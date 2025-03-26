package com.plusinfosys.videotrimmerkotlin.Utils

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.plusinfosys.videotrimmerkotlin.R

object Utils {

    fun View.visible() {
        visibility = View.VISIBLE
    }

    fun View.inVisible() {
        visibility = View.INVISIBLE
    }

    fun View.gone() {
        visibility = View.GONE
    }

    fun isTablet(context: Context): Boolean {
        val metrics = context.resources.displayMetrics
        val dpWidth = metrics.widthPixels / metrics.density

        return dpWidth >= 600  // 600dp is a common threshold for tablets
    }

    fun getVideoDurationInSeconds(videoPath: String): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            (durationMs / 1000).toDouble() // Convert milliseconds to seconds
        } catch (e: Exception) {
            e.printStackTrace()
            0.0
        } finally {
            retriever.release()
        }
    }

    fun isReadStoragePermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.READ_MEDIA_IMAGES
            return ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun showErrorDialog(
        context: Context,
        message: String,
        isInvokeSuccess: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val dialog = Dialog(context)
        dialog.window?.decorView?.background = ColorDrawable(Color.TRANSPARENT)
        dialog.setContentView(R.layout.custom_validation_dialog)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dialog.window!!.attributes)
        lp.gravity = android.view.Gravity.CENTER

        val txtMessage = dialog.findViewById<TextView>(R.id.txtValidationMessage)
        txtMessage.text = message
        val txtCancel: TextView = dialog.findViewById(R.id.txtOk)

        txtCancel.setOnClickListener {
            dialog.dismiss()
            if (isInvokeSuccess) {
                onSuccess.invoke()
            }
        }

        dialog.window?.attributes = lp
        dialog.show()
    }

}
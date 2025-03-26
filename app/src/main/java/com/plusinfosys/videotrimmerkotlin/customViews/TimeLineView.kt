package com.plusinfosys.videotrimmerkotlin.customViews


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.AttributeSet
import android.util.LongSparseArray
import android.view.View
import com.plusinfosys.videotrimmerkotlin.R
import com.plusinfosys.videotrimmerkotlin.Utils.BackgroundExecutor

open class TimeLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var mVideoUri: Uri? = null
    private var mHeightView: Int = 0
    private var mBitmapList: LongSparseArray<Bitmap>? = null

    init {
        init()
    }

    private fun init() {
        mHeightView =
            context.resources.getDimensionPixelOffset(R.dimen.frames_height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minW = paddingLeft + paddingRight + suggestedMinimumWidth
        val w = resolveSizeAndState(minW, widthMeasureSpec, 1)
        val minH = paddingBottom + paddingTop + mHeightView
        val h = resolveSizeAndState(minH, heightMeasureSpec, 1)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w != oldW) {
            getBitmap()
        }
    }

    private fun getBitmap() {
        BackgroundExecutor.execute(object : BackgroundExecutor.Task("", 0L, "") {
            override fun execute() {
                try {
                    val thumbnailList = LongSparseArray<Bitmap>()

                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(context, mVideoUri)

                    // Retrieve video duration in microseconds.
                    // NOTE: The metadata returns duration in milliseconds,
                    // so multiply by 1000 to convert to microseconds (which getFrameAtTime expects).
                    val durationMs =
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!
                            .toInt()
                    val videoLengthInMicroseconds = durationMs * 1300L

                    // Dynamically determine the number of thumbnails based on video duration
                    val numThumbs = when {
                        durationMs < 5000 -> 5   // Less than 5 sec → 5 thumbnails
                        durationMs > 15000 -> 20 // More than 15 sec → 20 thumbnails
                        else -> durationMs / 1000 // Otherwise, 1 thumbnail per second
                    }

                    // Define thumbnail properties
                    val thumbWidth = if(numThumbs == 5) 110 else 40
                    val thumbHeight = mHeightView

                    // Calculate the interval. Using (numThumbs - 1) ensures first frame is at 0 and last at video end.
                    val interval =
                        if (numThumbs > 1) videoLengthInMicroseconds / (numThumbs - 1) else videoLengthInMicroseconds

                    // Retrieve and scale bitmaps
                    for (i in 0 until numThumbs) {
                        var bitmap = mediaMetadataRetriever.getFrameAtTime(
                            i * interval,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                        // Ensure bitmap is not null and scale it
                        try {
                            bitmap = Bitmap.createScaledBitmap(
                                bitmap!!,
                                thumbWidth,
                                thumbHeight,
                                false
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        thumbnailList.put(i.toLong(), bitmap)
                    }

                    mediaMetadataRetriever.release()
                    returnBitmaps(thumbnailList)
                } catch (e: Throwable) {
                    Thread.getDefaultUncaughtExceptionHandler()
                        ?.uncaughtException(Thread.currentThread(), e)
                }
            }
        })
    }

    private fun returnBitmaps(thumbnailList: LongSparseArray<Bitmap>?) {
        UiThreadExecutor.runTask(
            "", {
                mBitmapList = thumbnailList
                invalidate()
            },
            0L
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mBitmapList != null) {
            canvas.save()
            var x = 0

            for (i in 0 until mBitmapList!!.size()) {
                val bitmap = mBitmapList!![i.toLong()]

                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, x.toFloat(), 0f, null)
                    x += bitmap.width
                }
            }
        }
    }

    fun setVideo(data: Uri) {
        mVideoUri = data
        getBitmap()
    }
}
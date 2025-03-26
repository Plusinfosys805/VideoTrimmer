package com.plusinfosys.videotrimmerkotlin.customViews

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.plusinfosys.videotrimmerkotlin.customViews.RangeSeekBarView.Thumb.Companion.initThumbs
import com.plusinfosys.videotrimmerkotlin.customViews.RangeSeekBarView.Thumb.Companion.initThumbsGrey
import com.plusinfosys.videotrimmerkotlin.R
import com.plusinfosys.videotrimmerkotlin.Utils.Utils.isTablet
import com.plusinfosys.videotrimmerkotlin.interfaces.OnRangeSeekBarListener
import kotlin.math.abs
import kotlin.math.max

@Suppress("LeakingThis")
open class RangeSeekBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    enum class ThumbType(var index: Int) {
        LEFT(0),
        RIGHT(1)
    }

    private val defaultTopColor = "#6C3DCC"
    private val bottomFrameColor = "#242625"
    private var isTouchEnabled = true
    private val thumbTouchExtraMultiplier = initThumbTouchExtraMultiplier()
    var thumbs: List<Thumb> = listOf()
    private var thumbsGrey: List<Thumb> = listOf()
    private val listeners: MutableList<OnRangeSeekBarListener> = ArrayList()
    private val thumbWidth = initThumbWidth(getContext())
    private var viewWidth = 0
    private var pixelRangeMin = 0f
    private var pixelRangeMax = 0f
    private val scaleRangeMax = 100f
    private var firstRun = true
    private val shadowPaint = Paint()
    private val strokePaint = Paint()
    private val edgePaint = Paint()
    private var currentThumb = ThumbType.LEFT.index
    private val strokeGray = Paint()
    private val paintTop = Paint()
    private val paintGray = Paint()
    private var displayWidth = 0
    private var minThumbDistance = 200f
    var isYellowColor = false

    init {
        thumbsGrey = initThumbsGrey(resources)
        thumbs = initThumbs(resources)
        isFocusable = true
        isFocusableInTouchMode = true
        shadowPaint.isAntiAlias = true
        shadowPaint.color = initShadowColor()
        strokePaint.isAntiAlias = true
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
        strokePaint.color = Color.TRANSPARENT
        edgePaint.isAntiAlias = true
        edgePaint.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
        edgePaint.color = Color.RED
        strokeGray.style = Paint.Style.STROKE
        strokeGray.color = Color.parseColor(bottomFrameColor)
        strokeGray.isAntiAlias = true
        strokeGray.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
        paintTop.style = Paint.Style.FILL
        paintTop.color = Color.TRANSPARENT
        paintTop.isAntiAlias = true
        paintTop.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
        paintGray.style = Paint.Style.FILL
        paintGray.color = Color.parseColor(bottomFrameColor)
        paintGray.isAntiAlias = true
        paintGray.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
    }

    fun changeColor() {
        isYellowColor = if (isYellowColor) {
            strokePaint.color = Color.TRANSPARENT
            paintTop.color = Color.TRANSPARENT
            false
        } else {
            strokePaint.color = Color.parseColor(defaultTopColor)
            paintTop.color = Color.parseColor(defaultTopColor)
            true
        }
        invalidate()
    }

    // set top trim frame color
    fun setTopFrameColor(color: String) {
        if (color.isNotEmpty()) {
            strokePaint.color = Color.parseColor(color)
            paintTop.color = Color.parseColor(color)
        } else {
            strokePaint.color = Color.parseColor(defaultTopColor)
            paintTop.color = Color.parseColor(defaultTopColor)
            isYellowColor = true
        }

        invalidate()
    }

    //set below frame color
    fun setBelowFrameColor(color: String) {
        if (color.isNotEmpty()) {
            strokeGray.color = Color.parseColor(color)
            paintGray.color = Color.parseColor(color)
        } else {
            strokeGray.color = Color.TRANSPARENT
            paintGray.color = Color.TRANSPARENT
            isYellowColor = false
        }
        invalidate()
    }

    @ColorInt
    open fun initShadowColor(): Int {
        return -0x80000000
    }

    open fun initThumbTouchExtraMultiplier(): Float {
        return 1.0f
    }

    open fun initThumbWidth(context: Context): Int {
        return max(
            1.0,
            Math.round(
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    22f,
                    context.resources.displayMetrics
                )
            ).toDouble()
        ).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        displayWidth = context.resources.displayMetrics.widthPixels
        viewWidth = measuredWidth
        pixelRangeMin = 0f
        pixelRangeMax = (viewWidth - thumbWidth).toFloat()
        if (firstRun) {
            for (index in thumbsGrey.indices) {
                val thumb = thumbsGrey[index]
                thumb.value = scaleRangeMax * index
                thumb.pos = pixelRangeMax * index
            }
            for (index in thumbs.indices) {
                val thumb = thumbs[index]
                thumb.value = scaleRangeMax * index
                thumb.pos = pixelRangeMax * index
            }
            onCreate(this, currentThumb, getThumbValue(currentThumb))
            firstRun = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (thumbs.isEmpty()) {
            return
        }
        //FOR DISABLE grey thumbs
        if (thumbsGrey.isEmpty()) {
            return
        }

        //for grey rectangle
        canvas.drawRect(
            thumbsGrey[ThumbType.LEFT.index].pos + 20,
            0f,
            thumbsGrey[ThumbType.RIGHT.index].pos - paddingRight + 5,
            height.toFloat(),
            strokeGray
        )

        //for yellow
        for (thumb in thumbs) {
            if (thumb.index == ThumbType.LEFT.index) thumb.pos else thumb.pos - thumbWidth
            if (thumb.index == ThumbType.LEFT.index) {
                val x = thumb.pos + paddingLeft
                if (x > pixelRangeMin) {
                    canvas.drawRect(thumbWidth.toFloat(), 0f, x + 13, height.toFloat(), shadowPaint)
                }
            } else {
                val x = thumb.pos - paddingRight
                if (x < pixelRangeMax) {
                    canvas.drawRect(
                        x,
                        0f,
                        (viewWidth - thumbWidth).toFloat(),
                        height.toFloat(),
                        shadowPaint
                    )
                }
            }
        }

        //for yellow rectangle
        canvas.drawRect(
            thumbs[ThumbType.LEFT.index].pos + 20,
            0f,
            thumbs[ThumbType.RIGHT.index].pos - paddingRight + 5,
            height.toFloat(),
            strokePaint
        )


        //gray disable frame round side corners
        if (thumbsGrey.isNotEmpty()) {
            for (th in thumbsGrey) {
                if (th.index == 0 || th.index == 1) {
                    if (th.bitmap != null) {
                        if (isTablet(context)) {
                            val barWidth = context.resources.displayMetrics.density * 10

                            if (th.index == 0) {
                                canvas.drawRoundRect(
                                    th.pos,
                                    0f,
                                    th.pos + thumbWidth + barWidth,
                                    height.toFloat(),
                                    15f,
                                    15f,
                                    paintGray
                                )
                            } else {
                                canvas.drawRoundRect(
                                    th.pos - barWidth,
                                    0f,
                                    th.pos + thumbWidth - 3,
                                    height.toFloat(),
                                    15f,
                                    15f,
                                    paintGray
                                )
                            }
                        } else {
                            if (th.index == 0) {
                                canvas.drawRoundRect(
                                    th.pos,
                                    0f,
                                    th.pos + thumbWidth,
                                    height.toFloat(),
                                    15f,
                                    15f,
                                    paintGray
                                )
                            } else {
                                canvas.drawRoundRect(
                                    th.pos,
                                    0f,
                                    th.pos + thumbWidth - 3,
                                    height.toFloat(),
                                    15f,
                                    15f,
                                    paintGray
                                )
                            }
                        }
                    }
                }
            }
        }
        val resImageLeft = R.drawable.ic_arrow_left
        val drawableLeft = ContextCompat.getDrawable(context, resImageLeft)
        var arrowLeft: Bitmap? = drawableLeft?.let { Thumb.drawableToBitmap(it, context) }
        val resImageRight = R.drawable.ic_arrow_right
        val drawableRight = ContextCompat.getDrawable(context, resImageRight)
        var arrowRight: Bitmap? = drawableRight?.let { Thumb.drawableToBitmap(it, context) }
        if (isTablet(context)) {
            val scaleFactor = context.resources.displayMetrics.density * 0.7f// Example scale factor
            scaleDrawableWithGlide(context, resImageRight, scaleFactor) { scaledArrowRight ->
                arrowRight =
                    scaledArrowRight ?: drawableRight?.let { Thumb.drawableToBitmap(it, context) }
            }

            scaleDrawableWithGlide(context, resImageLeft, scaleFactor) { scaledArrowRight ->
                arrowLeft =
                    scaledArrowRight ?: drawableRight?.let { Thumb.drawableToBitmap(it, context) }
            }
        } else {
            arrowLeft = drawableLeft?.let { Thumb.drawableToBitmap(it, context) }
            arrowRight = drawableRight?.let { Thumb.drawableToBitmap(it, context) }
        }


        //yellow frame round side corners
        if (thumbs.isNotEmpty()) {
            for (th in thumbs) {
                if (th.index == 0 || th.index == 1) {
                    if (th.bitmap != null) {
                        if (isTablet(context)) {
                            val barWidth = context.resources.displayMetrics.density * 10
                            if (th.index == 0) {
                                canvas.drawRoundRect(
                                    th.pos,
                                    0f,
                                    th.pos + thumbWidth + barWidth,
                                    height.toFloat(),
                                    12f,
                                    12f,
                                    paintTop
                                )
                                if (arrowLeft != null) {

                                    canvas.drawBitmap(
                                        arrowLeft!!,
                                        th.pos + context.resources.displayMetrics.density * 6 + 5,
                                        (height.toFloat() / 2) - (arrowLeft!!.height / 2),
                                        null
                                    )
                                }
                            } else {
                                canvas.drawRoundRect(
                                    th.pos - barWidth,
                                    0f,
                                    th.pos + thumbWidth - 3,
                                    height.toFloat(),
                                    12f,
                                    12f,
                                    paintTop
                                )
                                if (arrowRight != null) {
                                    canvas.drawBitmap(
                                        arrowRight!!,
                                        th.pos - thumbWidth + 25,
                                        (height.toFloat() / 2) - (arrowLeft!!.height / 2),
                                        null
                                    )
                                }
                            }
                        } else {
                            if (th.index == 0) {
                                canvas.drawRoundRect(
                                    th.pos,
                                    0f,
                                    th.pos + thumbWidth,
                                    height.toFloat(),
                                    12f,
                                    12f,
                                    paintTop
                                )
                                if (arrowLeft != null) {
                                    canvas.drawBitmap(
                                        arrowLeft!!,
                                        th.pos + context.resources.displayMetrics.density * 6,
                                        context.resources.displayMetrics.density * 18,
                                        null
                                    )
                                }
                            } else {
                                canvas.drawRoundRect(
                                    th.pos,
                                    0f,
                                    th.pos + thumbWidth - 3,
                                    height.toFloat(),
                                    12f,
                                    12f,
                                    paintTop
                                )
                                if (arrowRight != null) {
                                    canvas.drawBitmap(
                                        arrowRight!!,
                                        th.pos + context.resources.displayMetrics.density * 5,
                                        context.resources.displayMetrics.density * 18,
                                        null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun setTouchEnabled(touchEnabled: Boolean) {
        isTouchEnabled = touchEnabled
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val coordinate = ev.x
        val action = ev.action
        // Minimum distance between thumbs in pixels (adjust as needed)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchEnabled) {
                    // Identify which thumb is closest to the touch event
                    currentThumb = getClosestThumb(coordinate)
                    if (currentThumb == -1) {
                        return false
                    }

                    // Register the touch for the selected thumb
                    thumbs[currentThumb].lastTouchX = coordinate
                    thumbsGrey[currentThumb].lastTouchX = coordinate

                    // Notify that seeking has started
                    onSeekStart(this, currentThumb, thumbs[currentThumb].value)
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isTouchEnabled && currentThumb != -1) {
                    // Notify that seeking has stopped
                    onSeekStop(this, currentThumb, thumbs[currentThumb].value)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTouchEnabled && currentThumb != -1) {
                    val mThumb = thumbs[currentThumb]
                    val mThumb2 =
                        if (currentThumb == ThumbType.LEFT.index) thumbs[ThumbType.RIGHT.index] else thumbs[ThumbType.LEFT.index]

                    // Calculate the new thumb position
                    val dx = coordinate - mThumb.lastTouchX
                    val newX = mThumb.pos + dx

                    // Handle left thumb movement
                    if (currentThumb == ThumbType.LEFT.index) {
                        if (newX + thumbWidth + minThumbDistance > mThumb2.pos) {
                            // Enforce minimum distance from right thumb
                            mThumb.pos = mThumb2.pos - thumbWidth - minThumbDistance
                        } else if (newX <= pixelRangeMin) {
                            mThumb.pos = pixelRangeMin
                        } else {
                            mThumb.pos += dx
                        }
                    }
                    // Handle right thumb movement
                    else {
                        if (newX < mThumb2.pos + thumbWidth + minThumbDistance) {
                            // Enforce minimum distance from left thumb
                            mThumb.pos = mThumb2.pos + thumbWidth + minThumbDistance
                        } else if (newX >= pixelRangeMax) {
                            mThumb.pos = pixelRangeMax
                        } else {
                            mThumb.pos += dx
                        }
                    }

                    // Update the last touch position
                    mThumb.lastTouchX = coordinate

                    // Set the new position of the thumb
                    setThumbPos(currentThumb, mThumb.pos)
                    invalidate() // Redraw the view
                    return true
                }
            }
        }

        return false
    }

    private fun pixelToScale(index: Int, pixelValue: Float): Float {
        var scale = pixelValue * 100 / pixelRangeMax
        if (index == 0) {
            val pxThumb = scale * thumbWidth / 100
            scale += pxThumb * 100 / pixelRangeMax
        } else {
            val pxThumb = (100 - scale) * thumbWidth / 100
            scale -= pxThumb * 100 / pixelRangeMax
        }
        return scale
    }

    private fun scaleToPixel(index: Int, scaleValue: Float): Float {
        var px = scaleValue * pixelRangeMax / 100
        px = if (index == 0) {
            val pxThumb = scaleValue * thumbWidth / 100
            px - pxThumb
        } else {
            val pxThumb = (100 - scaleValue) * thumbWidth / 100
            px + pxThumb
        }
        return px
    }

    private fun calculateThumbValue(index: Int) {
        if (index < thumbs.size && thumbs.isNotEmpty()) {
            val thumb = thumbs[index]
            thumb.value = pixelToScale(index, thumb.pos)
            onSeek(this, index, thumb.value, thumb.pos)
        }
    }

    private fun calculateThumbPos(index: Int) {
        if (index < thumbs.size && thumbs.isNotEmpty()) {
            val thumb = thumbs[index]
            thumb.pos = scaleToPixel(index, thumb.value)
        }
    }

    private fun getThumbValue(index: Int): Float {
        return thumbs[index].value
    }

    fun setThumbValue(index: Int, value: Float) {
        thumbs[index].value = value
        calculateThumbPos(index)
        invalidate()
    }

    private fun setThumbPos(index: Int, pos: Float) {
        thumbs[index].pos = pos
        calculateThumbValue(index)
        invalidate()
    }

    private fun getClosestThumb(xPos: Float): Int {
        if (thumbs.isEmpty()) {
            return -1
        }
        var closest = -1
        var minDistanceFound = Float.MAX_VALUE
        val x = xPos - thumbWidth
        for (thumb in thumbs) {
            val thumbPos =
                if (thumb.index == ThumbType.LEFT.index) thumb.pos else thumb.pos - thumbWidth
            val xMin = thumbPos - thumbWidth * thumbTouchExtraMultiplier
            val xMax = thumbPos + thumbWidth * thumbTouchExtraMultiplier
            if (x in xMin..xMax) {
                val distance = abs((thumbPos - x).toDouble()).toFloat()
                if (distance < minDistanceFound) {
                    closest = thumb.index
                    minDistanceFound = distance
                }
            }
        }
        return closest
    }

    fun addOnRangeSeekBarListener(listener: OnRangeSeekBarListener) {
        listeners.add(listener)
    }

    private fun onCreate(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        for (listener in listeners) {
            listener.onCreate(rangeSeekBarView, index, value)
        }
    }

    private fun onSeek(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float, pos: Float) {
        for (listener in listeners) {
            listener.onSeek(rangeSeekBarView, index, value, pos)
        }
    }

    private fun onSeekStart(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        for (listener in listeners) {
            listener.onSeekStart(rangeSeekBarView, index, value)
        }
    }

    private fun onSeekStop(rangeSeekBarView: RangeSeekBarView, index: Int, value: Float) {
        for (listener in listeners) {
            listener.onSeekStop(rangeSeekBarView, index, value)

        }
    }

    class Thumb private constructor() {
        var index = 0
        var value = 0f
        var pos = 0f
        var bitmap: Bitmap? = null
            private set(bitmap) {
                field = bitmap
                widthBitmap = bitmap?.width ?: 0
                heightBitmap = bitmap?.height ?: 0
            }
        private var widthBitmap = 0
        private var heightBitmap = 0
        var lastTouchX = 0f

        companion object {
            fun initThumbsGrey(resources: Resources?): List<Thumb> {
                val thumbs: MutableList<Thumb> = ArrayList()
                for (i in 0..1) {
                    val thumb1 = Thumb()
                    thumb1.index = i
                    if (i == 0) {
                        val resImageLeft = R.drawable.seek_left_round
                        thumb1.bitmap = BitmapFactory.decodeResource(resources, resImageLeft)
                    } else {
                        val resImageRight = R.drawable.seek_right_round
                        thumb1.bitmap = BitmapFactory.decodeResource(resources, resImageRight)
                    }
                    thumbs.add(thumb1)
                }
                return thumbs
            }

            fun initThumbs(resources: Resources?): List<Thumb> {
                val thumbs: MutableList<Thumb> = ArrayList()
                for (i in 0..1) {
                    val thumb = Thumb()
                    thumb.index = i
                    if (i == 0) {
                        val resImageLeft = R.drawable.seek_left_round
                        thumb.bitmap = BitmapFactory.decodeResource(resources, resImageLeft)
                    } else {
                        val resImageRight = R.drawable.seek_right_round
                        thumb.bitmap = BitmapFactory.decodeResource(resources, resImageRight)
                    }
                    thumbs.add(thumb)
                }
                return thumbs
            }

            fun drawableToBitmap(drawable: Drawable, mContext: Context): Bitmap {
                if (drawable is BitmapDrawable && drawable.bitmap != null) {
                    val bitmap1 = drawable.bitmap
                    return Bitmap.createScaledBitmap(
                        bitmap1,
                        (mContext.resources.displayMetrics.density * 10).toInt(),
                        (mContext.resources.displayMetrics.density * 12).toInt(),
                        true
                    )
                }
                val bitmap: Bitmap =
                    if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    } else {
                        Bitmap.createBitmap(
                            drawable.intrinsicWidth,
                            drawable.intrinsicHeight,
                            Bitmap.Config.ARGB_8888
                        )
                    }
                return bitmap
            }
        }
    }

    private fun scaleDrawableWithGlide(
        context: Context,
        drawableId: Int,
        scaleFactor: Float,
        callback: (Bitmap?) -> Unit
    ) {
        val drawable = ContextCompat.getDrawable(context, drawableId)
        drawable?.let {
            val targetWidth = (it.intrinsicWidth * scaleFactor).toInt()
            val targetHeight = (it.intrinsicHeight * scaleFactor).toInt()

            Glide.with(context)
                .asBitmap()
                .load(drawableId)
                .apply(RequestOptions().override(targetWidth, targetHeight))
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        callback(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // Handle cleanup if needed
                    }
                })
        } ?: run {
            callback(null)
        }
    }
}





package com.plusinfosys.videotrimmerkotlin.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.plusinfosys.videotrimmerkotlin.R
import com.plusinfosys.videotrimmerkotlin.Utils.Constants.Companion.FRAME_BLUE_COLOR
import com.plusinfosys.videotrimmerkotlin.Utils.Constants.Companion.FRAME_YELLOW_COLOR
import com.plusinfosys.videotrimmerkotlin.Utils.TrimVideoUtils.Companion.startTrim
import com.plusinfosys.videotrimmerkotlin.Utils.Utils.getVideoDurationInSeconds
import com.plusinfosys.videotrimmerkotlin.Utils.Utils.gone
import com.plusinfosys.videotrimmerkotlin.Utils.Utils.isReadStoragePermissionGranted
import com.plusinfosys.videotrimmerkotlin.Utils.Utils.showErrorDialog
import com.plusinfosys.videotrimmerkotlin.Utils.Utils.visible
import com.plusinfosys.videotrimmerkotlin.customViews.RangeSeekBarView
import com.plusinfosys.videotrimmerkotlin.databinding.ActivityVideoPlayerBinding
import com.plusinfosys.videotrimmerkotlin.interfaces.OnRangeSeekBarListener
import com.plusinfosys.videotrimmerkotlin.interfaces.VideoTrimmingListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class VideoPlayerActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var exoPlayer: ExoPlayer
    private var videoPath = ""
    private var videoUri: Uri? = null
    private var isVideoPaused = false
    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0
    private val updateInterval = 100L
    private var videoDuration = 0.0
    private val minTimeFrame = 1000
    private var startPosition = 0
    private var endPosition = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestReadStoragePermission()
        setupPlayer()
        setTrimListener()
        setupListeners()
        rangeBarFrame()
    }

    private fun setupListeners() {
        binding.imgPlayPause.setOnClickListener(this@VideoPlayerActivity)
        binding.imgBack.setOnClickListener(this@VideoPlayerActivity)
        binding.txtSave.setOnClickListener(this@VideoPlayerActivity)
        binding.txtSelect.setOnClickListener(this@VideoPlayerActivity)
        binding.topSeekbarProcess.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val progressThumbsValues = Pair(
                        binding.rangeSeekBarView.thumbs[0].value.toInt(),
                        binding.rangeSeekBarView.thumbs[1].value.toInt()
                    )
                    if (isBetweenCutRange(
                            progress = progress,
                            maxProgress = binding.topSeekbarProcess.max,
                            thumbsValue = progressThumbsValues
                        )
                    ) {
                        showTimer(progress)
                    } else {
                        setProgressToMinOrMax(
                            progressThumbsValues = progressThumbsValues,
                            progress = progress
                        )
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                pauseVideo()
                binding.seekLineLinear.visible()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                binding.seekLineLinear.gone()
                exoPlayer.seekTo((binding.topSeekbarProcess.progress).toLong())
                progress = binding.topSeekbarProcess.progress
            }
        })
    }

    override fun onClick(v: View?) {
        when (v) {
            binding.imgPlayPause -> {
                if (exoPlayer.isPlaying) {
                    pauseVideo()
                } else {
                    playVideo()
                }
            }

            binding.txtSave -> {
                trimVideo()
            }

            binding.imgBack -> {
                finish()
            }

            binding.txtSelect -> {
                if (isReadStoragePermissionGranted(this@VideoPlayerActivity)) {
                    openIntentForVideoSelection()
                } else {
                    openAppSettings()
                }
            }
        }
    }

    private fun requestReadStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this@VideoPlayerActivity, arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                ), 1002
            )
        } else {
            ActivityCompat.requestPermissions(
                this@VideoPlayerActivity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                1002
            )
        }
    }

    private fun openAppSettings() {
        showErrorDialog(
            this,
            getString(R.string.this_permission_is_necessary_for_saving_and_accessing_your_videos),
            true
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", this@VideoPlayerActivity.packageName, null)
            }
            startActivity(intent)
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun openIntentForVideoSelection() {
        try {
            val pickVideoIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "video/*"
            }
            val isActivityAvailable =
                pickVideoIntent.resolveActivity(this.packageManager) != null

            if (isActivityAvailable) {
                galleryActivityResultLauncher.launch(pickVideoIntent)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.no_suitable_app_was_found_for_selecting_videos),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val galleryActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            val selectedImageUri = result.data?.data!!
            val cachedFile = saveVideoToCache(selectedImageUri)
            if (!cachedFile?.absolutePath.isNullOrEmpty()) {
                videoPath = cachedFile?.absolutePath.toString()
                val videoFile = File(videoPath)
                videoUri = FileProvider.getUriForFile(
                    this@VideoPlayerActivity,
                    "${this.packageName}.fileprovider",
                    videoFile
                )
                binding.clVideoController.visible()
                binding.txtSelect.gone()
                setUpVideoPlayback()
                playVideo()
                binding.rangeSeekBarView.setTouchEnabled(true)
            } else {
                binding.clVideoController.gone()
                binding.txtSelect.visible()
            }
        }
    }

    private fun saveVideoToCache(
        videoUri: Uri,
    ): File? {
        return try {
            val inputStream: InputStream? =
                this@VideoPlayerActivity.contentResolver.openInputStream(videoUri)
            val videoCacheDir = File(this.filesDir, "videos")
            if (!videoCacheDir.exists()) {
                videoCacheDir.mkdirs()
            }
            val cacheFileName = "cached_video_${System.currentTimeMillis()}.mp4"
            val cacheFile = File(videoCacheDir, cacheFileName)

            inputStream?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun trimVideo() {
        pauseVideo()
        val videoCacheDir = File(this.filesDir, "videos")
        if (!videoCacheDir.exists()) {
            videoCacheDir.mkdirs()
        }

        val cacheFile = File(videoCacheDir, "cached_trimmed_video_${System.currentTimeMillis()}.mp4")
        val trimTime = endPosition - startPosition
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(this, videoUri)
        val metadataKeyDuration =
            mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()

        if (trimTime < minTimeFrame) {
            if (metadataKeyDuration - endPosition > minTimeFrame - trimTime) {
                endPosition += minTimeFrame - trimTime
            } else if (startPosition > minTimeFrame - trimTime) {
                startPosition -= minTimeFrame - trimTime
            }
        }

        val leftThumb = binding.rangeSeekBarView.thumbs[0].value.toLong()
        val rightThumb = binding.rangeSeekBarView.thumbs[1].value.toLong()
        val startTimeMillis = (leftThumb * videoDuration) * 10
        val endTimeMillis = (rightThumb * videoDuration) * 10

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.e("videoTrimmed - ", "trimStart.........")
                startTrim(
                    this@VideoPlayerActivity,
                    videoUri,
                    cacheFile,
                    startTimeMillis.toLong(),
                    endTimeMillis.toLong(),
                    (videoDuration * 1000).toLong(),
                    callback = object : VideoTrimmingListener {
                        override fun onVideoPrepared(mp: MediaPlayer) {
                            Log.e("videoTrimmed - ", "onVideoPrepared")
                        }

                        override fun onCompleteVideo() {
                            Log.e("videoTrimmed - ", "onCompleteVideo")
                        }

                        override fun onTrimStarted() {
                            Log.e("videoTrimmed - ", "onTrimStarted")
                        }

                        override fun onFinishedTrimming(uri: Uri?) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (uri != null) {
                                    Toast.makeText(
                                        this@VideoPlayerActivity,
                                        "Trim success",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    val trimmedVideoPath = uri.path ?: uri.toString()
                                    Log.e("videoTrimmed - ", "Trimmed Video Path: $trimmedVideoPath")

                                    videoPath = trimmedVideoPath
                                    releaseExoPlayer()
                                    setUpVideoPlayback()
                                    trimVideoCloseEvent()
                                } else {
                                    Toast.makeText(
                                        this@VideoPlayerActivity,
                                        "Trimming failed!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }

                        override fun onCancelTrimming() {
                            Log.e("videoTrimmed - ", "onCancelTrimming")
                        }

                        override fun onErrorWhileViewingVideo(what: Int, extra: Int) {
                            Log.e("videoTrimmed - ", "onErrorWhileViewingVideo")
                        }
                    }
                )
            } catch (e: Throwable) {
                e.printStackTrace()
                Thread.getDefaultUncaughtExceptionHandler()
                    ?.uncaughtException(Thread.currentThread(), e)
            }
        }
    }

    private fun releaseExoPlayer() {
        exoPlayer.stop()
        exoPlayer.release()
        exoPlayer = ExoPlayer.Builder(this).build() // Recreate ExoPlayer
        binding.videoPlayer.player = exoPlayer // Set new player instance
    }

    private fun trimVideoCloseEvent() {
        //binding.rangeSeekBarView.changeColor()
        binding.rangeSeekBarView.setTouchEnabled(true)
        binding.rangeSeekBarView.setThumbValue(0, 0f)
        binding.rangeSeekBarView.setThumbValue(1, 100f)
        binding.txtSave.visible()
        binding.imgBack.visible()
    }

    private fun setProgressToMinOrMax(progressThumbsValues: Pair<Int, Int>, progress: Int) {
        if (progress < (progressThumbsValues.first * binding.topSeekbarProcess.max / 100)) {
            binding.topSeekbarProcess.progress =
                progressThumbsValues.first * binding.topSeekbarProcess.max / 100
        } else {
            binding.topSeekbarProcess.progress =
                progressThumbsValues.second * binding.topSeekbarProcess.max / 100
        }
    }

    private fun showTimer(progress: Int) {
        val width: Int =
            binding.topSeekbarProcess.width - binding.topSeekbarProcess.paddingLeft - binding.topSeekbarProcess.paddingRight

        val thumbPos: Int =
            width * binding.topSeekbarProcess.progress / binding.topSeekbarProcess.max

        binding.seekLineLinear.translationX = thumbPos.toFloat()
        binding.seekLineTimeTextView.text = formatMilliseconds(
            progress.toLong(),
            binding.topSeekbarProcess.max.toLong()
        )
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        exoPlayer = ExoPlayer.Builder(this, renderersFactory).build()
        binding.videoPlayer.player = exoPlayer
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            val progressThumbsValues = Pair(
                binding.rangeSeekBarView.thumbs[0].value.toInt(),
                binding.rangeSeekBarView.thumbs[1].value.toInt()
            )

            if (isBetweenCutRange(
                    progress = progress,
                    maxProgress = binding.topSeekbarProcess.max,
                    thumbsValue = progressThumbsValues
                )
            ) {
                increaseProgress(this)
            } else {
                resetProgress()
            }
        }
    }

    fun isBetweenCutRange(thumbsValue: Pair<Int, Int>, progress: Int, maxProgress: Int): Boolean {
        val minValue = thumbsValue.first * maxProgress / 100
        val maxValue = thumbsValue.second * maxProgress / 100

        return progress in minValue..maxValue
    }

    private fun increaseProgress(runnable: Runnable) {
        binding.topSeekbarProcess.progress = progress
        progress += 100
        handler.postDelayed(runnable, updateInterval)
    }

    private fun resetProgress() {
        val seekTo =
            ((binding.rangeSeekBarView.thumbs[0].value.toInt() * binding.topSeekbarProcess.max / 100) + 100)
        progress = seekTo
        exoPlayer.seekTo(seekTo.toLong())
    }

    private fun startProgress() {
        handler.removeCallbacks(progressRunnable)
        handler.post(progressRunnable)
    }

    private fun stopProgress() {
        handler.removeCallbacks(progressRunnable)
    }

    private fun setTrimListener() {
        binding.rangeSeekBarView.addOnRangeSeekBarListener(object : OnRangeSeekBarListener {
            override fun onCreate(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float) {
                // Do nothing
            }

            override fun onSeek(
                rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float, pos: Float
            ) {
                rangeSeekBarView?.thumbs?.let { onSeekThumbs(index, value) }
                binding.topSeekbarProcess.gone()
            }

            override fun onSeekStart(
                rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float
            ) {
                binding.topSeekbarProcess.gone()
                // Do nothing
            }

            override fun onSeekStop(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float) {
                binding.topSeekbarProcess.visible()
                pauseVideo()
            }
        })
    }


    private fun onSeekThumbs(
        index: Int, value: Float
    ) {
        pauseVideo()
        when (index) {
            0 -> {
                startPosition = (videoDuration * value / 100L).toInt()
                setProgressBarPosition(startPosition)
            }

            1 -> {
                endPosition = (videoDuration * value / 100L).toInt()
                setProgressBarPosition(endPosition)
            }
        }
    }

    private fun setProgressBarPosition(position: Int) {
        if (videoDuration > 0) {
            binding.topSeekbarProcess.visible()
            val progress = (1000L * position / videoDuration).toInt()
            binding.topSeekbarProcess.progress = progress
        }
    }

    private fun setSeekBarPosition() {
        startPosition = 0
        endPosition = videoDuration.toInt()
        binding.rangeSeekBarView.setThumbValue(0, (startPosition * 100f / videoDuration).toFloat())
        binding.rangeSeekBarView.setThumbValue(1, (endPosition * 100f / videoDuration).toFloat())
    }

    @OptIn(UnstableApi::class)
    private fun setUpVideoPlayback() {
        progress = 0
        val mediaItem = MediaItem.fromUri(videoPath)

        exoPlayer.apply {
            addListener(object : Player.Listener {
                @Deprecated("Deprecated in Java")
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        if (!isVideoPaused) {
                            exoPlayer.play()
                            binding.imgPlayPause.setImageDrawable(
                                ContextCompat.getDrawable(
                                    this@VideoPlayerActivity,
                                    R.drawable.ic_video_pause
                                )
                            )
                            startProgress()
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        progress = 0
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                        binding.imgPlayPause.setImageDrawable(
                            ContextCompat.getDrawable(
                                this@VideoPlayerActivity,
                                R.drawable.ic_video_pause
                            )
                        )
                    }
                }
            })
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItem(mediaItem)
            prepare()
        }

        videoDuration = getVideoDurationInSeconds(videoPath)
        binding.topSeekbarProcess.max = videoDuration.toInt() * 1000
        binding.timeLineView.setVideo(Uri.parse(videoPath))
        setSeekBarPosition()

        // set top frame color and below frame color
        binding.rangeSeekBarView.setTopFrameColor(FRAME_BLUE_COLOR)
        binding.rangeSeekBarView.setBelowFrameColor(FRAME_YELLOW_COLOR)

        //set playPause Icon Background color
        setPlayPauseIconBackgroundColor(FRAME_BLUE_COLOR)
        //set timeText Background Color
        setTimeTextViewBackgroundColor(FRAME_BLUE_COLOR)
    }

    private fun rangeBarFrame() {
        if (binding.rangeSeekBarView.isYellowColor) {
            binding.rangeSeekBarView.setTouchEnabled(false)
            binding.rangeSeekBarView.setThumbValue(0, 0f)
            binding.rangeSeekBarView.setThumbValue(1, 100f)
        } else {
            if (!binding.trimmingContainer.isVisible) {
                binding.trimmingContainer.visible()
            }
            binding.rangeSeekBarView.setTouchEnabled(true)
        }
        binding.rangeSeekBarView.changeColor()
    }

    @SuppressLint("DefaultLocale")
    private fun formatMilliseconds(ms: Long, totalDuration: Long): String {
        return if (totalDuration < 60000) { // Less than 1 minute
            val seconds = ms / 1000
            val milliseconds = (ms % 1000) / 10
            String.format("%d.%02d", seconds, milliseconds)
        } else { // 1 minute or more
            val minutes = ms / 60000
            val seconds = (ms % 60000) / 1000
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun playVideo() {
        startProgress()
        exoPlayer.play()
        isVideoPaused = false

        binding.imgPlayPause.setImageDrawable(
            ContextCompat.getDrawable(
                this, R.drawable.ic_video_pause
            )
        )
    }

    private fun pauseVideo() {
        stopProgress()
        Handler(Looper.getMainLooper()).post {
            exoPlayer.pause()
        }
        isVideoPaused = true

        binding.imgPlayPause.setImageDrawable(
            ContextCompat.getDrawable(
                this, R.drawable.ic_video_play
            )
        )
    }

    override fun onPause() {
        super.onPause()
        pauseVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::exoPlayer.isInitialized) {
            exoPlayer.release()
        }
    }

    private fun setTimeTextViewBackgroundColor(color: String){
        binding.seekLineTimeTextView.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(color))
    }

    private fun setTimeTextColor(color: String){
        binding.seekLineTimeTextView.setTextColor(ColorStateList.valueOf(Color.parseColor(color)))
    }

    private fun setPlayPauseIconBackgroundColor(color: String){
        binding.imgPlayPause.backgroundTintList =
            ColorStateList.valueOf(Color.parseColor(color))
    }

}
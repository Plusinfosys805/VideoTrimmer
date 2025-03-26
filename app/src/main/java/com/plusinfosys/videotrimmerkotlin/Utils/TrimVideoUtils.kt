package com.plusinfosys.videotrimmerkotlin.Utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SparseIntArray
import com.plusinfosys.videotrimmerkotlin.interfaces.VideoTrimmingListener
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class TrimVideoUtils {
    companion object {
        fun startTrim(
            context: Context,
            inputVideoUri: Uri?,
            outputTrimmedVideoFile: File,
            startMs: Long,
            endMs: Long,
            durationInMs: Long,
            callback: VideoTrimmingListener
        ) {
            outputTrimmedVideoFile.parentFile?.mkdirs()
            outputTrimmedVideoFile.delete()
            var succeeded = false
            if (startMs <= 0L && endMs >= durationInMs) {
                try {
                    FileInputStream(
                        context.contentResolver.openFileDescriptor(inputVideoUri!!, "r")?.fileDescriptor
                    ).use { inputStream ->
                        FileOutputStream(outputTrimmedVideoFile).use { outputStream ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytesRead: Int
                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }
                            succeeded = outputTrimmedVideoFile.exists()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            if (!succeeded) {
                succeeded = inputVideoUri?.let { genVideoUsingMuxer(context, it, outputTrimmedVideoFile.path, startMs, endMs, true, true) } == true
            }

            val finalSucceeded = succeeded
            Handler(Looper.getMainLooper()).post {
                callback.onFinishedTrimming(if (finalSucceeded) Uri.fromFile(outputTrimmedVideoFile) else null)
            }
        }

        private fun genVideoUsingMuxer(
            context: Context,
            uri: Uri,
            dstPath: String,
            startMs: Long,
            endMs: Long,
            useAudio: Boolean,
            useVideo: Boolean
        ): Boolean {
            val extractor = MediaExtractor()
            var fileDescriptor: FileDescriptor? = null
            fileDescriptor = try { context.contentResolver.openFileDescriptor(uri, "r")!!.fileDescriptor
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return false
            }
            if (fileDescriptor != null) {
                extractor.setDataSource(fileDescriptor)
            }
            val trackCount = extractor.trackCount
            var muxer: MediaMuxer? = null
            val indexMap = SparseIntArray(trackCount)
            var bufferSize = -1
            try {
                muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    var selectCurrentTrack = false
                    if (mime != null && mime.startsWith("audio/") && useAudio) {
                        selectCurrentTrack = true
                    } else if (mime != null && mime.startsWith("video/") && useVideo) {
                        selectCurrentTrack = true
                    }
                    if (selectCurrentTrack) {
                        extractor.selectTrack(i)
                        try {
                            val dstIndex = muxer.addTrack(format)
                            indexMap.put(i, dstIndex)
                            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                                val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                                bufferSize = if (newSize > bufferSize) newSize else bufferSize
                            }
                        } catch (e: IllegalArgumentException) {
                            e.printStackTrace()
                            // Handle the exception or continue to the next track
                        }
                    }
                }
                if (bufferSize < 0) {
                    bufferSize = DEFAULT_BUFFER_SIZE
                }
                try {
                    val retrieverSrc = MediaMetadataRetriever()
                    retrieverSrc.setDataSource(fileDescriptor)
                    val degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    if (degreesString != null) {
                        val degrees = degreesString.toInt()
                        if (degrees >= 0) {
                            muxer.setOrientationHint(degrees)
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                    return false
                } catch (e: RuntimeException) {
                    e.printStackTrace()
                    return false
                }


                if (startMs > 0) {
                    extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }
                val dstBuf = ByteBuffer.allocate(bufferSize)
                val bufferInfo = MediaCodec.BufferInfo()
                muxer.start()
                while (true) {
                    bufferInfo.offset = 0
                    bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                    if (bufferInfo.size < 0) {
                        bufferInfo.size = 0
                        break
                    } else {
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000) {
                            break
                        } else {
                            bufferInfo.flags = extractor.sampleFlags
                            val trackIndex = extractor.sampleTrackIndex
                            muxer.writeSampleData(indexMap[trackIndex], dstBuf, bufferInfo)
                            extractor.advance()
                        }
                    }
                }
                muxer.stop()
                return true
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                muxer?.release()
            }
            return false
        }
    }

}
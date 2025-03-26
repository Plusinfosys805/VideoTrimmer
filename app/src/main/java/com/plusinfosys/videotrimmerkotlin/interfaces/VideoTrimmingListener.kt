package com.plusinfosys.videotrimmerkotlin.interfaces

import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.UiThread

interface VideoTrimmingListener {
    @UiThread
    fun onVideoPrepared(mp: MediaPlayer)
    fun onCompleteVideo()

    @UiThread
    fun onTrimStarted()

    /**
     * @param uri the result, trimmed video, or null if failed
     */
    @UiThread
    fun onFinishedTrimming(uri: Uri?)
    fun onCancelTrimming()

    /**
     * check [android.media.MediaPlayer.OnErrorListener]
     */
    @UiThread
    fun onErrorWhileViewingVideo(what: Int, extra: Int)
}
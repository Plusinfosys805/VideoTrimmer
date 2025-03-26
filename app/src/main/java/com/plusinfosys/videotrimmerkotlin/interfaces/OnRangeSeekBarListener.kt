package com.plusinfosys.videotrimmerkotlin.interfaces

import com.plusinfosys.videotrimmerkotlin.customViews.RangeSeekBarView


interface OnRangeSeekBarListener {
    fun onCreate(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)

    fun onSeek(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float, pos: Float)

    fun onSeekStart(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)

    fun onSeekStop(rangeSeekBarView: RangeSeekBarView?, index: Int, value: Float)
}
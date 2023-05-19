package com.acorn.xfreechart.library.data

import android.graphics.Rect
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry

/**
 * Created by acorn on 2023/5/16.
 */
data class FixedMarkerEntry(
    val id: Int,
    private val text: String,
    val position: Entry,
    val axisDependency: AxisDependency
) {
    val textEntry = Entry(0f, 0f, text)
    //记录绘制区域
    val markerRect = Rect()
}
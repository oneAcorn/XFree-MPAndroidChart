package com.acorn.xfreechart.library.dataprovider

import com.acorn.xfreechart.library.data.BezierData
import com.acorn.xfreechart.library.data.BezierEntry
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider

/**
 * Created by acorn on 2023/5/8.
 */
interface XFreeDataProvider : LineDataProvider {
    fun getBezierData(): BezierData?
}
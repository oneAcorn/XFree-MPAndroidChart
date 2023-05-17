package com.acorn.xfreechart.library.dataset

import com.github.mikephil.charting.components.YAxis.AxisDependency

/**
 * Created by acorn on 2023/5/16.
 */
interface IBezierDataSet {
    /**
     * Calculates the minimum and maximum x and y values (mXMin, mXMax, mYMin, mYMax).
     *
     */
    fun calcMinMax()

    fun getXMin(): Float

    fun getXMax(): Float

    fun getYMin(): Float

    fun getYMax(): Float

    fun getAxisDependency(): AxisDependency
}
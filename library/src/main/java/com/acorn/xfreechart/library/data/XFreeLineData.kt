package com.acorn.xfreechart.library.data

import com.acorn.xfreechart.library.dataset.IBezierDataSet
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet

/**
 * Created by acorn on 2023/5/16.
 */
open class XFreeLineData : LineData {
    val bezierData: BezierData = BezierData()
    val fixedMarkerData: FixedMarkerData = FixedMarkerData()

    constructor() : super()
    constructor(vararg dataSets: ILineDataSet?) : super(*dataSets)
    constructor(dataSets: MutableList<ILineDataSet>?) : super(dataSets)


    //region 计算x,y范围.需要重新计算mYMax,mYMin,mXMax,mXMin.mLeftAxisMax,mLeftAxisMin,mRightAxisMax,mRightAxisMin.

    override fun calcMinMax() {
        if (mDataSets == null) return

        mYMax = -Float.MAX_VALUE
        mYMin = Float.MAX_VALUE
        mXMax = -Float.MAX_VALUE
        mXMin = Float.MAX_VALUE


        for (set in mDataSets) {
            calcMinMax(set)
        }

        val bezierDataSets = bezierData.getDataSets()
        if (bezierDataSets.isNotEmpty()) {
            for (d in bezierDataSets) {
                calcBezierMinMax(d)
            }
        }
    }

    override fun calcMinMax(e: Entry?, axis: YAxis.AxisDependency?) {
        super.calcMinMax(e, axis)
    }

    protected fun calcBezierMinMax(d: IBezierDataSet) {
        if (mYMax < d.getYMax()) mYMax = d.getYMax()
        if (mYMin > d.getYMin()) mYMin = d.getYMin()

        if (mXMax < d.getXMax()) mXMax = d.getXMax()
        if (mXMin > d.getXMin()) mXMin = d.getXMin()

        if (d.getAxisDependency() == AxisDependency.LEFT) {
            if (mLeftAxisMax < d.getYMax()) mLeftAxisMax = d.getYMax()
            if (mLeftAxisMin > d.getYMin()) mLeftAxisMin = d.getYMin()
        } else {
            if (mRightAxisMax < d.getYMax()) mRightAxisMax = d.getYMax()
            if (mRightAxisMin > d.getYMin()) mRightAxisMin = d.getYMin()
        }
    }

    override fun calcMinMax(d: ILineDataSet) {
        super.calcMinMax(d)
    }

    override fun calcMinMaxY(fromX: Float, toX: Float) {
        super.calcMinMaxY(fromX, toX)
    }
    //endregion
}
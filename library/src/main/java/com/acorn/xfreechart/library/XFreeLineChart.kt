package com.acorn.xfreechart.library

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.acorn.xfreechart.library.data.BezierData
import com.acorn.xfreechart.library.data.FixedMarkerData
import com.acorn.xfreechart.library.data.XFreeLineData
import com.acorn.xfreechart.library.dataprovider.XFreeDataProvider
import com.acorn.xfreechart.library.listener.FixedMarkerTouchListener
import com.acorn.xfreechart.library.renderer.XFreeLineChartRenderer
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.highlight.IHighlighter
import com.github.mikephil.charting.selectarea.SelectAreaHelper

/**
 * x轴不必递增的LineChart
 * Created by acorn on 2023/4/7.
 */
class XFreeLineChart : BarLineChartBase<XFreeLineData>, XFreeDataProvider {
    private val markerTouchListener = FixedMarkerTouchListener(this)

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    override fun init() {
        super.init()
        mRenderer = XFreeLineChartRenderer(this, mAnimator, mViewPortHandler)
        mSelectAreaHelper =
            SelectAreaHelper(this, ViewConfiguration.get(context).scaledTouchSlop, false, this)
    }

    override fun getBezierData(): BezierData? = mData?.bezierData

    override fun getFixedMarkerData(): FixedMarkerData? = mData?.fixedMarkerData

    override fun getLineData(): LineData? {
        return mData
    }

    override fun setData(data: XFreeLineData?) {
        super.setData(data)
    }

    fun setHighlighter(highlighter: IHighlighter) {
        this.mHighlighter = highlighter
    }

    override fun onDetachedFromWindow() {
        // releases the bitmap in the renderer to avoid oom error
        if (mRenderer != null && mRenderer is XFreeLineChartRenderer) {
            (mRenderer as? XFreeLineChartRenderer)?.releaseBitmap()
        }
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isConsume = markerTouchListener.onTouch(this, event)
        if (isConsume) return true
        return super.onTouchEvent(event)
    }

    override fun refreshUI() {
        invalidate()
    }
}
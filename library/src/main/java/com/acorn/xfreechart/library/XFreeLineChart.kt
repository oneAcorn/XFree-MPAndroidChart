package com.acorn.xfreechart.library

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import com.acorn.xfreechart.library.data.BezierData
import com.acorn.xfreechart.library.data.FixedMarkerData
import com.acorn.xfreechart.library.data.XFreeLineData
import com.acorn.xfreechart.library.dataprovider.XFreeDataProvider
import com.acorn.xfreechart.library.listener.FixedMarkerTouchListener
import com.acorn.xfreechart.library.listener.SelectAreaTouchHelper
import com.acorn.xfreechart.library.renderer.XFreeLineChartRenderer
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.highlight.IHighlighter
import com.github.mikephil.charting.selectarea.SelectedSet

/**
 * x轴不必递增的LineChart
 * Created by acorn on 2023/4/7.
 */
class XFreeLineChart : BarLineChartBase<XFreeLineData>, XFreeDataProvider {
    private val markerTouchListener = FixedMarkerTouchListener(this)
    private val mSelectAreaTouchHelper = SelectAreaTouchHelper(context, this, this)

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
        if (mSelectAreaTouchHelper.onTouch(this, event)) return true
        if (markerTouchListener.onTouch(this, event)) return true
        return super.onTouchEvent(event)
    }

    override fun refreshUI() {
        invalidate()
    }


    //region 选区

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mSelectAreaTouchHelper.draw(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mSelectAreaTouchHelper.calculateBounds(w, h)
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || mSelectAreaTouchHelper.verifyDrawable(who)
    }

    fun enterSelectAreaMode() {
        mSelectAreaTouchHelper.selectAreaMode = true
    }

    fun quitSelectAreaMode() {
        mSelectAreaTouchHelper.selectAreaMode = false
    }

    fun isInSelectAreaMode() = mSelectAreaTouchHelper.selectAreaMode

    fun clearSelectedData() {
        val dataSets = mData.dataSets
        if (dataSets?.isNotEmpty() != true) return
        for (set in dataSets) {
            val size = set.entryCount
            for (i in 0 until size) {
                val entry = set.getEntryForIndex(i)
                entry.isHighLight = false
            }
        }
    }

    fun getSelectedData(): List<SelectedSet>? {
        val dataSets = mData.dataSets
        if (dataSets?.isNotEmpty() != true) return null
        val ret = mutableListOf<SelectedSet>()
        for (set in dataSets) {
            val size = set.entryCount
            val entries = mutableListOf<Entry>()
            for (i in 0 until size) {
                val entry = set.getEntryForIndex(i)
                if (entry.isHighLight) {
                    entries.add(entry)
                }
            }
            ret.add(SelectedSet(set, entries))
        }
        return ret
    }
    //endregion
}
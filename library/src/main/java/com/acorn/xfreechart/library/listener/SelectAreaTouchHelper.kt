package com.acorn.xfreechart.library.listener

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.acorn.xfreechart.library.dataprovider.XFreeDataProvider
import com.acorn.xfreechart.library.dataset.XFreeLineDataSet
import com.github.mikephil.charting.selectarea.SelectAreaDrawable

/**
 * Created by acorn on 2023/5/19.
 */
class SelectAreaTouchHelper(
    context: Context,
    callback: Drawable.Callback,
    private val mChart: XFreeDataProvider
) :
    View.OnTouchListener {
    var selectAreaMode = false
    private val minTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val mDrawable = SelectAreaDrawable(minTouchSlop, false).apply {
        this.callback = callback
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        if (!selectAreaMode) return false
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> startDragging(event)
            MotionEvent.ACTION_MOVE -> onDragging(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopDragging()
        }
        return true
    }

    private fun startDragging(event: MotionEvent) {
        mDrawable.setStartPoint(event.x, event.y)
    }

    private fun onDragging(event: MotionEvent) {
        mDrawable.setMovedPoint(event.x, event.y)
    }

    private fun stopDragging() {
        selectPoints()
        mDrawable.reset()
        mDrawable.invalidateSelf()
//        mChart.refreshUI()
    }

    private val pointBuf = FloatArray(2)
    private fun selectPoints() {
        if (!selectAreaMode) return
        var rect = mDrawable.mDrawRect ?: return
        val dataSets = mChart.lineData.dataSets ?: return
        if (dataSets.isEmpty()) return
        if (rect.left > rect.right) { //contains方法不考虑left>right的情况,所以得反转一下
            rect = RectF(rect.right, rect.top, rect.left, rect.bottom)
        }
        if (rect.top > rect.bottom) {
            rect = RectF(rect.left, rect.bottom, rect.right, rect.top)
        }
        for (set in dataSets) {
            if (set is XFreeLineDataSet<*> && !set.isSelectAreable) continue
            val trans = mChart.getTransformer(set.axisDependency)
            val size = set.entryCount
            for (i in 0 until size) {
                val entry = set.getEntryForIndex(i)
                pointBuf[0] = entry.x
                pointBuf[1] = entry.y
                trans.pointValuesToPixel(pointBuf)
                val isInArea = rect.contains(pointBuf[0], pointBuf[1])
                if (isInArea) {
                    entry.isHighLight = !entry.isHighLight
                }
            }
        }
    }

    fun draw(canvas: Canvas) {
        if (!selectAreaMode) return
        mDrawable.draw(canvas)
    }

    fun verifyDrawable(who: Drawable): Boolean {
        return who == mDrawable
    }

    fun calculateBounds(w: Int, h: Int) {
        mDrawable.setBounds(0, 0, w, h)
    }
}
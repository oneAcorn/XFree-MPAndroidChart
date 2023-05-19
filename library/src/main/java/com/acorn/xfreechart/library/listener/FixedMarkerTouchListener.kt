package com.acorn.xfreechart.library.listener

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import com.acorn.xfreechart.library.data.FixedMarkerEntry
import com.acorn.xfreechart.library.dataprovider.XFreeDataProvider

/**
 * Created by acorn on 2023/5/18.
 */
class FixedMarkerTouchListener(private val mChart: XFreeDataProvider) : View.OnTouchListener {
    private var dragEntry: FixedMarkerEntry? = null
    private var xMin = -Float.MAX_VALUE
    private var xMax = Float.MAX_VALUE
    private var yMin = -Float.MAX_VALUE
    private var yMax = Float.MAX_VALUE

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent): Boolean {
        val markerEntries = mChart.getFixedMarkerData()?.getMarkers()
        if (markerEntries?.isNotEmpty() != true) return false
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                dragEntry = checkInterested(event, markerEntries)
                if (dragEntry == null) return false

                //当dragEntry!=null时,折线图不能缩放和移动.
                //以下数据此时就是固定值了
                xMin = mChart.xChartMin
                xMax = mChart.xChartMax
                yMin = mChart.yChartMin
                yMax = mChart.yChartMax
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragEntry == null) return false
                performDrag(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragEntry = null
            }
        }
        if (dragEntry != null) {
            mChart.refreshUI()
        }
        return dragEntry != null
    }

    private val pointArr = FloatArray(2)
    private fun performDrag(event: MotionEvent) {
        val entry = dragEntry ?: return
        val trans = mChart.getTransformer(entry.axisDependency) ?: return

        pointArr[0] = event.x
        pointArr[1] = event.y
        trans.pixelsToValue(pointArr)
        var pointX = pointArr[0]
        if (pointX < xMin)
            pointX = xMin
        if (pointX > xMax)
            pointX = xMax
        var pointY = pointArr[1]
        if (pointY < yMin)
            pointY = yMin
        if (pointY > yMax)
            pointY = yMax
        entry.position.x = pointX
        entry.position.y = pointY
    }

    private fun checkInterested(
        event: MotionEvent,
        markerEntries: List<FixedMarkerEntry>
    ): FixedMarkerEntry? {
        val x = event.x.toInt()
        val y = event.y.toInt()
        for (entry in markerEntries.reversed()) {
            if (entry.markerRect.contains(x, y)) {
                return entry
            }
        }
        return null
    }
}
package com.acorn.xfreechart.library.dataset

import android.graphics.Color
import com.acorn.xfreechart.library.data.BezierEntry
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import kotlin.math.sin

/**
 * Created by acorn on 2023/5/11.
 */

fun main() {
    //小数取余不靠谱
//    println("what:${3.3 % 1.1},${3.4f % 1.2f},${4 % 2}")
//    println("left:${findSineLeftPeak2(2f)}")
    val test = BezierDataSet(Color.GREEN, 2f, AxisDependency.LEFT)
    for (i in -20..20 step 2) {
        println("$i -> ${test.findSinePeakX(i.toFloat(), true, 2.00, 1.00)}")
    }
}

class BezierDataSet(val color: Int, val lineWidth: Float, val axisDependency: AxisDependency) {
    val mEntries = mutableListOf<BezierEntry>()

    companion object {
        private const val HALF_PI: Double = Math.PI / 2.00

        //详见 https://stackoverflow.com/questions/29022438/how-to-approximate-a-half-cosine-curve-with-bezier-paths-in-svg
        private const val BEZIER_SINE_LEFT_CONTROL_K = (Math.PI - 2.00) / Math.PI
        private const val BEZIER_SINE_RIGHT_CONTROL_K = 1 - BEZIER_SINE_LEFT_CONTROL_K
    }

    fun addEntry(entry: BezierEntry) {
        mEntries.add(entry)
    }

    fun removeEntry(entry: BezierEntry) {
        mEntries.remove(entry)
    }

    fun addEntries(entries: List<BezierEntry>) {
        mEntries.addAll(entries)
    }

    /**
     * Add sine y = a*sin(b*x+c)+d
     * 每次用贝塞尔画π长的线段,如-π/2 -> π/2 or π/2 -> (3/2)π
     *
     * @param startX 起始点,和abcd无关的值
     * @param endX 结束点,和abcd无关的值
     */
    fun addSine(
        startX: Float,
        endX: Float,
        a: Double = 1.00,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Float = 0f
    ) {
        val startLeftPeakX = findSinePeakX(startX, b = b, c = c)
        val endLeftPeakPosition = findSinePeakX(endX, b = b, c = c)
//        val endRightPeakPosition = findSinePeakN(endX, false) * Math.PI
        var curDrawPosition = startLeftPeakX
        val entries = mutableListOf<BezierEntry>()
        while (curDrawPosition <= endLeftPeakPosition) {
            //起始点
            val leftX = curDrawPosition
            //每次绘制多长(如果是sin(x),则每次绘制π长)
            val drawSegmentLength = Math.PI / b
            curDrawPosition += drawSegmentLength
            //结束点
            val rightX = curDrawPosition
            //控制点1的x轴
            val controlX1 = leftX + (rightX - leftX) * BEZIER_SINE_LEFT_CONTROL_K
            //控制点2的x轴
            val controlX2 = leftX + (rightX - leftX) * BEZIER_SINE_RIGHT_CONTROL_K
            //结束点
            val leftY = sin(b * leftX + c).toFloat() + d
            val rightY = sin(b * rightX + c).toFloat() + d
            val entry = BezierEntry(
                Entry(leftX.toFloat(), leftY),
                Entry(controlX1.toFloat(), leftY),
                Entry(controlX2.toFloat(), rightY),
                Entry(rightX.toFloat(), rightY)
            )
            entries.add(entry)
        }
        addEntries(entries)
    }

    /**
     * Find sine bottom peak
     * y = sin(b*x+c)
     * 找到贝塞尔曲线应该绘制的最左边起始点
     *
     * @param x
     * @param isLeft 左或右
     * @return
     */
    fun findSinePeakX(
        x: Float,
        isLeft: Boolean = true,
        b: Double = 1.00,
        c: Double = 0.00
    ): Double {
        //https://www.desmos.com/calculator
        //b影响波长,bx会使波长缩小b倍
        //c影响波的横移bx+c,c为正时,会使波向左横移c/b

        val dx = x.toDouble()
        val sineOffset = if (isLeft) {
            -(HALF_PI / b - c / b)
        } else {
            HALF_PI / b + c / b
        }
        //每次绘制多长(如果是sin(x),则每次绘制π长)
        val drawSegmentLength = Math.PI / b
        val n = (dx + sineOffset) / drawSegmentLength
        val minus = if (n > 0.00) 1 else -1
        return (n.toInt() + minus * 0.50) * drawSegmentLength - c / b
    }
}
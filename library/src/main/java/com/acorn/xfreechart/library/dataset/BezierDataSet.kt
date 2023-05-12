package com.acorn.xfreechart.library.dataset

import com.acorn.xfreechart.library.data.BezierEntry
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import kotlin.math.abs
import kotlin.math.sin

/**
 * Created by acorn on 2023/5/11.
 */

fun main() {
    //小数取余不靠谱
//    println("what:${3.3 % 1.1},${3.4f % 1.2f},${4 % 2}")
//    println("left:${findSineLeftPeak2(2f)}")
    for (i in -10..10 step 2) {
//        findSineLeftPeak2(i.toFloat())
        findSineRightPeakN(i.toFloat())
    }
}

private fun findSineLeftPeak2(x: Float): Double {
    val halfPI: Double = Math.PI / 2.00
    val dx = x.toDouble()
    val n = (dx - halfPI) / Math.PI
    //因为n需要转为int,此处防止比如-0.1转换时丢失负数
    val minus = if (n > 0.00) 1 else -1
    val n1 = abs(n.toInt())
    val ret = n.toInt() * Math.PI + minus * halfPI
    println("ret:$x -> ${n.toInt()}π + $minus*(π/2)")
    return ret
}

private fun findSineRightPeakN(x: Float): Double {
    val halfPI: Double = Math.PI / 2.00
    val dx = x.toDouble()
    val n = (dx + halfPI) / Math.PI
    val minus = if (n > 0.00) 1 else -1
    val ret = n.toInt() + minus * 0.50
    println("$x -> ${n.toInt()}π + $minus*(π/2)")
    return ret
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
     * Add sine
     * 每次用贝塞尔画π长的线段,如-π/2 -> π/2 or π/2 -> (3/2)π
     *
     * @param startX
     * @param endX
     */
    fun addSine(startX: Float, endX: Float) {
        val startLeftPeakN = findSinePeakN(startX, true)
        val endLeftPeakPosition = findSinePeakN(endX, true) * Math.PI
//        val endRightPeakPosition = findSinePeakN(endX, false) * Math.PI
        var curDrawPosition = startLeftPeakN * Math.PI
        val entries = mutableListOf<BezierEntry>()
        while (curDrawPosition <= endLeftPeakPosition) {
            //起始点
            val leftX = curDrawPosition
            curDrawPosition += Math.PI
            //结束点
            val rightX = curDrawPosition
            //控制点1的x轴
            val controlX1 = leftX + (rightX - leftX) * BEZIER_SINE_LEFT_CONTROL_K
            //控制点2的x轴
            val controlX2 = leftX + (rightX - leftX) * BEZIER_SINE_RIGHT_CONTROL_K
            //结束点
            val leftY = sin(leftX).toFloat()
            val rightY = sin(rightX).toFloat()
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
     * 找到贝塞尔曲线应该绘制的最左边起始点
     *
     * @param x
     * @param isLeft 左或右
     * @return 返回n*π中的n.比如传入-2,则返回-3/2,表示应该以(-3/2)π为起始点
     */
    private fun findSinePeakN(x: Float, isLeft: Boolean = true): Double {
        val dx = x.toDouble()
        val sineOffset = if (isLeft) -HALF_PI else HALF_PI
        val n = (dx + sineOffset) / Math.PI
        val minus = if (n > 0.00) 1 else -1
        return n.toInt() + minus * 0.50
    }
}
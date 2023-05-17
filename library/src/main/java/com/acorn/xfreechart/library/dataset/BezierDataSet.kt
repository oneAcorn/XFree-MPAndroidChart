package com.acorn.xfreechart.library.dataset

import android.graphics.Color
import com.acorn.xfreechart.library.data.BezierEntry
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import kotlin.math.*

/**
 * Created by acorn on 2023/5/11.
 */

fun main() {
    //小数取余不靠谱
//    println("what:${3.3 % 1.1},${3.4f % 1.2f},${4 % 2}")
//    println("left:${findSineLeftPeak2(2f)}")
    val test = BezierDataSet(Color.GREEN, 2f, AxisDependency.LEFT)
    for (i in -20..20 step 2) {
//        println("$i -> ${test.findCosinePeakX(i.toFloat(), true, -2.00, -3.00)}")
    }
}

class BezierDataSet(
    val color: Int,
    val lineWidth: Float,
    private val axisDependency: AxisDependency
) :
    IBezierDataSet {
    val mEntries = mutableListOf<BezierEntry>()
    private var _xMin = -Float.MAX_VALUE
    private var _xMax = Float.MAX_VALUE
    private var _yMin = -Float.MAX_VALUE
    private var _yMax = Float.MAX_VALUE

    companion object {
        private const val HALF_PI: Double = Math.PI / 2.00

        //详见 https://stackoverflow.com/questions/29022438/how-to-approximate-a-half-cosine-curve-with-bezier-paths-in-svg
        private const val BEZIER_SINE_LEFT_CONTROL_K = (Math.PI - 2.00) / Math.PI
        private const val BEZIER_SINE_RIGHT_CONTROL_K = 1 - BEZIER_SINE_LEFT_CONTROL_K
    }

    private fun addEntry(entry: BezierEntry) {
        mEntries.add(entry)
    }

    private fun addEntries(entries: List<BezierEntry>) {
        mEntries.addAll(entries)
    }

    fun clear() {
        mEntries.clear()
        resetMinMax()
    }


    //region All curve(or line)

    /**
     * Add line y = ax+b
     *
     * @param startX
     * @param endX
     * @param a
     * @param b
     */
    fun addLine(
        startX: Float,
        endX: Float,
        a: Float = 1f,
        b: Float = 0f
    ) {
        addEquation({
            val entries = mutableListOf<BezierEntry>()
            val startY = a * startX + b
            val endY = a * endX + b
            entries.add(BezierEntry(Entry(startX, startY), null, null, Entry(endX, endY)))
            entries
        }) { entries ->
            //只计算一个的情况
            if (entries.size != 1) return@addEquation
            val entry = entries[0]
            _xMin = min(entry.p1.x, entry.p2.x)
            _xMax = max(entry.p1.x, entry.p2.x)
            _yMin = min(entry.p1.y, entry.p2.y)
            _yMax = max(entry.p1.y, entry.p2.y)
        }
    }


    /**
     * Add cosine y = a*cos(b*x+c)+d
     * 每次用贝塞尔画π长的线段,如-π/2 -> π/2 or π/2 -> (3/2)π
     *
     * @param startX 起始点,和abcd无关的值
     * @param endX 结束点,和abcd无关的值
     */
    fun addCosine(
        startX: Float,
        endX: Float,
        a: Float = 1f,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Float = 0f
    ) {
        addEquation({
            val startLeftPeakX = findCosinePeakX(startX, b = b, c = c)
            //每次绘制多长(如果是sin(x),则每次绘制π长)
            val drawSegmentLength = Math.PI / abs(b)
            //多加一些偏移,防止画少了
            val endLeftPeakPosition = findCosinePeakX(endX, b = b, c = c) + drawSegmentLength / 2.00
//        val endRightPeakPosition = findSinePeakN(endX, false) * Math.PI
            var curDrawPosition = startLeftPeakX
            val entries = mutableListOf<BezierEntry>()
            while (curDrawPosition <= endLeftPeakPosition) {
                //起始点
                val leftX = curDrawPosition
                curDrawPosition += drawSegmentLength
                //结束点
                val rightX = curDrawPosition
                //控制点1的x轴
                val controlX1 = leftX + (rightX - leftX) * BEZIER_SINE_LEFT_CONTROL_K
                //控制点2的x轴
                val controlX2 = leftX + (rightX - leftX) * BEZIER_SINE_RIGHT_CONTROL_K
                //结束点
                val leftY = a * cos(b * leftX + c).toFloat() + d
                val rightY = a * cos(b * rightX + c).toFloat() + d
                val entry = BezierEntry(
                    Entry(leftX.toFloat(), leftY),
                    Entry(controlX1.toFloat(), leftY),
                    Entry(controlX2.toFloat(), rightY),
                    Entry(rightX.toFloat(), rightY)
                )
//            Log.i("acornTag", "addSine: $entry")
                entries.add(entry)
            }
            entries
        }) {
            calcSinCosMinMax(it, a, d)
        }
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
        a: Float = 1f,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Float = 0f
    ) {
        addEquation({
            val startLeftPeakX = findSinePeakX(startX, b = b, c = c)
            //每次绘制多长(如果是sin(x),则每次绘制π长)
            val drawSegmentLength = Math.PI / abs(b)
            //多加一些偏移,防止画少了
            val endLeftPeakPosition = findSinePeakX(endX, b = b, c = c) + drawSegmentLength / 2.00
//        val endRightPeakPosition = findSinePeakN(endX, false) * Math.PI
            var curDrawPosition = startLeftPeakX
            val entries = mutableListOf<BezierEntry>()
            while (curDrawPosition <= endLeftPeakPosition) {
                //起始点
                val leftX = curDrawPosition
                curDrawPosition += drawSegmentLength
                //结束点
                val rightX = curDrawPosition
                //控制点1的x轴
                val controlX1 = leftX + (rightX - leftX) * BEZIER_SINE_LEFT_CONTROL_K
                //控制点2的x轴
                val controlX2 = leftX + (rightX - leftX) * BEZIER_SINE_RIGHT_CONTROL_K
                //结束点
                val leftY = a * sin(b * leftX + c).toFloat() + d
                val rightY = a * sin(b * rightX + c).toFloat() + d
                val entry = BezierEntry(
                    Entry(leftX.toFloat(), leftY),
                    Entry(controlX1.toFloat(), leftY),
                    Entry(controlX2.toFloat(), rightY),
                    Entry(rightX.toFloat(), rightY)
                )
//            Log.i("acornTag", "addSine: $entry")
                entries.add(entry)
            }
            entries
        }) {
            calcSinCosMinMax(it, a, d)
        }
    }
    //endregion

    private fun addEquation(
        generateEntries: () -> List<BezierEntry>,
        calcEquationMinMax: (entries: List<BezierEntry>) -> Unit
    ) {
        //一个DataSet只画一个公式
        clear()
        val entries = generateEntries()
        addEntries(entries)
        resetMinMax()
        calcEquationMinMax(entries)
    }

    private fun calcSinCosMinMax(entries: List<BezierEntry>, a: Float, d: Float) {
        if (entries.isEmpty()) return
        val startEntry = entries[0]
        val endEntry = entries[entries.size - 1]
        _xMin = startEntry.p1.x
        _xMax = endEntry.p2.x
        _yMin = d - abs(a)
        _yMax = d + abs(a)
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
    private fun findSinePeakX(
        x: Float,
        isLeft: Boolean = true,
        b: Double = 1.00,
        c: Double = 0.00
    ): Double {
        //https://www.desmos.com/calculator
        //b影响波长,bx会使波长缩小b倍
        //c影响波的横移bx+c,c为正时,会使波向左横移c/b

        val dx = x.toDouble()
        val absB = abs(b)
        val sineOffset = if (isLeft) {
            -(HALF_PI / absB - c / b)
        } else {
            HALF_PI / absB + c / b
        }
        //每次绘制多长(如果是sin(x),则每次绘制π长)
        val drawSegmentLength = Math.PI / absB
        val n = (dx + sineOffset) / drawSegmentLength
        val minus = if (n > 0.00) 1 else -1
        return (n.toInt() + minus * 0.50) * drawSegmentLength - c / b
    }

    /**
     * Find cosine bottom peak
     * y = cos(b*x+c)
     * 找到贝塞尔曲线应该绘制的最左边起始点
     *
     * @param x
     * @param isLeft 左或右
     * @return
     */
    private fun findCosinePeakX(
        x: Float,
        isLeft: Boolean = true,
        b: Double = 1.00,
        c: Double = 0.00
    ): Double {
        //https://www.desmos.com/calculator
        //b影响波长,bx会使波长缩小b倍
        //c影响波的横移bx+c,c为正时,会使波向左横移c/b

        val dx = x.toDouble()
        val absB = abs(b)
        val cosineOffset = if (isLeft) {
            c / b
        } else {
            -c / b
        }
        //每次绘制多长(如果是sin(x),则每次绘制π长)
        val drawSegmentLength = Math.PI / absB
        val n = (dx + cosineOffset) / drawSegmentLength
        val minus = if (n > 0.00) 0 else -1
        return (n.toInt() + minus) * drawSegmentLength - c / b
    }

    private fun resetMinMax() {
        _xMin = -Float.MAX_VALUE
        _xMax = Float.MAX_VALUE
        _yMin = -Float.MAX_VALUE
        _yMax = Float.MAX_VALUE
    }

    override fun calcMinMax() {

    }

    override fun getXMin(): Float = _xMin

    override fun getXMax(): Float = _xMax

    override fun getYMin(): Float = _yMin

    override fun getYMax(): Float = _yMax
    override fun getAxisDependency(): AxisDependency = axisDependency
}
package com.acorn.demo.xfreechart

import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.acorn.demo.xfreechart.databinding.ActivityDemoBinding
import com.acorn.xfreechart.library.XFreeLineChart
import com.acorn.xfreechart.library.data.*
import com.acorn.xfreechart.library.dataset.BezierDataSet
import com.acorn.xfreechart.library.dataset.XFreeLineDataSet
import com.acorn.xfreechart.library.highlight.XFreeHighlighter
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener
import com.github.mikephil.charting.utils.ColorTemplate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Created by acorn on 2023/4/18.
 */
class DemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDemoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDemoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        binding.toolbar.title = "Demo"
        initLineChart()
//        addUnsortedData()
    }

    private fun addSortedData() {
        for (i in 0..200) {
            addEntry(Entry(i.toFloat(), Random.nextInt(100).toFloat()))
        }
    }

    private fun addUnsortedData() {
        for (i in 0..100) {
            addEntry(Entry(Random.nextInt(5000).toFloat(), Random.nextInt(5000).toFloat()))
        }
    }

    private fun resetChart() {
        binding.lineChart.clear()
    }

    private fun selectArea() {
        binding.lineChart.enterSelectAreaMode { selectedSets ->
            for (set in selectedSets) {
                Toast.makeText(
                    this@DemoActivity,
                    "select(${set.set.label}):${set.entrys}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setCirclesDisplayThreshold(threshold: Int) {
        val lineData = binding.lineChart.lineData ?: return
        for (set in lineData.dataSets) {
            if (set is XFreeLineDataSet<*>) {
                set.mPointVisibleThreshold = threshold
                set.notifyDataSetChanged()
            }
        }
        binding.lineChart.invalidate()
    }

    private fun addEntry(entry: Entry) {
        var lineData = binding.lineChart.data
        if (lineData == null) {
            val data = XFreeLineData()
            data.setValueTextColor(Color.WHITE)
            // add empty data
            binding.lineChart.data = data
            lineData = data
        }
        //a dataset represents a line
        var dataSet = lineData.getDataSetByIndex(0)
        if (dataSet == null) {
            dataSet = createSet()
            lineData.addDataSet(dataSet)
        }
        //add entry to the first line.
        lineData.addEntry(entry, 0)
        lineData.notifyDataChanged()

        // let the chart know it's data has changed
        binding.lineChart.notifyDataSetChanged()

        // limit the number of visible entries
//        lineChart.setVisibleXRangeMaximum(120f)
        // chart.setVisibleYRange(30, AxisDependency.LEFT);

        // move to the latest entry(this automatically refreshes the chart (calls invalidate()))
        binding.lineChart.moveViewToX(lineData.entryCount.toFloat())
//        lineChart.invalidate()
    }

//    private fun addBezierEntry(bezierEntry: BezierEntry) {
//        val bezierData = binding.lineChart.getBezierData() ?: return
//        var dataSet = bezierData.getDataSet(0)
//        if (dataSet == null) {
//            dataSet = createBezierSet()
//            bezierData.addDataSet(dataSet)
//        }
//        dataSet.addEntry(bezierEntry)
//        binding.lineChart.invalidate()
//    }

    private fun prepareBezierDataSetAndInvalidate(callback: BezierDataSet.() -> Unit) {
        val bezierData = binding.lineChart.getBezierData() ?: return
        var dataSet = bezierData.getDataSet(0)
        if (dataSet == null) {
            dataSet = createBezierSet()
            bezierData.addDataSet(dataSet)
        }
//        dataSet.addSine(startX, endX, a.toFloat(), b, c, d.toFloat())
        callback.invoke(dataSet)
        binding.lineChart.notifyDataSetChanged()
        binding.lineChart.invalidate()
    }

    private fun createBezierSet(): BezierDataSet {
        return BezierDataSet(Color.GREEN, 2f, YAxis.AxisDependency.LEFT)
    }

    private fun createSet(): XFreeLineDataSet<XFreeLineChart> {
        val set = XFreeLineDataSet(binding.lineChart, null, "Test Data")
        set.axisDependency = YAxis.AxisDependency.LEFT
        set.color = ColorTemplate.getHoloBlue()
        set.setCircleColor(Color.WHITE)
        set.setDrawCircles(false)
        set.lineWidth = 2f
        set.circleRadius = 4f
        set.fillAlpha = 65
        set.fillColor = ColorTemplate.getHoloBlue()
        set.highLightColor = Color.rgb(244, 117, 117)
        set.valueTextColor = Color.WHITE
        set.valueTextSize = 9f
        set.setDrawValues(false)
        return set
    }

    private fun initLineChart() {
        binding.lineChart.run {
//            setOnChartValueSelectedListener(this@DemoActivity)
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setDrawGridBackground(false)
            // if disabled, scaling can be done on x- and y-axis separately
            setPinchZoom(false)
            setBackgroundColor(Color.LTGRAY)

//            val data = XFreeLineData()
//            data.setValueTextColor(Color.WHITE)
//            // add empty data
//            this.data = data

            //点击point显示的MarkerView
            val markerView = MyMarkerView(this@DemoActivity, R.layout.custom_marker_view)
            markerView.chartView = this
            marker = markerView

            // get the legend (only possible after setting data)
            val l: Legend = legend
            // modify the legend ...
            l.form = Legend.LegendForm.LINE
            l.textColor = Color.BLUE
            l.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            l.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            l.orientation = Legend.LegendOrientation.VERTICAL
            l.setDrawInside(false)

            xAxis.textColor = Color.WHITE
            xAxis.setDrawGridLines(false)
            xAxis.setAvoidFirstLastClipping(true)
            xAxis.setLabelCount(12, false)
            xAxis.isEnabled = true
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.isDrawArrow = true

            axisLeft.textColor = Color.WHITE
//            axisLeft.axisMaximum = 100f
//            axisLeft.axisMinimum = 0f
            axisLeft.setDrawGridLines(true)
//            axisLeft.setCenterAxisLabels(true)
            axisLeft.isDrawArrow = true

            axisRight.isEnabled = false

            highlighter = XFreeHighlighter(this)

            onChartGestureListener = object : OnChartGestureListener {
                override fun onChartGestureStart(
                    me: MotionEvent?,
                    lastPerformedGesture: ChartTouchListener.ChartGesture?
                ) {
                }

                override fun onChartGestureEnd(
                    me: MotionEvent?,
                    lastPerformedGesture: ChartTouchListener.ChartGesture?
                ) {
//                    logI("onChartGestureEnd")
                    setHighlightAndMarkerEnable(true)
                }

                override fun onChartLongPressed(me: MotionEvent?) {
                }

                override fun onChartDoubleTapped(me: MotionEvent?) {
                }

                override fun onChartSingleTapped(me: MotionEvent?) {
                }

                override fun onChartFling(
                    me1: MotionEvent?,
                    me2: MotionEvent?,
                    velocityX: Float,
                    velocityY: Float
                ) {
                }

                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
//                    logI("onChartScale")
                    setHighlightAndMarkerEnable(false)
                }

                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
//                    logI("onChartTranslate")
                    setHighlightAndMarkerEnable(false)
                }

                override fun onChartTranslateEnd() {
//                    logI("onChartTranslateEnd")
                    setHighlightAndMarkerEnable(true)
                }
            }

//            isLogEnabled = true
        }
    }

    /**
     * Set highlight enable
     * For performance optimization
     * @param isEnable
     */
    private fun setHighlightAndMarkerEnable(isEnable: Boolean) {
        binding.lineChart.setDrawMarkers(isEnable)
        val lineData = binding.lineChart.data ?: return
        try {
            for (set in lineData.dataSets) {
                set.isHighlightEnabled = isEnable
            }
        } catch (e: ConcurrentModificationException) {
            e.printStackTrace()
        }
    }

    private fun testBezierData() {
        addMockSineData(-5f, 5f, 2000)
        prepareBezierDataSetAndInvalidate {
            addSine(-1f, 1f)
        }
//        addBezierEntry(
//            BezierEntry(
//                Entry(0f, 0f),
//                Entry(0.3642f, 0f),
//                Entry(0.6358f, 1f),
//                Entry(1f, 1f)
//            )
//        )
//        addBezierEntry(
//            BezierEntry(
//                Entry(100f, 100f),
//                Entry(136.42f, 100f),
//                Entry(163.58f, 0f),
//                Entry(200f, 0f)
//            )
//        )
    }

    private fun addMockSineData(
        startX: Float, endX: Float, totalPoints: Int,
        a: Double = 1.00,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Double = 0.00
    ) {
        //添加用一堆点模拟的sine曲线
        var x = startX
        val step = (endX - startX) / totalPoints.toFloat()
        while (x < endX) {
            val y = a * sin((b * x) + c) + d
            addEntry(Entry(x, y.toFloat()))
            x += step
        }
    }

    private fun addMockCosineData(
        startX: Float, endX: Float, totalPoints: Int,
        a: Double = 1.00,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Double = 0.00
    ) {
        //添加用一堆点模拟的sine曲线
        var x = startX
        val step = (endX - startX) / totalPoints.toFloat()
        while (x < endX) {
            val y = a * cos((b * x) + c) + d
            addEntry(Entry(x, y.toFloat()))
            x += step
        }
    }

    /**
     * Test sine data
     * y = a*sin(b*x+c)+d
     *
     * @param startX
     * @param endX
     * @param totalPoints
     */
    private fun testSineData(
        startX: Float, endX: Float, totalPoints: Int,
        a: Double = 1.00,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Double = 0.00
    ) {
        addMockSineData(startX, endX, totalPoints, a, b, c, d)

        //添加Sine公式绘制
        prepareBezierDataSetAndInvalidate {
            addSine(startX, endX, a.toFloat(), b, c, d.toFloat())
            binding.lineChart.lineData.notifyDataChanged()
        }
    }

    private fun testCosineData(
        startX: Float, endX: Float, totalPoints: Int,
        a: Double = 1.00,
        b: Double = 1.00,
        c: Double = 0.00,
        d: Double = 0.00
    ) {
        addMockCosineData(startX, endX, totalPoints, a, b, c, d)

        prepareBezierDataSetAndInvalidate {
            addCosine(startX, endX, a.toFloat(), b, c, d.toFloat())
        }
    }

    private fun testMarker() {
        binding.lineChart.getFixedMarkerData()
            ?.addMarker(FixedMarkerEntry(0, "Marker", Entry(0f, 0f), YAxis.AxisDependency.LEFT))
        binding.lineChart.invalidate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_demo, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isConsume = true
        when (item.itemId) {
            R.id.action_add_sorted_data -> {
                addSortedData()
            }
            R.id.action_add_unsorted_data -> {
                addUnsortedData()
            }
            R.id.action_reset_chart -> {
                resetChart()
            }
            R.id.action_select_area -> {
                selectArea()
            }
            R.id.action_limit_circles_number -> {
                item.isChecked = !item.isChecked
                val threshold = if (item.isChecked) 50 else -1
                setCirclesDisplayThreshold(threshold)
            }
            R.id.test_bezier -> {
                testBezierData()
            }
            R.id.test_sine -> {
                testSineData(-30f, 30f, 2000, a = 4.00, b = -2.00, c = 1.00, d = 3.00)
//                testSineData(-200f, 200f, 2000, a = -4.00, b = -2.00, c = -1.00, d = -3.00)
            }
            R.id.test_cosine -> {
                testCosineData(-30f, 30f, 2000, a = 4.00, b = -2.00, c = 1.00, d = 3.00)
            }
            R.id.test_marker -> {
                testMarker()
            }
            else -> {
                isConsume = false
            }
        }
        return if (isConsume) true else super.onOptionsItemSelected(item)
    }
}
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
        addData()
    }

    private fun addData() {
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
            val data = LineData()
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

    private fun createSet(): XFreeLineDataSet<XFreeLineChart> {
        val set = XFreeLineDataSet(binding.lineChart, null, "Test Data")
        set.axisDependency = YAxis.AxisDependency.LEFT
        set.color = ColorTemplate.getHoloBlue()
        set.setCircleColor(Color.WHITE)
        set.setDrawCircles(true)
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

//            val data = LineData()
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

            axisLeft.textColor = Color.WHITE
//            axisLeft.axisMaximum = 100f
//            axisLeft.axisMinimum = 0f
            axisLeft.setDrawGridLines(true)
//            axisLeft.setCenterAxisLabels(true)

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_demo, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isConsume = true
        when (item.itemId) {
            R.id.action_reset_chart -> {
                resetChart()
            }
            R.id.action_add_data -> {
                addData()
            }
            R.id.action_select_area -> {
                selectArea()
            }
            R.id.action_limit_circles_number -> {
                item.isChecked = !item.isChecked
                val threshold = if (item.isChecked) 50 else -1
                setCirclesDisplayThreshold(threshold)
            }
            else -> {
                isConsume = false
            }
        }
        return if (isConsume) true else super.onOptionsItemSelected(item)
    }
}
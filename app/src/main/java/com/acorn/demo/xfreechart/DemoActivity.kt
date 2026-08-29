package com.acorn.demo.xfreechart

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.acorn.demo.xfreechart.databinding.ActivityDemoBinding
import com.acorn.xfreechart.library.XFreeLineChart
import com.acorn.xfreechart.library.dataset.XFreeLineDataSet
import com.acorn.xfreechart.library.highlight.XFreeHighlighter
import com.acorn.xfreechart.library.renderer.XFreeLineChartRenderer
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
//        addUnsortedData()
    }

    private fun addSortedData() {
        for (i in 0..100) {
            addEntry(Entry(i.toFloat(), Random.nextInt(5000).toFloat()))
        }
    }

    private fun addUnsortedData() {
        for (i in 0..100) {
            addEntry(Entry(Random.nextInt(5000).toFloat(), Random.nextInt(5000).toFloat()))
        }
    }

    /**
     * Batch-insert [count] entries with a single notify at the end, and report the elapsed time.
     * This measures the one-shot insertion cost.
     */
    private fun addBatchData(count: Int, unsorted: Boolean = false) {
        var lineData = binding.lineChart.data
        if (lineData == null) {
            lineData = LineData()
            binding.lineChart.data = lineData
        }
        if (lineData.dataSetCount == 0) {
            lineData.addDataSet(createSet())
        }
        val start = SystemClock.elapsedRealtime()
        val baseX = if (lineData.entryCount > 0) lineData.xMax + 1f else 0f
        val yBase = if (lineData.entryCount > 0) lineData.yMax + 1f else 0f
        for (i in 0 until count) {
            val x = if (unsorted) Random.nextInt(5000).toFloat() else baseX + i
            val y = if (unsorted) Random.nextInt(5000).toFloat() else Random.nextInt(5000).toFloat()
            lineData.addEntry(Entry(x, y), 0)
        }
        lineData.notifyDataChanged()
        binding.lineChart.notifyDataSetChanged()
        binding.lineChart.invalidate()
        val elapsed = SystemClock.elapsedRealtime() - start
        Toast.makeText(
            this@DemoActivity,
            "batch insert $count entries in $elapsed ms (total ${lineData.entryCount})",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Simulate the realtime path (addEntry -> notifyDataChanged -> notifyDataSetChanged -> moveViewToX
     * per single entry), chunked across frames to avoid ANR, timing only the insert work.
     * Shows how the per-insert cost grows as the chart gets bigger (O(n) per insert).
     */
    private fun insertOneByOne(total: Int) {
        val handler = Handler(Looper.getMainLooper())
        var remaining = total
        var measureMs = 0L
        val chunkSize = 20
        val step = object : Runnable {
            override fun run() {
                val n = minOf(chunkSize, remaining)
                val start = SystemClock.elapsedRealtime()
                repeat(n) {
                    val x = (binding.lineChart.data?.xMax ?: 0f) + 1f
                    addEntry(Entry(x, Random.nextInt(5000).toFloat()))
                }
                measureMs += SystemClock.elapsedRealtime() - start
                remaining -= n
                if (remaining > 0) {
                    handler.post(this)
                } else {
                    Toast.makeText(
                        this@DemoActivity,
                        "one-by-one: $total inserts in $measureMs ms (avg ${measureMs / total} ms/insert)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        handler.post(step)
    }

    private var debugOverlay: TextView? = null

    private fun setShowDrawTime(enable: Boolean) {
        val renderer = binding.lineChart.renderer as? XFreeLineChartRenderer
        if (enable) {
            if (debugOverlay == null) {
                val tv = TextView(this).apply {
                    setTextColor(Color.BLACK)
                    textSize = 12f
                    setPadding(8, 8, 8, 8)
                    text = "line: -ms  circles: -ms"
                }
                val lp = ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topToBottom = R.id.toolbar
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                binding.root.addView(tv, lp)
                debugOverlay = tv
            }
            renderer?.onFrameDrawTimeListener = { lineMs, circleMs ->
                debugOverlay?.text =
                    String.format("line: %.1fms  circles: %.1fms", lineMs, circleMs)
            }
        } else {
            renderer?.onFrameDrawTimeListener = null
            debugOverlay?.let { binding.root.removeView(it) }
            debugOverlay = null
        }
        binding.lineChart.invalidate()
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
            R.id.action_add_sorted_data -> {
                addSortedData()
            }
            R.id.action_add_unsorted_data -> {
                addUnsortedData()
            }
            R.id.action_add_data_1w -> {
                addBatchData(10_000)
            }
            R.id.action_add_data_5w -> {
                addBatchData(50_000)
            }
            R.id.action_add_data_20w -> {
                addBatchData(200_000)
            }
            R.id.action_add_data_100w -> {
                addBatchData(1_000_000)
            }
            R.id.action_add_unsorted_data_10w -> {
                addBatchData(100_000, unsorted = true)
            }
            R.id.action_insert_1000_one_by_one -> {
                insertOneByOne(1000)
            }
            R.id.action_show_draw_time -> {
                item.isChecked = !item.isChecked
                setShowDrawTime(item.isChecked)
            }
            R.id.action_zoom_in -> {
                repeat(3) { binding.lineChart.zoomIn() }
            }
            R.id.action_zoom_out -> {
                binding.lineChart.zoomOut()
            }
            R.id.action_fit_screen -> {
                binding.lineChart.fitScreen()
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
            else -> {
                isConsume = false
            }
        }
        return if (isConsume) true else super.onOptionsItemSelected(item)
    }
}
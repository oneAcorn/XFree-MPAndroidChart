package com.acorn.xfreechart.library.renderer

import android.graphics.*
import android.os.SystemClock
import android.util.Log
import com.acorn.xfreechart.library.dataset.XFreeLineDataSet
import com.acorn.xfreechart.library.extendfun.safeGetEntryForIndex
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider
import com.github.mikephil.charting.interfaces.datasets.IDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineScatterCandleRadarDataSet
import com.github.mikephil.charting.renderer.DataRenderer
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.MPPointD
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.ViewPortHandler
import java.lang.ref.WeakReference

/**
 * Created by acorn on 2023/4/7.
 *
 * 渲染成本只与"屏幕内可见点数"相关,与数据总量无关:
 * - 借助XFreeLineDataSet的x排序索引做视口裁剪(兼容无序x);
 * - 源数据恰好按x递增时,密集场景自动降级为min-max像素抽稀(每列只保留极值,视觉无损);
 * - 圆点改为批量矩阵变换;可见点数超阈值自动不画(旧逻辑需手动设置阈值)。
 */
class XFreeLineChartRenderer(
    private val mChart: LineDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler
) : DataRenderer(animator, viewPortHandler) {
    private val TAG = "XFreeLineChartRenderer"

    companion object {
        /**
         * mPointVisibleThreshold<=0(自动模式)时,屏幕内可见点数超过该值则不画点
         */
        const val AUTO_MAX_VISIBLE_CIRCLES = 600

        /**
         * 无序数据超过该数量时不再全量精确绘制,
         * 两端点都在视口外、仅线段几何上越过视口的"穿越段"会被省略(有序数据不存在这种线段)
         */
        private const val UNSORTED_EXACT_SCAN_LIMIT = 20_000

        /**
         * 可见点数超过像素列数的该倍数时,有序数据改用min-max抽稀绘制
         */
        private const val DECIMATE_THRESHOLD_FACTOR = 2
    }

    /**
     * paint for the inner circle of the value indicators
     */
    private val mCirclePaintInner: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /**
     * Bitmap object used for drawing the paths (otherwise they are too long if
     * rendered directly on the canvas)
     */
    private var mDrawBitmap: WeakReference<Bitmap>? = null

    /**
     * on this canvas, the paths are rendered, it is initialized with the
     * pathBitmap
     */
    private var mBitmapCanvas: Canvas? = null

    /**
     * the bitmap configuration to be used
     */
    private var mBitmapConfig = Bitmap.Config.ARGB_8888

    /**
     * 可复用的线段缓冲(x1,y1,x2,y2...数据坐标)
     */
    private var mLineBuffer = FloatArray(1024)

    /**
     * 可复用的圆点坐标缓冲(x,y...像素坐标,批量变换)
     */
    private var mCircleBuffer = FloatArray(1024)

    /**
     * 圆点对应的源索引缓冲(与[mCircleBuffer]成对,用于取圆点颜色)
     */
    private var mCircleIndexBuffer = IntArray(512)

    /**
     * 无序数据绘制时的线段去重戳(以线段右端点的源索引为键)
     */
    private var mSegmentStamp = IntArray(1024)
    private var mStampCounter = 0

    /**
     * min-max抽稀的每列极值缓冲
     */
    private var mDecMinY = FloatArray(0)
    private var mDecMaxY = FloatArray(0)
    private var mDecMinIdx = IntArray(0)
    private var mDecMaxIdx = IntArray(0)
    private var mDecStamp = IntArray(0)
    private var mDecStampVal = 0

    /**
     * Per-frame draw time callback for performance testing.
     * Called on the end of every data pass with (line-draw ms, circles-draw ms).
     */
    var onFrameDrawTimeListener: ((lineMs: Float, circleMs: Float) -> Unit)? = null
    private var mLastLineDrawMs = 0f
    private var mLastCirclesDrawMs = 0f

    override fun initBuffers() {
    }

    override fun drawData(c: Canvas?) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        drawDataInternal(c)
        mLastLineDrawMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000f
    }

    private fun drawDataInternal(c: Canvas?) {
        c ?: return
        val width = mViewPortHandler.chartWidth.toInt()
        val height = mViewPortHandler.chartHeight.toInt()

        var drawBitmap = if (mDrawBitmap == null) null else mDrawBitmap!!.get()

        if (drawBitmap == null || drawBitmap.width != width || drawBitmap.height != height) {
            if (width > 0 && height > 0) {
                drawBitmap = Bitmap.createBitmap(width, height, mBitmapConfig)
                mDrawBitmap = WeakReference(drawBitmap)
                mBitmapCanvas = Canvas(drawBitmap)
            } else return
        }
        drawBitmap ?: return
        drawBitmap.eraseColor(Color.TRANSPARENT)

        for (set in mChart.lineData.dataSets) {
            if (!set.isVisible) continue
            drawDataSet(c, set)
        }
        c.drawBitmap(drawBitmap, 0f, 0f, mRenderPaint)
    }

    private fun drawDataSet(c: Canvas, dataSet: ILineDataSet) {
        val entryCount = dataSet.entryCount
        if (entryCount < 1) return
        mRenderPaint.strokeWidth = dataSet.lineWidth
        mRenderPaint.pathEffect = dataSet.dashPathEffect
        mRenderPaint.color = dataSet.color
        mRenderPaint.style = Paint.Style.STROKE

        val xfree = dataSet as? XFreeLineDataSet<*>
        if (xfree == null) {
            drawDataSetFullScan(c, dataSet, entryCount)
        } else {
            try {
                drawXFreeDataSet(c, xfree, entryCount)
            } catch (e: IndexOutOfBoundsException) { //渲染中数据被删除,本帧放弃,下帧自然恢复
                Log.w(TAG, "drawXFreeDataSet interrupted by data change")
            }
        }

        mRenderPaint.pathEffect = null
    }

    /**
     * 可见区裁剪后的绘制入口
     */
    private fun drawXFreeDataSet(c: Canvas, set: XFreeLineDataSet<*>, entryCount: Int) {
        val trans = mChart.getTransformer(set.axisDependency)
        val phaseY = mAnimator.phaseY
        set.ensureXOrder()
        val order = set.xOrderIndices()
        val orderSize = minOf(set.xOrderCount(), entryCount)
        var pLo = set.lowerBoundInOrder(mChart.lowestVisibleX)
        var pHi = set.upperBoundInOrder(mChart.highestVisibleX)
        if (pLo > orderSize) pLo = orderSize
        if (pHi > orderSize) pHi = orderSize
        if (pHi <= pLo) return

        if (set.isSortedByX()) {
            //源顺序==x顺序,可见entry在源数组中连续
            val srcLo = order[pLo]
            val srcHi = order[pHi - 1]
            if (srcLo >= entryCount || srcHi >= entryCount) return
            val count = srcHi - srcLo + 1
            val columns = mViewPortHandler.contentWidth().toInt().coerceAtLeast(1)
            if (count > columns * DECIMATE_THRESHOLD_FACTOR) {
                drawSortedDecimated(c, set, trans, phaseY, srcLo, srcHi, columns)
            } else {
                drawSegmentsRange(c, set, trans, phaseY, srcLo, srcHi, entryCount)
            }
        } else {
            if (entryCount <= UNSORTED_EXACT_SCAN_LIMIT) {
                //少量无序数据:全量精确绘制,视觉与旧版一致
                drawDataSetFullScan(c, set, entryCount)
            } else {
                drawUnsortedCulled(c, set, trans, phaseY, order, pLo, pHi, entryCount)
            }
        }
    }

    /**
     * 绘制源索引[srcLo]..[srcHi]的可见entry,并含各端一个相邻entry,
     * 即段i = (entry[i-1]→entry[i]),i∈[max(srcLo,1)..min(srcHi+1,n-1)]
     */
    private fun drawSegmentsRange(
        c: Canvas,
        set: XFreeLineDataSet<*>,
        trans: Transformer,
        phaseY: Float,
        srcLo: Int,
        srcHi: Int,
        entryCount: Int
    ) {
        val iStart = maxOf(srcLo, 1)
        val iEnd = minOf(srcHi + 1, entryCount - 1)
        if (iEnd < iStart) return
        val floats = (iEnd - iStart + 1) * 4
        if (mLineBuffer.size < floats) {
            mLineBuffer = FloatArray(floats)
        }
        val buf = mLineBuffer
        var j = 0
        try {
            for (i in iStart..iEnd) {
                val e1 = set.getEntryForIndex(i - 1)
                val e2 = set.getEntryForIndex(i)
                buf[j++] = e1.x
                buf[j++] = e1.y * phaseY
                buf[j++] = e2.x
                buf[j++] = e2.y * phaseY
            }
        } catch (e: IndexOutOfBoundsException) { //渲染中数据被删除,本帧放弃,下帧自然恢复
            return
        }
        trans.getValueToPixelMatrix().mapPoints(buf, 0, buf, 0, j shr 1)
        c.drawLines(buf, 0, j, mRenderPaint)
    }

    /**
     * 有序密集数据:min-max像素抽稀。
     * 屏幕每个像素列只保留y最小/最大的两个点,再以首尾entry锚定边界,
     * 线段数量从O(可见点数)降到O(像素列数),而每列极值都被保留,视觉上无损。
     */
    private fun drawSortedDecimated(
        c: Canvas,
        set: XFreeLineDataSet<*>,
        trans: Transformer,
        phaseY: Float,
        srcLo: Int,
        srcHi: Int,
        columns: Int
    ) {
        if (mDecStamp.size < columns) {
            mDecStamp = IntArray(columns)
            mDecMinY = FloatArray(columns)
            mDecMaxY = FloatArray(columns)
            mDecMinIdx = IntArray(columns)
            mDecMaxIdx = IntArray(columns)
        }
        val stamp = ++mDecStampVal
        val eFirst = set.getEntryForIndex(srcLo)
        val eLast = set.getEntryForIndex(srcHi)

        //像素列与数据x的线性映射:px = slope * x + intercept
        val p1 = trans.getPixelForValues(eFirst.x, 0f)
        val p2 = trans.getPixelForValues(eLast.x, 0f)
        val contentLeft = mViewPortHandler.contentLeft()
        val dx = eLast.x - eFirst.x
        val slope = if (dx != 0f) ((p2.x - p1.x) / dx).toFloat() else 0f
        val intercept = (p1.x - slope * eFirst.x).toFloat()
        MPPointD.recycleInstance(p1)
        MPPointD.recycleInstance(p2)

        val lastCol = columns - 1
        try {
            for (i in srcLo..srcHi) {
                val e = set.getEntryForIndex(i)
                var col = (slope * e.x + intercept - contentLeft).toInt()
                if (col < 0) col = 0 else if (col > lastCol) col = lastCol
                val y = e.y
                if (mDecStamp[col] != stamp) {
                    mDecStamp[col] = stamp
                    mDecMinY[col] = y
                    mDecMaxY[col] = y
                    mDecMinIdx[col] = i
                    mDecMaxIdx[col] = i
                } else {
                    if (y < mDecMinY[col]) {
                        mDecMinY[col] = y
                        mDecMinIdx[col] = i
                    }
                    if (y > mDecMaxY[col]) {
                        mDecMaxY[col] = y
                        mDecMaxIdx[col] = i
                    }
                }
            }
        } catch (e: IndexOutOfBoundsException) { //渲染中数据被删除,本帧放弃
            return
        }

        //生成折线:首entry → 每列(按源索引先后)min/max → 末entry
        val maxFloats = (2 * columns + 4) * 4
        if (mLineBuffer.size < maxFloats) {
            mLineBuffer = FloatArray(maxFloats)
        }
        val buf = mLineBuffer
        var j = 0
        var prevX = eFirst.x
        var prevY = eFirst.y * phaseY
        for (col in 0 until columns) {
            if (mDecStamp[col] != stamp) continue
            val iMin = mDecMinIdx[col]
            val iMax = mDecMaxIdx[col]
            val first: Int
            val second: Int
            if (iMin <= iMax) {
                first = iMin
                second = iMax
            } else {
                first = iMax
                second = iMin
            }
            var e = set.getEntryForIndex(first)
            buf[j++] = prevX
            buf[j++] = prevY
            buf[j++] = e.x
            buf[j++] = e.y * phaseY
            prevX = e.x
            prevY = e.y * phaseY
            if (second != first) {
                e = set.getEntryForIndex(second)
                buf[j++] = prevX
                buf[j++] = prevY
                buf[j++] = e.x
                buf[j++] = e.y * phaseY
                prevX = e.x
                prevY = e.y * phaseY
            }
        }
        if (prevX != eLast.x || prevY != eLast.y * phaseY) {
            buf[j++] = prevX
            buf[j++] = prevY
            buf[j++] = eLast.x
            buf[j++] = eLast.y * phaseY
        }
        if (j == 0) return
        trans.getValueToPixelMatrix().mapPoints(buf, 0, buf, 0, j shr 1)
        c.drawLines(buf, 0, j, mRenderPaint)
    }

    /**
     * 大量无序数据:只绘制至少一个端点在视口内的线段(以线段右端点索引去重)。
     * 注意:两端点都在视口外、但几何上穿越视口的线段会被省略
     */
    private fun drawUnsortedCulled(
        c: Canvas,
        set: XFreeLineDataSet<*>,
        trans: Transformer,
        phaseY: Float,
        order: IntArray,
        pLo: Int,
        pHi: Int,
        entryCount: Int
    ) {
        if (mSegmentStamp.size < entryCount) {
            mSegmentStamp = IntArray(entryCount * 2)
        }
        val stamps = mSegmentStamp
        val stamp = ++mStampCounter
        val maxFloats = (pHi - pLo) * 2 * 4
        if (mLineBuffer.size < maxFloats) {
            mLineBuffer = FloatArray(maxFloats)
        }
        val buf = mLineBuffer
        var j = 0
        try {
            for (p in pLo until pHi) {
                val i = order[p]
                if (i >= entryCount) continue
                val cur = set.getEntryForIndex(i)
                //右段(cur→next),next可能不在视口内
                val nextIdx = i + 1
                if (nextIdx < entryCount && stamps[nextIdx] != stamp) {
                    val next = set.getEntryForIndex(nextIdx)
                    buf[j++] = cur.x
                    buf[j++] = cur.y * phaseY
                    buf[j++] = next.x
                    buf[j++] = next.y * phaseY
                    stamps[nextIdx] = stamp
                }
                //左段(prev→cur),仅当prev不可见时补画
                if (i > 0 && stamps[i] != stamp) {
                    val prev = set.getEntryForIndex(i - 1)
                    buf[j++] = prev.x
                    buf[j++] = prev.y * phaseY
                    buf[j++] = cur.x
                    buf[j++] = cur.y * phaseY
                    stamps[i] = stamp
                }
            }
        } catch (e: IndexOutOfBoundsException) { //渲染中数据被删除,本帧放弃
            return
        }
        if (j == 0) return
        trans.getValueToPixelMatrix().mapPoints(buf, 0, buf, 0, j shr 1)
        c.drawLines(buf, 0, j, mRenderPaint)
    }

    /**
     * 旧实现:全量扫描所有entry绘制线段(非XFreeLineDataSet或少量无序数据时使用)
     */
    private fun drawDataSetFullScan(c: Canvas, dataSet: ILineDataSet, entryCount: Int) {
        val trans = mChart.getTransformer(dataSet.axisDependency)
        val phaseY = mAnimator.phaseY
        val pointsPerEntryPair = 2

        if (mLineBuffer.size < Math.max(entryCount * pointsPerEntryPair, pointsPerEntryPair) * 4) {
            mLineBuffer =
                FloatArray(Math.max(entryCount * pointsPerEntryPair, pointsPerEntryPair) * 4)
        }
        var j = 0
        for (i in 0 until entryCount) {
            val e1 = dataSet.safeGetEntryForIndex(if (i == 0) 0 else i - 1) ?: continue
            val e2 = dataSet.safeGetEntryForIndex(i) ?: continue

            mLineBuffer[j++] = e1.x
            mLineBuffer[j++] = e1.y * phaseY

            mLineBuffer[j++] = e2.x
            mLineBuffer[j++] = e2.y * phaseY
        }
        if (j > 0) { //需要绘制线条
            trans.pointValuesToPixel(mLineBuffer)

            mRenderPaint.color = dataSet.color

            c.drawLines(mLineBuffer, 0, j, mRenderPaint)
        }
    }

    override fun drawValues(c: Canvas?) {
    }

    override fun drawExtras(c: Canvas?) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        drawCircles(c)
        mLastCirclesDrawMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000f
        onFrameDrawTimeListener?.invoke(mLastLineDrawMs, mLastCirclesDrawMs)
    }

    /**
     * cache for the circle bitmaps of all datasets
     */
    private val mImageCaches = HashMap<IDataSet<*>, DataSetImageCache>()

    private fun drawCircles(c: Canvas?) {
        c ?: return
        mRenderPaint.style = Paint.Style.FILL

        val phaseY = mAnimator.phaseY

        val dataSets = mChart.lineData.dataSets
        for (dataSet in dataSets) {
            if (!dataSet.isVisible || !dataSet.isDrawCirclesEnabled || dataSet.entryCount == 0) continue
            mCirclePaintInner.color = dataSet.circleHoleColor

            val circleRadius = dataSet.circleRadius
            val circleHoleRadius = dataSet.circleHoleRadius
            val drawCircleHole =
                dataSet.isDrawCircleHoleEnabled && circleHoleRadius < circleRadius && circleHoleRadius > 0f
            val drawTransparentCircleHole =
                drawCircleHole && dataSet.circleHoleColor == ColorTemplate.COLOR_NONE

            var imageCache: DataSetImageCache

            if (mImageCaches.containsKey(dataSet)) {
                imageCache = mImageCaches.get(dataSet)!!
            } else {
                imageCache = DataSetImageCache(mRenderPaint, mCirclePaintInner)
                mImageCaches.put(dataSet, imageCache)
            }

            val changeRequired = imageCache.init(dataSet)

            // only fill the cache with new bitmaps if a change is required
            if (changeRequired) {
                imageCache.fill(dataSet, drawCircleHole, drawTransparentCircleHole)
            }

            val xfree = dataSet as? XFreeLineDataSet<*>
            if (xfree != null) {
                drawCirclesXFree(c, xfree, phaseY, imageCache, circleRadius)
            } else {
                drawCirclesFullScan(c, dataSet, phaseY, imageCache, circleRadius)
            }
        }
    }

    /**
     * 视口裁剪+批量变换绘制圆点。
     * 可见点数超过阈值(mPointVisibleThreshold>0时手动阈值,否则自动阈值)时不画点
     */
    private fun drawCirclesXFree(
        c: Canvas,
        set: XFreeLineDataSet<*>,
        phaseY: Float,
        imageCache: DataSetImageCache,
        circleRadius: Float
    ) {
        val entryCount = set.entryCount
        set.ensureXOrder()
        val order = set.xOrderIndices()
        val orderSize = minOf(set.xOrderCount(), entryCount)
        var pLo = set.lowerBoundInOrder(mChart.lowestVisibleX)
        var pHi = set.upperBoundInOrder(mChart.highestVisibleX)
        if (pLo > orderSize) pLo = orderSize
        if (pHi > orderSize) pHi = orderSize
        val visible = pHi - pLo
        if (visible <= 0) return
        val threshold = set.mPointVisibleThreshold
        val limit = if (threshold > 0) threshold else AUTO_MAX_VISIBLE_CIRCLES
        if (visible > limit) return

        val floats = visible * 2
        if (mCircleBuffer.size < floats) {
            mCircleBuffer = FloatArray(floats * 2)
            mCircleIndexBuffer = IntArray(floats)
        }
        val buf = mCircleBuffer
        val idxBuf = mCircleIndexBuffer
        var j = 0
        try {
            for (p in pLo until pHi) {
                val idx = order[p]
                if (idx >= entryCount) continue
                val e = set.getEntryForIndex(idx)
                buf[j] = e.x
                buf[j + 1] = e.y * phaseY
                idxBuf[j / 2] = idx
                j += 2
            }
        } catch (e: IndexOutOfBoundsException) { //渲染中数据被删除,本帧放弃
            return
        }
        if (j == 0) return
        val trans = mChart.getTransformer(set.axisDependency)
        trans.getValueToPixelMatrix().mapPoints(buf, 0, buf, 0, j shr 1)

        //mXOrder按x升序,变换后px单调,越出右边界即可停止
        for (i in 0 until j step 2) {
            val px = buf[i]
            if (!mViewPortHandler.isInBoundsRight(px)) break
            if (!mViewPortHandler.isInBoundsLeft(px) || !mViewPortHandler.isInBoundsY(buf[i + 1])) {
                continue
            }
            val circleBitmap = imageCache.getBitmap(idxBuf[i / 2])
            if (circleBitmap != null) {
                c.drawBitmap(circleBitmap, px - circleRadius, buf[i + 1] - circleRadius, null)
            }
        }
    }

    /**
     * 旧实现:全量扫描所有entry逐点变换绘制圆点(非XFreeLineDataSet时使用)
     */
    private fun drawCirclesFullScan(
        c: Canvas,
        dataSet: ILineDataSet,
        phaseY: Float,
        imageCache: DataSetImageCache,
        circleRadius: Float
    ) {
        val trans = mChart.getTransformer(dataSet.axisDependency)
        val buffer = FloatArray(2)
        val entryCount = dataSet.entryCount
        //计算屏幕中需要绘制的点
        for (j in 0 until entryCount) {
            val e = dataSet.safeGetEntryForIndex(j) ?: continue
            buffer[0] = e.x
            buffer[1] = e.y * phaseY
            trans.pointValuesToPixel(buffer)
            if (!mViewPortHandler.isInBoundsRight(buffer[0])) {
                continue
            }

            if (!mViewPortHandler.isInBoundsLeft(buffer[0]) ||
                !mViewPortHandler.isInBoundsY(buffer[1])
            ) {
                continue
            }
            val circleBitmap = imageCache.getBitmap(j)

            if (circleBitmap != null) {
                c.drawBitmap(
                    circleBitmap,
                    buffer[0] - circleRadius,
                    buffer[1] - circleRadius,
                    null
                )
            }
        }
    }

    override fun drawHighlighted(c: Canvas, indices: Array<out Highlight>) {
        val lineData = mChart.lineData

        for (high in indices) {
            val set = lineData.getDataSetByIndex(high.dataSetIndex)
            if (set == null || !set.isHighlightEnabled) continue
//            val e = set.getEntryForXValue(high.x, high.y) ?: continue
//            if (!isInBoundsX(e, set)) continue
            val pix = mChart.getTransformer(set.axisDependency).getPixelForValues(
                high.x, high.y * mAnimator.phaseY
            )
            high.setDraw(pix.x.toFloat(), pix.y.toFloat())

            // draw the lines
            drawHighlightLines(c, pix.x.toFloat(), pix.y.toFloat(), set)
        }
    }

    /**
     * path that is used for drawing highlight-lines (drawLines(...) cannot be used because of dashes)
     */
    private val mHighlightLinePath = Path()

    /**
     * Draws vertical & horizontal highlight-lines if enabled.
     *
     * @param c
     * @param x x-position of the highlight line intersection
     * @param y y-position of the highlight line intersection
     * @param set the currently drawn dataset
     */
    private fun drawHighlightLines(
        c: Canvas,
        x: Float,
        y: Float,
        set: ILineScatterCandleRadarDataSet<*>
    ) {

        // set color and stroke-width
        mHighlightPaint.color = set.highLightColor
        mHighlightPaint.strokeWidth = set.highlightLineWidth

        // draw highlighted lines (if enabled)
        mHighlightPaint.pathEffect = set.dashPathEffectHighlight

        // draw vertical highlight lines
        if (set.isVerticalHighlightIndicatorEnabled) {

            // create vertical path
            mHighlightLinePath.reset()
            mHighlightLinePath.moveTo(x, mViewPortHandler.contentTop())
            mHighlightLinePath.lineTo(x, mViewPortHandler.contentBottom())
            c.drawPath(mHighlightLinePath, mHighlightPaint)
        }

        // draw horizontal highlight lines
        if (set.isHorizontalHighlightIndicatorEnabled) {

            // create horizontal path
            mHighlightLinePath.reset()
            mHighlightLinePath.moveTo(mViewPortHandler.contentLeft(), y)
            mHighlightLinePath.lineTo(mViewPortHandler.contentRight(), y)
            c.drawPath(mHighlightLinePath, mHighlightPaint)
        }
    }


    fun releaseBitmap() {
        if (mBitmapCanvas != null) {
            mBitmapCanvas?.setBitmap(null)
            mBitmapCanvas = null
        }
        if (mDrawBitmap != null) {
            val drawBitmap = mDrawBitmap?.get()
            drawBitmap?.recycle()
            mDrawBitmap?.clear()
            mDrawBitmap = null
        }
    }
}

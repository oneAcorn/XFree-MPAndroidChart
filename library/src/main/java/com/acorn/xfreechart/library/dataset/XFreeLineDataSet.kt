package com.acorn.xfreechart.library.dataset

import com.acorn.xfreechart.library.extendfun.calculateDistanceOf2Points
import com.github.mikephil.charting.data.DataSet.Rounding
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider
import com.github.mikephil.charting.utils.MPPointD
import kotlin.math.abs

/**
 * Created by acorn on 2023/4/10.
 *
 * x轴不必递增的DataSet。
 *
 * 性能设计(兼容无序x):
 * - 维护按x升序的索引数组[mXOrder](值是mEntries中的下标),追加式插入O(1)增量维护,
 *   删除用原地压缩维护,乱序插入二分定位后搬移(过于频繁则标记失效,惰性重建);
 * - [mSortedByX]标记源数据是否恰好按x递增,渲染器据此走"精确裁剪+min-max抽稀"快车道,
 *   无序数据走"排序索引裁剪"路线,渲染成本只与屏幕内可见点数相关,与数据总量无关;
 * - min/max增量维护,仅删除/清空/整体替换后才全量重算(覆写[calcMinMax]),
 *   使逐条实时插入不再触发O(n)全量扫描。
 */
class XFreeLineDataSet<T : LineDataProvider>(
    private val mChart: T,
    yVals: MutableList<Entry>?,
    lable: String
) : LineDataSet(yVals, lable) {
    /**
     * The threshold for displaying points. When the number of points on the screen exceeds this value, the points will not show.
     * 小于等于0时为自动模式:可见点数超过[Companion.AUTO_MAX_VISIBLE_CIRCLES]时不画点
     */
    var mPointVisibleThreshold = -1

    companion object {
        /**
         * 两次索引重建之间允许的乱序(中部)插入次数上限,
         * 防止大量乱序批量插入时每次插入都O(n)搬移索引(此时改为惰性重建)
         */
        private const val MAX_MIDDLE_INSERTS_PER_BUILD = 256

        /**
         * 命中测试的初始像素窗口
         */
        private const val HIT_TEST_WINDOW_PX = 60f

        /**
         * 命中测试窗口内最多扫描的候选点数(超出则取靠近触摸x的一半区间)
         */
        private const val HIT_TEST_MAX_CANDIDATES = 8192
    }

    //注意:以下字段的"未初始化"缺省值必须落在保守(慢速)路径上,
    //因为父类构造函数会虚调用calcMinMax(),此时子类的属性初始化尚未执行
    /**
     * 按x升序排序的entry源索引;容量>=mXOrderSize,尾部为待复用空间
     */
    private var mXOrder = IntArray(0)
    private var mXOrderSize = 0
    private var mXOrderValid = false
    private var mSortedByX = false
    private var mStructureClean = false
    private var mMiddleInsertsSinceBuild = 0

    override fun calcMinMax() {
        if (mStructureClean) {
            //纯追加场景:min/max由calcMinMax(e)增量维护,无需全量扫描
            return
        }
        mStructureClean = true
        recomputeMinMaxAndSortedFlag()
        if (!mXOrderValid) {
            rebuildXOrder()
        }
    }

    /**
     * O(n)全量重算min/max,并顺带校验源数据是否按x递增
     */
    private fun recomputeMinMaxAndSortedFlag() {
        mYMax = -Float.MAX_VALUE
        mYMin = Float.MAX_VALUE
        mXMax = -Float.MAX_VALUE
        mXMin = Float.MAX_VALUE
        mSortedByX = true
        var prevX = Float.NEGATIVE_INFINITY
        val list = mEntries
        for (i in list.indices) {
            val e = list[i]
            val x = e.x
            val y = e.y
            if (x < mXMin) mXMin = x
            if (x > mXMax) mXMax = x
            if (y < mYMin) mYMin = y
            if (y > mYMax) mYMax = y
            if (x < prevX) mSortedByX = false
            prevX = x
        }
    }

    override fun addEntry(e: Entry?): Boolean {
        val added = super.addEntry(e)
        if (added && e != null) {
            onEntryInsertedAtEnd()
        }
        return added
    }

    override fun addEntryOrdered(e: Entry?) {
        if (e == null) return
        calcMinMax(e)
        if (mEntries.size > 0 && mEntries[mEntries.size - 1].x > e.x) {
            val closestIndex = getEntryIndex(e.x, e.y, Rounding.UP)
            mEntries.add(closestIndex, e)
            //中部插入使后续源索引整体位移,标记索引失效(下一帧惰性重建)
            mXOrderValid = false
        } else {
            mEntries.add(e)
            onEntryInsertedAtEnd()
        }
    }

    /**
     * mEntries末尾新增了一个entry后,增量维护有序标记与x排序索引
     */
    private fun onEntryInsertedAtEnd() {
        val n = mEntries.size
        val e = mEntries[n - 1]
        if (mSortedByX && n > 1 && mEntries[n - 2].x > e.x) {
            mSortedByX = false
        }
        if (!mXOrderValid) return
        if (mXOrderSize != n - 1) {
            //结构不一致(如外部直接改了entries列表),惰性重建
            mXOrderValid = false
            return
        }
        if (mXOrderSize == 0) {
            appendToOrder(0)
            return
        }
        val lastOrderedX = mEntries[mXOrder[mXOrderSize - 1]].x
        if (lastOrderedX <= e.x) {
            //x不小于当前最大x:追加到排序索引尾部,O(1)
            appendToOrder(n - 1)
        } else {
            //乱序插入:二分定位后搬移;过于频繁则放弃增量,等下次惰性重建
            if (mMiddleInsertsSinceBuild >= MAX_MIDDLE_INSERTS_PER_BUILD) {
                mXOrderValid = false
                return
            }
            insertIntoOrder(lowerBoundInOrder(e.x), n - 1)
            mMiddleInsertsSinceBuild++
        }
    }

    override fun removeEntry(e: Entry?): Boolean {
        if (e == null || mEntries.isEmpty()) return false
        val idx = mEntries.indexOf(e)
        if (idx < 0) return false
        mEntries.removeAt(idx)
        onEntryRemovedAt(idx)
        return true
    }

    override fun removeEntry(index: Int): Boolean {
        if (index < 0 || index >= mEntries.size) return false
        val e = mEntries[index]
        mEntries.removeAt(index)
        onEntryRemovedAt(index)
        return true
    }

    /**
     * 删除源索引[idx]后:压缩维护x排序索引(被删项丢弃,其后项前移),
     * min/max可能因极值被删而失真,标记脏并立即全量重算
     */
    private fun onEntryRemovedAt(idx: Int) {
        if (mXOrderValid) {
            var w = 0
            for (r in 0 until mXOrderSize) {
                val v = mXOrder[r]
                if (v == idx) continue
                mXOrder[w++] = if (v > idx) v - 1 else v
            }
            mXOrderSize = w
        }
        mStructureClean = false
        calcMinMax()
    }

    override fun clear() {
        mStructureClean = false
        mXOrderValid = false
        mXOrderSize = 0
        mSortedByX = true
        super.clear()
    }

    override fun setEntries(entries: MutableList<Entry>?) {
        mStructureClean = false
        mXOrderValid = false
        mXOrderSize = 0
        super.setEntries(entries)
    }

    override fun getEntryForXValue(xValue: Float, closestToY: Float, rounding: Rounding): Entry? {
        if (xValue.isNaN()) return null
        val index = getEntryIndex(xValue, closestToY, rounding)
        return if (index in 0 until mEntries.size) mEntries[index] else null
    }

    /**
     * Return the closest point value of the touched point
     *
     * @param xValue     x of touched point
     * @param closestToY the closest y of the touched point
     * @param rounding
     * @return 源索引
     */
    override fun getEntryIndex(xValue: Float, closestToY: Float, rounding: Rounding): Int {
        val n = mEntries.size
        if (n == 0 || xValue.isNaN()) return -1
        ensureXOrder()
        if (closestToY.isNaN()) {
            //纯x最近:在x排序索引上二分,等价于旧实现的全量|x-xValue|最小
            return nearestIndexByX(xValue)
        }
        //像素距离最近:自适应窗口扫描;窗口内最优距离小于窗口半径时结果必为全局最优
        val trans = mChart.getTransformer(axisDependency)
        val touch = trans.getPixelForValues(xValue, closestToY)
        val touchX = touch.x.toFloat()
        val touchY = touch.y.toFloat()
        MPPointD.recycleInstance(touch)
        var winPx = HIT_TEST_WINDOW_PX
        for (attempt in 0 until 3) {
            val r = scanWindowForNearest(xValue, touchX, touchY, winPx)
            if (r.first >= 0 && r.second <= r.third) {
                return r.first
            }
            winPx *= 16f
        }
        //极端分布兜底:全量精确扫描(与旧实现一致)
        return legacyNearestByPixelDistance(xValue, closestToY)
    }

    /**
     * 在触摸x的[winPx]像素窗口内寻找像素距离最近的点
     * @return (源索引, 最小像素距离, 已扫描区间对应的像素半径)
     */
    private fun scanWindowForNearest(
        xValue: Float,
        touchX: Float,
        touchY: Float,
        winPx: Float
    ): Triple<Int, Float, Float> {
        val order = mXOrder
        val size = mXOrderSize
        if (size == 0) return Triple(-1, Float.MAX_VALUE, 0f)
        val trans = mChart.getTransformer(axisDependency)
        val origin = trans.getPixelForValues(0f, 0f)
        val unit = trans.getPixelForValues(1f, 0f)
        val pxPerX = abs(unit.x - origin.x).toFloat()
        MPPointD.recycleInstance(origin)
        MPPointD.recycleInstance(unit)
        val xTol = if (pxPerX > 0f) winPx / pxPerX else Float.MAX_VALUE

        var pLo = lowerBoundInOrder(xValue - xTol)
        var pHi = upperBoundInOrder(xValue + xTol)
        val pMid = lowerBoundInOrder(xValue)
        if (pHi - pLo > HIT_TEST_MAX_CANDIDATES) {
            pLo = maxOf(pLo, pMid - HIT_TEST_MAX_CANDIDATES / 2)
            pHi = minOf(pHi, pMid + HIT_TEST_MAX_CANDIDATES / 2)
        }
        if (pHi <= pLo) return Triple(-1, Float.MAX_VALUE, 0f)
        val count = pHi - pLo
        val scannedHalfPx = if (pxPerX > 0f) {
            abs(mEntries[order[pHi - 1]].x - mEntries[order[pLo]].x) / 2f * pxPerX
        } else {
            winPx
        }
        val buf = FloatArray(count * 2)
        var j = 0
        for (p in pLo until pHi) {
            val idx = order[p]
            if (idx >= mEntries.size) {
                //并发删除导致索引越界:占位NaN保持缓冲与pLo的对齐关系(NaN距离永远不会成为最优)
                buf[j++] = Float.NaN
                buf[j++] = Float.NaN
                continue
            }
            val e = mEntries[idx]
            buf[j++] = e.x
            buf[j++] = e.y
        }
        if (j == 0) return Triple(-1, Float.MAX_VALUE, scannedHalfPx)
        trans.getValueToPixelMatrix().mapPoints(buf, 0, buf, 0, j shr 1)
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        for (i in 0 until j step 2) {
            val d = calculateDistanceOf2Points(
                buf[i].toDouble(), buf[i + 1].toDouble(),
                touchX.toDouble(), touchY.toDouble()
            )
            if (d < bestDist) {
                bestDist = d
                bestIdx = order[pLo + i / 2]
            }
        }
        return Triple(bestIdx, bestDist, scannedHalfPx)
    }

    /**
     * 纯x最近:在x排序索引上二分,取左右邻近两个候选中|x-xValue|较小者,
     * 等价于旧实现对全部entry求|x-xValue|最小
     */
    private fun nearestIndexByX(xValue: Float): Int {
        val order = mXOrder
        val size = mXOrderSize
        if (size == 0) return -1
        val pos = lowerBoundInOrder(xValue)
        val hiPos = if (pos < size) pos else size - 1
        val loPos = if (pos > 0) pos - 1 else 0
        val dHi = abs(mEntries[order[hiPos]].x - xValue)
        val dLo = abs(mEntries[order[loPos]].x - xValue)
        return if (dHi < dLo) order[hiPos] else order[loPos]
    }

    /**
     * 旧实现的O(n)全量像素距离扫描,兜底用
     */
    private fun legacyNearestByPixelDistance(xValue: Float, closestToY: Float): Int {
        var index: Int = -1
        var minDistance = Float.NaN
        val touchPoint = mChart.getTransformer(axisDependency).getPixelForValues(xValue, closestToY)
        val touchPixelX = touchPoint.x
        val touchPixelY = touchPoint.y
        MPPointD.recycleInstance(touchPoint)
        try { //处理ConcurrentModificationException性能消耗太大,此方法只是UI显示,所以不做处理(不想try catch可以用CopyOnWriteArrayList)
            for ((i, e) in mEntries.withIndex()) {
                val entryPoint =
                    mChart.getTransformer(axisDependency).getPixelForValues(e.x, e.y)
                val entryPixelX = entryPoint.x
                val entryPixelY = entryPoint.y
                MPPointD.recycleInstance(entryPoint)
                val distance = calculateDistanceOf2Points(
                    entryPixelX,
                    entryPixelY,
                    touchPixelX,
                    touchPixelY
                )
                if (minDistance.isNaN() || distance < minDistance) {
                    minDistance = distance
                    index = i
                }
            }
        } catch (e: ConcurrentModificationException) {
            index = -1
            e.printStackTrace()
        }
        return index
    }

    /**
     * Return all entries that the x-Axis equals xValue
     */
    override fun getEntriesForXValue(xValue: Float): MutableList<Entry> {
        val targetEntries = mutableListOf<Entry>()
        val n = mEntries.size
        if (n == 0 || xValue.isNaN()) return targetEntries
        ensureXOrder()
        val pEnd = upperBoundInOrder(xValue)
        var p = lowerBoundInOrder(xValue)
        val order = mXOrder
        while (p < pEnd) {
            val idx = order[p]
            if (idx < mEntries.size) {
                targetEntries.add(mEntries[idx])
            }
            p++
        }
        return targetEntries
    }

    // region 供渲染器使用的x排序索引

    internal fun isSortedByX(): Boolean = mSortedByX

    internal fun xOrderIndices(): IntArray = mXOrder

    internal fun xOrderCount(): Int = mXOrderSize

    /**
     * 确保x排序索引可用(失效时重建,均摊到每次结构性变更后一次)
     */
    internal fun ensureXOrder() {
        if (!mXOrderValid || mXOrderSize != mEntries.size) {
            rebuildXOrder()
        }
    }

    /**
     * mXOrder中第一个x >= [x]的位置(上界为mXOrderSize)
     */
    internal fun lowerBoundInOrder(x: Float): Int {
        var lo = 0
        var hi = mXOrderSize
        val list = mEntries
        val order = mXOrder
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[order[mid]].x < x) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * mXOrder中第一个x > [x]的位置(上界为mXOrderSize)
     */
    internal fun upperBoundInOrder(x: Float): Int {
        var lo = 0
        var hi = mXOrderSize
        val list = mEntries
        val order = mXOrder
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (list[order[mid]].x <= x) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * 重建x排序索引:有序数据直接生成恒等索引O(n),无序数据归并排序O(n log n)
     */
    private fun rebuildXOrder() {
        val list = mEntries
        val n = list.size
        mMiddleInsertsSinceBuild = 0
        if (n == 0) {
            mXOrder = IntArray(0)
            mXOrderSize = 0
            mXOrderValid = true
            return
        }
        if (mSortedByX) {
            mXOrder = IntArray(n) { it }
        } else {
            val order = IntArray(n) { it }
            val keys = FloatArray(n) { list[it].x }
            mergeSortByX(order, keys, n)
            mXOrder = order
        }
        mXOrderSize = n
        mXOrderValid = true
    }

    private fun appendToOrder(idx: Int) {
        var order = mXOrder
        if (order.size == mXOrderSize) {
            val newCap = (mXOrderSize * 3 / 2).coerceAtLeast(16)
            order = order.copyOf(newCap)
            mXOrder = order
        }
        order[mXOrderSize++] = idx
    }

    private fun insertIntoOrder(pos: Int, idx: Int) {
        var order = mXOrder
        if (order.size == mXOrderSize) {
            val newCap = (mXOrderSize * 3 / 2).coerceAtLeast(16)
            order = order.copyOf(newCap)
            mXOrder = order
        }
        System.arraycopy(order, pos, order, pos + 1, mXOrderSize - pos)
        order[pos] = idx
        mXOrderSize++
    }

    /**
     * 稳定归并排序:按keys把order(0..n-1)升序排列,自底向上两路归并,无装箱
     */
    private fun mergeSortByX(order: IntArray, keys: FloatArray, n: Int) {
        if (n < 2) return
        val tmpOrder = IntArray(n)
        val tmpKeys = FloatArray(n)
        var srcOrder = order
        var srcKeys = keys
        var dstOrder = tmpOrder
        var dstKeys = tmpKeys
        var width = 1
        while (width < n) {
            var i = 0
            while (i < n) {
                val mid = minOf(i + width, n)
                val end = minOf(i + 2 * width, n)
                var l = i
                var r = mid
                var k = i
                while (l < mid && r < end) {
                    if (srcKeys[r] < srcKeys[l]) {
                        dstKeys[k] = srcKeys[r]; dstOrder[k] = srcOrder[r]; r++
                    } else {
                        dstKeys[k] = srcKeys[l]; dstOrder[k] = srcOrder[l]; l++
                    }
                    k++
                }
                while (l < mid) {
                    dstKeys[k] = srcKeys[l]; dstOrder[k] = srcOrder[l]; l++; k++
                }
                while (r < end) {
                    dstKeys[k] = srcKeys[r]; dstOrder[k] = srcOrder[r]; r++; k++
                }
                i = end
            }
            var t = srcOrder; srcOrder = dstOrder; dstOrder = t
            val tk = srcKeys; srcKeys = dstKeys; dstKeys = tk
            width = width shl 1
        }
        if (srcOrder !== order) {
            System.arraycopy(srcOrder, 0, order, 0, n)
        }
    }
    // endregion
}

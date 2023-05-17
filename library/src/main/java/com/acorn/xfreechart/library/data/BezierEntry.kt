package com.acorn.xfreechart.library.data

import com.github.mikephil.charting.data.Entry

/**
 * Created by acorn on 2023/5/8.
 */

/**
 * Bezier entry
 *
 * @property p1 起始点
 * @property h1 控制点1 若为空则为p1到p2的直线
 * @property h2 控制点2(若为空则为二阶贝塞尔,非空为三阶贝塞尔
 * @property p2 结束点
 * @constructor Create empty Bezier entry
 */
data class BezierEntry(val p1: Entry, val h1: Entry?, val h2: Entry?, val p2: Entry)
package com.acorn.xfreechart.library.data

import com.acorn.xfreechart.library.dataset.BezierDataSet

/**
 * Created by acorn on 2023/5/11.
 */
class BezierData {
    private val mDataSets by lazy { mutableListOf<BezierDataSet>() }

    fun addDataSet(dataSet: BezierDataSet) {
        mDataSets.add(dataSet)
    }

    fun removeDataSet(dataSet: BezierDataSet) {
        mDataSets.remove(dataSet)
    }

    fun getDataSet(index: Int): BezierDataSet? {
        if (index >= mDataSets.size) return null
        return mDataSets[index]
    }

    fun getDataSets(): List<BezierDataSet> {
        return mDataSets
    }

    fun addEntry(setIndex: Int, entry: BezierEntry) {
        if (setIndex >= mDataSets.size) return
        mDataSets[setIndex].mEntries.add(entry)
    }

    fun removeEntry(setIndex: Int, entry: BezierEntry) {
        if (setIndex >= mDataSets.size) return
        mDataSets[setIndex].mEntries.remove(entry)
    }
}
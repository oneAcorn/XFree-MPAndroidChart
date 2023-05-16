package com.acorn.xfreechart.library.data

/**
 * Created by acorn on 2023/5/16.
 */
class FixedMarkerData {
    private val _markers by lazy { mutableListOf<FixedMarkerEntry>() }

    fun addMarker(marker: FixedMarkerEntry) {
        _markers.add(marker)
    }

    fun removeMarker(id: Int) {
        val iterator = _markers.iterator()
        while (iterator.hasNext()) {
            val marker = iterator.next()
            if (marker.id == id) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        _markers.clear()
    }

    fun getMarkers(): List<FixedMarkerEntry> = _markers
}
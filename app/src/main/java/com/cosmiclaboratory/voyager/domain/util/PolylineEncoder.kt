package com.cosmiclaboratory.voyager.domain.util

object PolylineEncoder {

    fun encode(points: List<Pair<Double, Double>>): String {
        val result = StringBuilder()
        var prevLat = 0
        var prevLng = 0

        for ((lat, lng) in points) {
            val iLat = Math.round(lat * 1e5).toInt()
            val iLng = Math.round(lng * 1e5).toInt()
            encodeValue(iLat - prevLat, result)
            encodeValue(iLng - prevLng, result)
            prevLat = iLat
            prevLng = iLng
        }
        return result.toString()
    }

    fun decode(encoded: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            val (dLat, newIdx1) = decodeValue(encoded, index)
            index = newIdx1
            val (dLng, newIdx2) = decodeValue(encoded, index)
            index = newIdx2
            lat += dLat
            lng += dLng
            result.add(Pair(lat / 1e5, lng / 1e5))
        }
        return result
    }

    private fun encodeValue(value: Int, result: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            result.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        result.append((v + 63).toChar())
    }

    fun mergePolylines(polylines: List<String>): String {
        val allPoints = polylines.flatMap { decode(it) }
        return if (allPoints.isEmpty()) "" else encode(allPoints)
    }

    private fun decodeValue(encoded: String, startIndex: Int): Pair<Int, Int> {
        var index = startIndex
        var result = 0
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        return Pair(if (result and 1 != 0) (result shr 1).inv() else result shr 1, index)
    }
}

package com.cosmiclaboratory.voyager.data.repository

/**
 * Google-encoded-polyline codec (precision 1e-5).
 *
 * Extracted from [ExportRepositoryImpl] so the encode/decode round trip — used both
 * to render GPX/GeoJSON geometry and to round route coordinates for privacy-stripped
 * exports — is unit-testable without the Android-bound repository.
 */
internal object PolylineCodec {

    /** Decodes an encoded polyline to a list of (lat, lng) pairs. */
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var b: Int
            var value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1F) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (value and 1 != 0) (value shr 1).inv() else value shr 1

            shift = 0
            value = 0
            do {
                b = encoded[index++].code - 63
                value = value or ((b and 0x1F) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (value and 1 != 0) (value shr 1).inv() else value shr 1

            result.add(lat / 1e5 to lng / 1e5)
        }
        return result
    }

    /** Encodes (lat, lng) pairs to an encoded polyline — inverse of [decode]. */
    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for ((lat, lng) in points) {
            val latE5 = Math.round(lat * 1e5)
            val lngE5 = Math.round(lng * 1e5)
            encodeValue(latE5 - prevLat, sb)
            encodeValue(lngE5 - prevLng, sb)
            prevLat = latE5
            prevLng = lngE5
        }
        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            sb.append(((0x20 or (v.toInt() and 0x1F)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}

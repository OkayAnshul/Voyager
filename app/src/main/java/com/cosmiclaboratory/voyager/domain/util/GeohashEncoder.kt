package com.cosmiclaboratory.voyager.domain.util

object GeohashEncoder {

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val DEFAULT_PRECISION = 7

    fun encode(lat: Double, lng: Double, precision: Int = DEFAULT_PRECISION): String {
        var minLat = -90.0; var maxLat = 90.0
        var minLng = -180.0; var maxLng = 180.0
        var isLng = true
        var bit = 0
        var ch = 0
        val geohash = StringBuilder()

        while (geohash.length < precision) {
            if (isLng) {
                val mid = (minLng + maxLng) / 2
                if (lng >= mid) { ch = ch or (1 shl (4 - bit)); minLng = mid } else { maxLng = mid }
            } else {
                val mid = (minLat + maxLat) / 2
                if (lat >= mid) { ch = ch or (1 shl (4 - bit)); minLat = mid } else { maxLat = mid }
            }
            isLng = !isLng
            if (bit < 4) { bit++ } else {
                geohash.append(BASE32[ch])
                bit = 0; ch = 0
            }
        }
        return geohash.toString()
    }

    fun decode(geohash: String): Pair<Double, Double> {
        var minLat = -90.0; var maxLat = 90.0
        var minLng = -180.0; var maxLng = 180.0
        var isLng = true

        for (c in geohash) {
            val cd = BASE32.indexOf(c)
            for (bit in 4 downTo 0) {
                if (isLng) {
                    val mid = (minLng + maxLng) / 2
                    if (cd and (1 shl bit) != 0) minLng = mid else maxLng = mid
                } else {
                    val mid = (minLat + maxLat) / 2
                    if (cd and (1 shl bit) != 0) minLat = mid else maxLat = mid
                }
                isLng = !isLng
            }
        }
        return Pair((minLat + maxLat) / 2, (minLng + maxLng) / 2)
    }

    fun neighbors(geohash: String): List<String> {
        val (lat, lng) = decode(geohash)
        val latErr = 180.0 / (1 shl (geohash.length * 5 / 2))
        val lngErr = 360.0 / (1 shl ((geohash.length * 5 + 1) / 2))
        return listOf(
            encode(lat + latErr, lng - lngErr, geohash.length),
            encode(lat + latErr, lng, geohash.length),
            encode(lat + latErr, lng + lngErr, geohash.length),
            encode(lat, lng - lngErr, geohash.length),
            encode(lat, lng + lngErr, geohash.length),
            encode(lat - latErr, lng - lngErr, geohash.length),
            encode(lat - latErr, lng, geohash.length),
            encode(lat - latErr, lng + lngErr, geohash.length)
        )
    }
}

package com.cosmiclaboratory.voyager.storage.database.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceDisplayNameTest {

    private fun place(userName: String? = null, providerName: String? = null) = PlaceEntity(
        centroidLat = 37.7749, centroidLng = -122.4194, geohash = "9q8y", createdAt = 0L,
        userDisplayName = userName, bestProviderName = providerName
    )

    @Test
    fun `a user-set name always wins`() {
        assertEquals("Home", place(userName = "Home", providerName = "123 Main St").displayName())
    }

    @Test
    fun `the best provider name is used when there is no user name`() {
        assertEquals("123 Main St", place(providerName = "123 Main St").displayName())
    }

    @Test
    fun `falls back to coordinates when nothing has been geocoded`() {
        assertEquals("37.7749, -122.4194", place().displayName())
    }
}

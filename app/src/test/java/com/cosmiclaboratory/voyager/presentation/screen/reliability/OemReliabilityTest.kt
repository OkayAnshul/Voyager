package com.cosmiclaboratory.voyager.presentation.screen.reliability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemReliabilityTest {

    @Test
    fun `known aggressive OEMs are detected, case-insensitively`() {
        assertTrue(OemReliability.isAggressive("samsung"))
        assertTrue(OemReliability.isAggressive("Xiaomi"))
        assertTrue(OemReliability.isAggressive("OnePlus"))
    }

    @Test
    fun `non-aggressive OEMs are not flagged`() {
        assertFalse(OemReliability.isAggressive("Google"))
        assertFalse(OemReliability.isAggressive("Fairphone"))
    }

    @Test
    fun `aggressive OEM deep-links to its device-specific guide`() {
        assertEquals("https://dontkillmyapp.com/samsung", OemReliability.dontKillMyAppUrl("Samsung"))
        assertEquals("https://dontkillmyapp.com/oneplus", OemReliability.dontKillMyAppUrl("OnePlus"))
    }

    @Test
    fun `sub-brands map to the parent guide`() {
        assertEquals("https://dontkillmyapp.com/xiaomi", OemReliability.dontKillMyAppUrl("Redmi"))
        assertEquals("https://dontkillmyapp.com/xiaomi", OemReliability.dontKillMyAppUrl("POCO"))
    }

    @Test
    fun `unknown OEM falls back to the homepage`() {
        assertEquals("https://dontkillmyapp.com/", OemReliability.dontKillMyAppUrl("Google"))
    }
}

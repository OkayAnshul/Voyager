package com.cosmiclaboratory.voyager.pipeline

import com.cosmiclaboratory.voyager.data.geocoding.PoiCategoryMapper
import com.cosmiclaboratory.voyager.domain.model.PlaceCategory
import org.junit.Assert.*
import org.junit.Test

class PoiCategoryMapperTest {

    @Test
    fun `amenity cafe maps to RESTAURANT`() {
        assertEquals(PlaceCategory.RESTAURANT, PoiCategoryMapper.fromOsmType("amenity=cafe"))
    }

    @Test
    fun `amenity restaurant maps to RESTAURANT`() {
        assertEquals(PlaceCategory.RESTAURANT, PoiCategoryMapper.fromOsmType("amenity=restaurant"))
    }

    @Test
    fun `amenity gym maps to GYM`() {
        assertEquals(PlaceCategory.GYM, PoiCategoryMapper.fromOsmType("amenity=gym"))
    }

    @Test
    fun `amenity hospital maps to HEALTHCARE`() {
        assertEquals(PlaceCategory.HEALTHCARE, PoiCategoryMapper.fromOsmType("amenity=hospital"))
    }

    @Test
    fun `amenity school maps to EDUCATION`() {
        assertEquals(PlaceCategory.EDUCATION, PoiCategoryMapper.fromOsmType("amenity=school"))
    }

    @Test
    fun `amenity cinema maps to ENTERTAINMENT`() {
        assertEquals(PlaceCategory.ENTERTAINMENT, PoiCategoryMapper.fromOsmType("amenity=cinema"))
    }

    @Test
    fun `shop supermarket maps to SHOPPING`() {
        assertEquals(PlaceCategory.SHOPPING, PoiCategoryMapper.fromOsmType("shop=supermarket"))
    }

    @Test
    fun `any shop value maps to SHOPPING`() {
        assertEquals(PlaceCategory.SHOPPING, PoiCategoryMapper.fromOsmType("shop=bakery"))
        assertEquals(PlaceCategory.SHOPPING, PoiCategoryMapper.fromOsmType("shop=electronics"))
    }

    @Test
    fun `leisure park maps to OUTDOOR`() {
        assertEquals(PlaceCategory.OUTDOOR, PoiCategoryMapper.fromOsmType("leisure=park"))
    }

    @Test
    fun `tourism hotel maps to TRAVEL`() {
        assertEquals(PlaceCategory.TRAVEL, PoiCategoryMapper.fromOsmType("tourism=hotel"))
    }

    @Test
    fun `tourism museum maps to ENTERTAINMENT`() {
        assertEquals(PlaceCategory.ENTERTAINMENT, PoiCategoryMapper.fromOsmType("tourism=museum"))
    }

    @Test
    fun `railway station maps to TRANSIT_HUB`() {
        assertEquals(PlaceCategory.TRANSIT_HUB, PoiCategoryMapper.fromOsmType("railway=station"))
    }

    @Test
    fun `aeroway aerodrome maps to TRANSIT_HUB`() {
        assertEquals(PlaceCategory.TRANSIT_HUB, PoiCategoryMapper.fromOsmType("aeroway=aerodrome"))
    }

    @Test
    fun `office is ambiguous and maps to null`() {
        // office=* could be a coworking space, NGO, lawyer, government — we don't guess WORK.
        assertNull(PoiCategoryMapper.fromOsmType("office=company"))
        assertNull(PoiCategoryMapper.fromOsmType("office=lawyer"))
    }

    @Test
    fun `building tag is ambiguous and maps to null`() {
        assertNull(PoiCategoryMapper.fromOsmType("building=yes"))
        assertNull(PoiCategoryMapper.fromOsmType("building=residential"))
    }

    @Test
    fun `unknown amenity maps to null`() {
        assertNull(PoiCategoryMapper.fromOsmType("amenity=parking"))
    }

    @Test
    fun `malformed input maps to null`() {
        assertNull(PoiCategoryMapper.fromOsmType(null))
        assertNull(PoiCategoryMapper.fromOsmType(""))
        assertNull(PoiCategoryMapper.fromOsmType("amenity"))
        assertNull(PoiCategoryMapper.fromOsmType("="))
    }

    @Test
    fun `mixed case input still matches`() {
        assertEquals(PlaceCategory.RESTAURANT, PoiCategoryMapper.fromOsmType("Amenity=Cafe"))
    }
}

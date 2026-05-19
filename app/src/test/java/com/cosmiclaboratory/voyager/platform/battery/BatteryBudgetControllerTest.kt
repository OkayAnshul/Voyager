package com.cosmiclaboratory.voyager.platform.battery

import com.cosmiclaboratory.voyager.domain.model.enums.TrackingTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the battery-budget controller recommends downgrades correctly. */
class BatteryBudgetControllerTest {

    private val controller = BatteryBudgetController()

    @Test
    fun `no budget set returns no recommendation`() {
        assertNull(controller.recommendDowngrade(50, budgetPctPerDay = 0, TrackingTier.ACCURATE))
    }

    @Test
    fun `no measurement yet returns no recommendation`() {
        assertNull(controller.recommendDowngrade(null, budgetPctPerDay = 5, TrackingTier.BALANCED))
    }

    @Test
    fun `within budget returns no recommendation`() {
        assertNull(controller.recommendDowngrade(4, budgetPctPerDay = 5, TrackingTier.BALANCED))
        // Exactly on budget is still within budget.
        assertNull(controller.recommendDowngrade(5, budgetPctPerDay = 5, TrackingTier.BALANCED))
    }

    @Test
    fun `over budget steps one tier down`() {
        assertEquals(
            TrackingTier.PASSIVE,
            controller.recommendDowngrade(9, budgetPctPerDay = 5, TrackingTier.BALANCED)
        )
        assertEquals(
            TrackingTier.BALANCED,
            controller.recommendDowngrade(12, budgetPctPerDay = 5, TrackingTier.ACCURATE)
        )
    }

    @Test
    fun `over budget at the floor tier returns no recommendation`() {
        assertNull(controller.recommendDowngrade(9, budgetPctPerDay = 5, TrackingTier.PASSIVE))
    }

    @Test
    fun `non-background tiers are not governed by the budget`() {
        assertNull(controller.recommendDowngrade(99, budgetPctPerDay = 5, TrackingTier.OFF))
        assertNull(controller.recommendDowngrade(99, budgetPctPerDay = 5, TrackingTier.WORKOUT))
    }
}

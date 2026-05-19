package com.cosmiclaboratory.voyager.platform.battery

import com.cosmiclaboratory.voyager.domain.model.enums.TrackingTier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether the tracking tier should step down to honour the user's
 * battery budget.
 *
 * Pure, deterministic logic — it never applies a change itself. The caller
 * (a worker, or the UI) reads [recommendDowngrade], surfaces it to the user,
 * and applies it. Keeping it pure makes it trivially testable and means the
 * tier is never changed silently behind the user's back.
 *
 * It only ever recommends *downgrades*: a budget that auto-upgraded would
 * fight the user's explicit choice. Re-raising the tier is a user action.
 */
@Singleton
class BatteryBudgetController @Inject constructor() {

    /** Background tiers ordered cheapest → most expensive. */
    private val ladder = listOf(
        TrackingTier.PASSIVE,
        TrackingTier.BALANCED,
        TrackingTier.ACCURATE
    )

    /**
     * Given the measured whole-day discharge and the user's budget, returns the
     * next tier down if the current tier is over budget — or `null` when no
     * change is warranted.
     *
     * Returns `null` when: the budget feature is off ([budgetPctPerDay] <= 0);
     * there is no measurement yet; discharge is within budget; or the current
     * tier is already at the floor (PASSIVE) or is a non-background tier
     * (OFF / WORKOUT, which the budget does not govern).
     *
     * @param budgetPctPerDay user's ceiling; 0 means the feature is off.
     */
    fun recommendDowngrade(
        measuredPctPerDay: Int?,
        budgetPctPerDay: Int,
        currentTier: TrackingTier
    ): TrackingTier? {
        if (budgetPctPerDay <= 0) return null
        if (measuredPctPerDay == null) return null
        if (measuredPctPerDay <= budgetPctPerDay) return null
        val index = ladder.indexOf(currentTier)
        if (index <= 0) return null
        return ladder[index - 1]
    }
}

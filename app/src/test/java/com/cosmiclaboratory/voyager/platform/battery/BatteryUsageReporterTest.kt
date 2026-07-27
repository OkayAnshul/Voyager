package com.cosmiclaboratory.voyager.platform.battery

import com.cosmiclaboratory.voyager.storage.database.dao.RawLocationSampleDao
import com.cosmiclaboratory.voyager.storage.database.entity.RawLocationSampleEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tests [BatteryUsageReporter]: the honest whole-device discharge estimate.
 *
 * The core contract under test is that [BatteryUsageReporter.Estimate.percentPerDay]
 * is **withheld (null) until the measurement is trustworthy** — battery is reported
 * in whole percent, so a short window would otherwise extrapolate a single 1% tick
 * into a saturated 100%/day. `measuredOverHours` is always reported as a progress proxy.
 */
class BatteryUsageReporterTest {

    private val dao = mockk<RawLocationSampleDao>()
    private val reporter = BatteryUsageReporter(dao)

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000

    /** Minimal valid row — only the fields the reporter reads carry meaning. */
    private fun sample(atMs: Long, pct: Int?, charging: Boolean = false) = RawLocationSampleEntity(
        capturedAt = atMs,
        receivedAt = atMs,
        lat = 0.0,
        lng = 0.0,
        accuracyM = 5f,
        provider = "fused",
        batteryPct = pct,
        isCharging = charging,
        permissionSnapshot = "fine",
        trackingSessionId = 1L,
        localTimeZone = "UTC",
        geohash = "gcpuv"
    )

    private fun givenSamples(vararg rows: RawLocationSampleEntity) {
        coEvery { dao.getByTimeRange(any(), any()) } returns rows.toList()
    }

    @Test
    fun `fewer than two battery-stamped samples yields null`() = runTest {
        givenSamples(sample(now - hour, pct = null), sample(now, pct = 80))
        val estimate = reporter.estimate(now)
        assertThat(estimate.percentPerDay).isNull()
        assertThat(estimate.measuredOverHours).isEqualTo(0)
    }

    @Test
    fun `charging throughout yields null`() = runTest {
        givenSamples(
            sample(now - 8 * hour, pct = 90, charging = true),
            sample(now - 4 * hour, pct = 95, charging = true),
            sample(now, pct = 100, charging = true)
        )
        assertThat(reporter.estimate(now).percentPerDay).isNull()
    }

    @Test
    fun `a single one-percent tick over a short window is withheld, never saturated to 100`() = runTest {
        // The original bug: 1% over 20 min extrapolates to ~72-144%/day and clamps to 100.
        givenSamples(
            sample(now - 20 * 60 * 1000, pct = 80),
            sample(now, pct = 79)
        )
        val estimate = reporter.estimate(now)
        assertThat(estimate.percentPerDay).isNull()
    }

    @Test
    fun `enough time and drop produces a plausible per-day figure`() = runTest {
        // 8% over 8h of discharge == 24%/day.
        givenSamples(
            sample(now - 8 * hour, pct = 88),
            sample(now, pct = 80)
        )
        assertThat(reporter.estimate(now).percentPerDay).isEqualTo(24)
    }

    @Test
    fun `a glitchy spike interval is ignored while surrounding discharge is counted`() = runTest {
        // 8% over 8h is genuine; the final pair is a 50% collapse over 10s (calibration
        // glitch) that must be rejected by the rate filter, leaving the 24%/day figure intact.
        givenSamples(
            sample(now - 8 * hour - 10_000, pct = 88),
            sample(now - 10_000, pct = 80),
            sample(now, pct = 30)
        )
        assertThat(reporter.estimate(now).percentPerDay).isEqualTo(24)
    }

    @Test
    fun `a large but slow drop over a long gap is counted`() = runTest {
        // 30% over 6h == 5%/h == 120%/day raw -> exceeds the plausibility ceiling -> withheld,
        // but the interval itself must NOT be discarded the way the old absolute-40 filter did.
        // Use a gentler slope that stays plausible: 24% over 12h == 48%/day.
        givenSamples(
            sample(now - 12 * hour, pct = 84),
            sample(now, pct = 60)
        )
        val estimate = reporter.estimate(now)
        assertThat(estimate.percentPerDay).isEqualTo(48)
        assertThat(estimate.measuredOverHours).isEqualTo(12)
    }

    @Test
    fun `an implausibly high result is withheld`() = runTest {
        // 40% over 6h == 6.66%/h (passes the per-interval rate filter) == 160%/day -> null.
        givenSamples(
            sample(now - 6 * hour, pct = 90),
            sample(now, pct = 50)
        )
        assertThat(reporter.estimate(now).percentPerDay).isNull()
    }

    @Test
    fun `measuredOverHours reflects real discharge time even when the figure is withheld`() = runTest {
        // 3% over 7h: enough hours but below the 5% drop floor -> number withheld, hours reported.
        givenSamples(
            sample(now - 7 * hour, pct = 83),
            sample(now, pct = 80)
        )
        val estimate = reporter.estimate(now)
        assertThat(estimate.percentPerDay).isNull()
        assertThat(estimate.measuredOverHours).isEqualTo(7)
    }
}

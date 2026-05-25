package com.cosmiclaboratory.voyager.domain.model.ids

/**
 * Typed identifiers for the core entities.
 *
 * `@JvmInline value class` wraps a `Long` with zero runtime cost (it inlines to a
 * plain long), but gives compile-time safety: a [VisitId] can never be passed where
 * a [PlaceId] is expected. Per the hardening audit (A8), these are adopted
 * gradually starting at the repository boundary; the DB layer keeps raw `Long`s,
 * so repositories convert with [raw] / the `as…Id()` helpers.
 */
@JvmInline value class PlaceId(val raw: Long)

@JvmInline value class VisitId(val raw: Long)

@JvmInline value class SegmentId(val raw: Long)

@JvmInline value class RouteId(val raw: Long)

fun Long.asPlaceId(): PlaceId = PlaceId(this)
fun Long.asVisitId(): VisitId = VisitId(this)
fun Long.asSegmentId(): SegmentId = SegmentId(this)
fun Long.asRouteId(): RouteId = RouteId(this)

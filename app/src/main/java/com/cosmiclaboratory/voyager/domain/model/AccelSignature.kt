package com.cosmiclaboratory.voyager.domain.model

/**
 * What the accelerometer says the body is doing, independent of GPS speed — the C2 signal that
 * disambiguates cases speed + activity-recognition confuse.
 *
 * - [STILL]: near-zero motion variance (sitting/standing still).
 * - [SMOOTH_MOTION]: low body-motion variance — consistent with *riding* (car/bus/train/bike on
 *   a smooth road) where the device moves but the body isn't striding. Paired with GPS speed,
 *   this is the tell that "moving but not on foot" → vehicle/transit, not walking/cycling effort.
 * - [ON_FOOT]: high, rhythmic variance from striding — walking or running.
 */
enum class AccelSignature { STILL, SMOOTH_MOTION, ON_FOOT }

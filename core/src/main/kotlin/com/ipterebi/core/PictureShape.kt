package com.ipterebi.core

import kotlin.math.roundToInt

/** A width-to-height ratio as whole numbers, which is what Android's `Rational` takes. */
data class AspectRatio(val numerator: Int, val denominator: Int)

/**
 * The shape of a video, for the picture-in-picture window it will play in, or
 * null when the stream has not said yet.
 *
 * [pixelWidthHeightRatio] is there because broadcast video is often anamorphic:
 * 720x576 stored, 16:9 shown. Taking the stored size at its word gives a window
 * that is nearly square around a picture that is not.
 *
 * Clamped, because Android refuses a picture-in-picture window thinner than
 * 1:2.39 or wider than 2.39:1 — it throws rather than fitting — and a stream
 * with a broken size can claim either. The limits are pulled in a hair so
 * rounding cannot push a ratio over them.
 */
fun pictureInPictureShape(width: Int, height: Int, pixelWidthHeightRatio: Float): AspectRatio? {
    if (width <= 0 || height <= 0) return null
    val pixelRatio = if (pixelWidthHeightRatio > 0f && pixelWidthHeightRatio.isFinite()) pixelWidthHeightRatio else 1f
    val ratio = (width * pixelRatio / height).coerceIn(1f / PIP_MAX_RATIO, PIP_MAX_RATIO)
    return AspectRatio((ratio * PIP_PRECISION).roundToInt(), PIP_PRECISION)
}

private const val PIP_MAX_RATIO = 2.38f
private const val PIP_PRECISION = 10_000

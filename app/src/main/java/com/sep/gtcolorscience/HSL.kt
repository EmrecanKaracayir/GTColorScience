package com.sep.gtcolorscience

import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

class HSL(@ColorInt rgb: Int) {
    val hue: Float
    val saturation: Float
    val luminosity: Float

    init {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(rgb, hsl)
        hue = hsl[0]
        saturation = hsl[1] * 100
        luminosity = hsl[2] * 100
    }

    override fun toString(): String {
        return "Hue: $hue Saturation: $saturation Lum: $luminosity"
    }
}
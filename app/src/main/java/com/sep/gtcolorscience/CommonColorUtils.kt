package com.sep.gtcolorscience

import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import kotlin.math.pow

class CommonColorUtils {

    @ColorInt
    fun toRGB1(
        h: Float,
        @FloatRange(from = 0.0, to = 1.0) s: Float,
        @FloatRange(from = 0.0, to = 1.0) l: Float
    ): Int {
        return ColorUtils.HSLToColor(floatArrayOf(h, s, l))
    }

    @ColorInt
    fun toRGB100(
        h: Float,
        @FloatRange(from = 0.0, to = 100.0) s: Float,
        @FloatRange(from = 0.0, to = 100.0) l: Float
    ): Int {
        return ColorUtils.HSLToColor(floatArrayOf(h, (s / 100), (l / 100)))
    }

    fun calcLum(@ColorInt colorRGB: Int): Double {
        val vR = (colorRGB.red / 255.0)
        val vG = (colorRGB.green / 255.0)
        val vB = (colorRGB.blue / 255.0)

        val rLin = sRGBtoLin(vR)
        val gLin = sRGBtoLin(vG)
        val bLin = sRGBtoLin(vB)

        val rLinC = rLum * rLin
        val gLinC = gLum * gLin
        val bLinC = bLum * bLin

        return rLinC + gLinC + bLinC
    }

    @FloatRange(from = 0.0, to = 1.0)
    fun convertLumToLStar(lum: Double): Float {
        return if (lum <= lTop) {
            ((lum * lCo) / 100).toFloat()
        } else {
            (((lum.pow(pCE) * 116) - 16) / 100).toFloat()
        }
    }

    private fun sRGBtoLin(colorChannel: Double): Double {
        return if (colorChannel <= 0.04045) {
            colorChannel / 12.92
        } else {
            ((colorChannel + 0.055) / 1.055).pow(mainTRC)
        }
    }

    companion object {
        private const val mainTRC = 2.4
        private const val rLum = 0.2126729
        private const val gLum = 0.7151522
        private const val bLum = 0.0721750
        private const val lTop = 216.0 / 24389.0
        private const val lCo = 24389.0 / 27.0
        private const val pCE = 1.0 / 3.0
    }
}
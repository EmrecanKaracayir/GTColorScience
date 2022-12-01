package com.sep.gtcolorscience

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.pow

class Contraster(private val hslAccent: HSL, private val context: Context) {
    private val commonColorUtils = CommonColorUtils()

    @ColorInt
    fun getAccentColors(): Pair<Int, Int> {
        // ACCENT FG Calc
        val themeColorBG = MaterialColors.getColor(context, R.attr.GTCS_backgroundColor, Color.RED)
        val hslThemeBG = HSL(themeColorBG)

        val isDarkMode = hslThemeBG.luminosity <= 50

        var lumAccentFG = if (isDarkMode) 0f else 100f
        var isFGFound = false
        while (!isFGFound) {
            val fgRGB = commonColorUtils.toRGB100(hslAccent.hue, hslAccent.saturation, lumAccentFG)
            val bgRGB =
                commonColorUtils.toRGB100(hslThemeBG.hue, hslThemeBG.saturation, hslThemeBG.luminosity)
            if (apacContrastCalc(bgRGB, fgRGB)) isFGFound = true
            else {
                if (isDarkMode) lumAccentFG++ else lumAccentFG--
            }
        }
        val rgbAccentFG = commonColorUtils.toRGB100(hslAccent.hue, hslAccent.saturation, lumAccentFG)

        // ACCENT BG Calc
        val themeColorFG = MaterialColors.getColor(context, R.attr.GTCS_foregroundColor, Color.RED)
        val hslThemeFG = HSL(themeColorFG)

        var lumAccentBG = if (isDarkMode) 100f else 0f
        var isBGFound = false
        while (!isBGFound) {
            val fgRGB =
                commonColorUtils.toRGB100(hslThemeFG.hue, hslThemeFG.saturation, hslThemeFG.luminosity)
            val bgRGB = commonColorUtils.toRGB100(hslAccent.hue, hslAccent.saturation, lumAccentBG)
            if (apacContrastCalc(bgRGB, fgRGB)) isBGFound = true
            else {
                if (isDarkMode) lumAccentBG-- else lumAccentBG++
            }
        }
        val rgbAccentBG = commonColorUtils.toRGB100(hslAccent.hue, hslAccent.saturation, lumAccentBG)

        return Pair(rgbAccentFG, rgbAccentBG)
    }

    private fun apacContrastCalc(
        @ColorInt bgRGB: Int, @ColorInt fgRGB: Int
    ): Boolean {
        val outputContrast: Double
        var bgLum = commonColorUtils.calcLum(bgRGB)
        var fgLum = commonColorUtils.calcLum(fgRGB)

        bgLum = if (bgLum > blkThrs) bgLum else bgLum + (blkThrs - bgLum).pow(
            blkClmp
        )
        fgLum = if (fgLum > blkThrs) fgLum else fgLum + (blkThrs - fgLum).pow(
            blkClmp
        )

        outputContrast = if (bgLum > fgLum) {
            val apac = (bgLum.pow(normBG) - fgLum.pow(normFG)) * scaleBoW
            if (apac < loClip) 0.0
            else if (apac < loThrsBoW) apac - apac * loFactorBoW * loOffsetBoW
            else apac - loOffsetBoW
        } else {
            val apac = (bgLum.pow(revBG) - fgLum.pow(revFG)) * scaleWoB

            if (apac > -loClip) 0.0
            else if (apac > -loThrsWoB) apac - apac * loFactorWoB * loOffsetWoB
            else apac + loOffsetWoB
        }
        val loc = outputContrast * 100
        return abs(loc) > THRESHOLD
    }

    companion object {
        private const val THRESHOLD = 75.0
        private const val blkThrs = 0.022
        private const val blkClmp = 1.414
        private const val normBG = 0.56
        private const val normFG = 0.57
        private const val revBG = 0.65
        private const val revFG = 0.62
        private const val loClip = 0.001
        private const val scaleBoW = 1.14
        private const val loThrsBoW = 0.035991
        private const val loFactorBoW = 27.7847239587675
        private const val loOffsetBoW = 0.027
        private const val scaleWoB = 1.14
        private const val loThrsWoB = 0.035991
        private const val loFactorWoB = 27.7847239587675
        private const val loOffsetWoB = 0.027
    }
}
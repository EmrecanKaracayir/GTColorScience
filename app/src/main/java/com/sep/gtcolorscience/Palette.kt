@file:Suppress("unused")

package com.sep.gtcolorscience

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.SparseBooleanArray
import androidx.annotation.ColorInt
import androidx.collection.ArrayMap
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * A helper class to extract prominent colors from an image.
 *
 * A number of colors with different profiles are extracted from the image:
 *
 * Instances are created with a [Palette.Builder] which supports several options to tweak the
 * generated Palette. See that class' documentation for more information.
 *
 * Generation should always be completed on a background thread, ideally the one in
 * which you load your image on. [Palette.Builder] supports both synchronous and asynchronous
 * generation:
 *
 * <pre>
 * // Synchronous
 * Palette p = Palette.from(bitmap).generate();
 *
 * // Asynchronous
 * Palette.from(bitmap).generate(new PaletteAsyncListener() {
 * public void onGenerated(Palette p) {
 * // Use generated instance
 * }
 * });
</pre> *
 */
class Palette constructor(
    private val mSwatches: List<Swatch>, private val mTargets: List<Target>
) {
    private val commonColorUtils = CommonColorUtils()
    private val mSelectedSwatches: MutableMap<Target, Swatch?>
    private val mUsedColors: SparseBooleanArray = SparseBooleanArray()
    private val mDominantSwatch: Swatch?
    private fun findDominantSwatch(): Swatch? {
        var maxPop = Int.MIN_VALUE
        var maxSwatch: Swatch? = null
        var i = 0
        val count = mSwatches.size
        while (i < count) {
            val swatch = mSwatches[i]
            if (swatch.population > maxPop) {
                maxSwatch = swatch
                maxPop = swatch.population
            }
            i++
        }
        return maxSwatch
    }

    /**
     * Returns the selected swatch for the given target from the palette, or `null` if one
     * could not be found.
     */
    fun getSwatchForTarget(target: Target): Swatch? {
        return mSelectedSwatches[target]
    }

    /**
     * Returns the selected color for the given target from the palette as an RGB packed int.
     *
     * @param defaultColor value to return if the swatch isn't available
     */
    @ColorInt
    fun getColorForTarget(target: Target, @ColorInt defaultColor: Int): Int {
        val swatch = getSwatchForTarget(target)
        return swatch?.rgb ?: defaultColor
    }

    fun generate() {
        // We need to make sure that the scored targets are generated first. This is so that
        // inherited targets have something to inherit from
        var i = 0
        val count = mTargets.size
        while (i < count) {
            val target = mTargets[i]
            target.normalizeWeights()
            mSelectedSwatches[target] = generateScoredTarget(target)
            i++
        }
        // We now clear out the used colors
        mUsedColors.clear()
    }

    private fun generateScoredTarget(target: Target): Swatch? {
        val maxScoreSwatch = getMaxScoredSwatchForTarget(target)
        if (maxScoreSwatch != null && target.isExclusive) {
            // If we have a swatch, and the target is exclusive, add the color to the used list
            mUsedColors.append(maxScoreSwatch.rgb, true)
        }
        return maxScoreSwatch
    }

    private fun getMaxScoredSwatchForTarget(target: Target): Swatch? {
        var maxScore = 0f
        var maxScoreSwatch: Swatch? = null
        var i = 0
        val count = mSwatches.size
        while (i < count) {
            val swatch = mSwatches[i]
            if (shouldBeScoredForTarget(swatch, target)) {
                val score = generateScore(swatch, target)
                if (maxScoreSwatch == null || score > maxScore) {
                    maxScoreSwatch = swatch
                    maxScore = score
                }
            }
            i++
        }
        return maxScoreSwatch
    }

    private fun shouldBeScoredForTarget(swatch: Swatch, target: Target): Boolean {
        // Check whether the HSL values are within the correct ranges, and this color hasn't
        // been used yet.
        val hsl = swatch.hsl
        hslLightnessFix(hsl)
        return hsl[1] >= target.minimumSaturation && hsl[1] <= target.maximumSaturation && hsl[2] >= target.minimumLightness && hsl[2] <= target.maximumLightness && !mUsedColors[swatch.rgb]
    }

    private fun hslLightnessFix(hsl: FloatArray) {
        @ColorInt val rgb = commonColorUtils.toRGB1(hsl[0], hsl[1], hsl[2])
        val lum = commonColorUtils.calcLum(rgb)
        hsl[2] = commonColorUtils.convertLumToLStar(lum)
    }

    private fun generateScore(swatch: Swatch, target: Target): Float {
        val hsl = swatch.hsl
        var saturationScore = 0f
        var luminanceScore = 0f
        var populationScore = 0f
        val maxPopulation = mDominantSwatch?.population ?: 1
        if (target.saturationWeight > 0) {
            saturationScore = target.saturationWeight * (1f - abs(hsl[1] - target.targetSaturation))
        }
        if (target.lightnessWeight > 0) {
            luminanceScore = target.lightnessWeight * (1f - abs(hsl[2] - target.targetLightness))
        }
        if (target.populationWeight > 0) {
            populationScore =
                target.populationWeight * (swatch.population / maxPopulation.toFloat())
        }
        return saturationScore + luminanceScore + populationScore
    }

    /**
     * Represents a color swatch generated from an image's palette. The RGB color can be retrieved
     * by calling [.getRgb].
     */
    class Swatch(@ColorInt color: Int, population: Int) {
        private val mRed: Int
        private val mGreen: Int
        private val mBlue: Int

        /**
         * @return this swatch's RGB color value
         */
        @get:ColorInt
        val rgb: Int

        /**
         * @return the number of pixels represented by this swatch
         */
        val population: Int
        private val mHsl = FloatArray(3)

        init {
            mRed = Color.red(color)
            mGreen = Color.green(color)
            mBlue = Color.blue(color)
            rgb = color
            this.population = population
        }

        /**
         * Return this swatch's HSL values.
         * hsv[0] is Hue [0 .. 360)
         * hsv[1] is Saturation [0...1]
         * hsv[2] is Lightness [0...1]
         */
        val hsl: FloatArray
            get() {
                ColorUtils.RGBToHSL(mRed, mGreen, mBlue, mHsl)
                return mHsl
            }

        override fun toString(): String {
            return javaClass.simpleName + " [RGB: #" + Integer.toHexString(rgb) + ']' + " [HSL: " + hsl.contentToString() + ']' + " [Population: " + population + ']'
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other == null || javaClass != other.javaClass) {
                return false
            }
            val swatch = other as Swatch
            return population == swatch.population && rgb == swatch.rgb
        }

        override fun hashCode(): Int {
            return 31 * rgb + population
        }
    }

    /**
     * Builder class for generating [Palette] instances.
     */
    class Builder(bitmap: Bitmap) {
        private val mSwatches: List<Swatch>?
        private val mBitmap: Bitmap
        private val mTargets: MutableList<Target> = ArrayList()
        private var mMaxColors = DEFAULT_CALCULATE_NUMBER_COLORS
        private var mResizeArea = DEFAULT_RESIZE_BITMAP_AREA
        private var mResizeMaxDimension = -1
        private val mFilters: MutableList<Filter> = ArrayList()
        private var mRegion: Rect? = null

        init {
            require(!bitmap.isRecycled) { "Bitmap is not valid" }
            mBitmap = bitmap
            mSwatches = null

            // Add the default targets
            mTargets.add(Target.LIGHT_VIBRANT)
            mTargets.add(Target.VIBRANT)
            mTargets.add(Target.DARK_VIBRANT)
            mTargets.add(Target.LIGHT_MUTED)
            mTargets.add(Target.MUTED)
            mTargets.add(Target.DARK_MUTED)
        }

        /**
         * Set the maximum number of colors to use in the quantization step when using a
         * [android.graphics.Bitmap] as the source.
         *
         *
         * Good values for depend on the source image type. For landscapes, good values are in
         * the range 10-16. For images which are largely made up of people's faces then this
         * value should be increased to ~24.
         */
        fun maximumColorCount(colors: Int): Builder {
            mMaxColors = colors
            return this
        }

        /**
         * Clear all added filters. This includes any default filters added automatically by
         * [Palette].
         */
        fun clearFilters(): Builder {
            mFilters.clear()
            return this
        }

        /**
         * Add a filter to be able to have fine grained control over which colors are
         * allowed in the resulting palette.
         *
         * @param filter filter to add.
         */
        fun addFilter(filter: Filter?): Builder {
            if (filter != null) {
                mFilters.add(filter)
            }
            return this
        }

        /**
         * Add a target profile to be generated in the palette.
         *
         *
         * You can retrieve the result via [Palette.getSwatchForTarget].
         */
        fun addTarget(target: Target): Builder {
            if (!mTargets.contains(target)) {
                mTargets.add(target)
            }
            return this
        }

        /**
         * Clear all added targets. This includes any default targets added automatically by
         * [Palette].
         */
        fun clearTargets(): Builder {
            mTargets.clear()
            return this
        }

        /**
         * Generate and return the [Palette] synchronously.
         */
        private fun generate(): Palette {
            val swatches: List<Swatch>

            // We have a Bitmap so we need to use quantization to reduce the number of colors

            // First we'll scale down the bitmap if needed
            val bitmap = scaleBitmapDown(mBitmap)
            val region = mRegion
            if (bitmap != mBitmap && region != null) {
                // If we have a scaled bitmap and a selected region, we need to scale down the
                // region to match the new scale
                val scale = bitmap.width / mBitmap.width.toDouble()
                region.left = floor(region.left * scale).toInt()
                region.top = floor(region.top * scale).toInt()
                region.right = ceil(region.right * scale).toInt().coerceAtMost(bitmap.width)
                region.bottom = ceil(region.bottom * scale).toInt().coerceAtMost(bitmap.height)
            }

            // Now generate a quantizer from the Bitmap
            val quantizer = ColorCutQuantizer(
                getPixelsFromBitmap(bitmap),
                mMaxColors,
                if (mFilters.isEmpty()) null else mFilters.toTypedArray()
            )

            // If created a new bitmap, recycle it
            if (bitmap != mBitmap) {
                bitmap.recycle()
            }
            swatches = quantizer.quantizedColors

            // Now create a Palette instance
            val p = Palette(swatches, mTargets)
            // And make it generate itself
            p.generate()
            return p
        }

        suspend fun generate(
            onGenerated: (Palette) -> Unit
        ) = CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                generate() // runs in background thread without blocking the Main Thread
            }
            onGenerated(result) // runs in Main Thread
        }

        private fun getPixelsFromBitmap(bitmap: Bitmap): IntArray {
            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height
            val pixels = IntArray(bitmapWidth * bitmapHeight)
            bitmap.getPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)
            return if (mRegion == null) {
                // If we don't have a region, return all of the pixels
                pixels
            } else {
                // If we do have a region, lets create a subset array containing only the region's
                // pixels
                val regionWidth = mRegion!!.width()
                val regionHeight = mRegion!!.height()
                // pixels contains all of the pixels, so we need to iterate through each row and
                // copy the regions pixels into a new smaller array
                val subsetPixels = IntArray(regionWidth * regionHeight)
                for (row in 0 until regionHeight) {
                    System.arraycopy(
                        pixels,
                        (row + mRegion!!.top) * bitmapWidth + mRegion!!.left,
                        subsetPixels,
                        row * regionWidth,
                        regionWidth
                    )
                }
                subsetPixels
            }
        }

        /**
         * Scale the bitmap down as needed.
         */
        private fun scaleBitmapDown(bitmap: Bitmap): Bitmap {
            var scaleRatio = -1.0
            if (mResizeArea > 0) {
                val bitmapArea = bitmap.width * bitmap.height
                if (bitmapArea > mResizeArea) {
                    scaleRatio = sqrt(mResizeArea / bitmapArea.toDouble())
                }
            } else if (mResizeMaxDimension > 0) {
                val maxDimension = bitmap.width.coerceAtLeast(bitmap.height)
                if (maxDimension > mResizeMaxDimension) {
                    scaleRatio = mResizeMaxDimension / maxDimension.toDouble()
                }
            }
            return if (scaleRatio <= 0) {
                // Scaling has been disabled or not needed so just return the Bitmap
                bitmap
            } else Bitmap.createScaledBitmap(
                bitmap,
                ceil(bitmap.width * scaleRatio).toInt(),
                ceil(bitmap.height * scaleRatio).toInt(),
                false
            )
        }
    }

    /**
     * A Filter provides a mechanism for exercising fine-grained control over which colors
     * are valid within a resulting [Palette].
     */
    interface Filter {
        /**
         * Hook to allow clients to be able filter colors from resulting palette.
         *
         * @param rgb the color in RGB888.
         * @param hsl HSL representation of the color.
         * @return true if the color is allowed, false if not.
         * @see Palette.Builder.addFilter
         */
        fun isAllowed(@ColorInt rgb: Int, hsl: FloatArray): Boolean
    }

    init {
        mSelectedSwatches = ArrayMap()
        mDominantSwatch = findDominantSwatch()
    }

    companion object {
        const val DEFAULT_RESIZE_BITMAP_AREA = 112 * 112
        const val DEFAULT_CALCULATE_NUMBER_COLORS = 16

        /**
         * Start generating a [Palette] with the returned [Palette.Builder] instance.
         */
        fun from(bitmap: Bitmap): Builder {
            return Builder(bitmap)
        }

        /**
         * The default filter.
         */
        val KARACAYIR_FILTER: Filter = object : Filter {
            private val BLACK_MAX_LIGHTNESS = 0.025f
            private val WHITE_MIN_LIGHTNESS = 0.975f
            override fun isAllowed(rgb: Int, hsl: FloatArray): Boolean {
                return !isWhite(hsl) && !isBlack(hsl)
            }

            /**
             * @return true if the color represents a color which is close to black.
             */
            private fun isBlack(hslColor: FloatArray): Boolean {
                return hslColor[2] <= BLACK_MAX_LIGHTNESS
            }

            /**
             * @return true if the color represents a color which is close to white.
             */
            private fun isWhite(hslColor: FloatArray): Boolean {
                return hslColor[2] >= WHITE_MIN_LIGHTNESS
            }
        }

        val KARACAYIR_TARGET = Target.Builder().setTargetSaturation(1f).setMinimumSaturation(0.24f)
            .setMinimumLightness(0.16f).setMaximumLightness(0.8f).setTargetLightness(0.5f)
            .setSaturationWeight(0.06f).setLightnessWeight(0.12f).setPopulationWeight(0.82f).build()

    }
}
package com.sep.gtcolorscience

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.sep.gtcolorscience.databinding.ActivityMainBinding
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        if (viewModel.accentContentURI != null) {
            setAccentContentAndTheme(viewModel.accentContentURI!!)
        }

        setContentView(binding.root)

        binding.pickButton.setOnClickListener {
            val pickIntent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            resultLauncher.launch(pickIntent)
        }
    }

    private var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val pickedImageURI = result.data!!.data
                viewModel.accentContentURI = pickedImageURI
                setAccentContentAndTheme(pickedImageURI!!)
            }
        }

    private fun setAccentContentAndTheme(uri: Uri) {
        Glide.with(baseContext).asBitmap().load(uri).override(1080).centerCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap, transition: Transition<in Bitmap>?
                ) {
                    binding.imageView.setImageBitmap(resource)
                    calculateColors(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    TODO("Not yet implemented")
                }

            })
    }

    private fun calculateColors(resource: Bitmap) {
        lifecycleScope.launch {
            Palette.from(resource).maximumColorCount(24).addFilter(Palette.KARACAYIR_FILTER)
                .addTarget(Palette.KARACAYIR_TARGET).generate {
                    @ColorInt val dominantColor =
                        it.getColorForTarget(Palette.KARACAYIR_TARGET, Color.BLACK)
                    binding.pickedImageColor.setCardBackgroundColor(dominantColor)
                    val hsl = HSL(dominantColor)
                    val contraster = Contraster(hsl, this@MainActivity)
                    setAccentTheme(contraster)
                }
        }
    }

    private fun setAccentTheme(contraster: Contraster) {
        val accentColors = contraster.getAccentColors()
        @ColorInt val accentColorFG = accentColors.first
        @ColorInt val accentColorBG = accentColors.second

        binding.apply {
            val accentGradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(accentColorBG, Color.TRANSPARENT)
            )
            val transitionDrawables = arrayOf(heroLayout.background, accentGradientDrawable)
            val mTransition = TransitionDrawable(transitionDrawables)
            mTransition.isCrossFadeEnabled = true
            heroLayout.background = mTransition
            mTransition.startTransition(300)

            bgTextSample.setCardBackgroundColor(accentColorBG)
            fgTextSample.setCardBackgroundColor(accentColorFG)

            sampleTextView.setTextColor(accentColorFG)

            pickButton.backgroundTintList = ColorStateList.valueOf(accentColorFG)
        }
    }
}
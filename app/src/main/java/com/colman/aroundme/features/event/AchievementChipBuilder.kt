package com.colman.aroundme.features.event

import android.content.Context
import android.content.res.ColorStateList
import com.colman.aroundme.R
import com.google.android.material.chip.Chip
import com.google.android.material.shape.CornerFamily

// Creates achievement chips shown on the publisher card.
object AchievementChipBuilder {

    fun create(context: Context, title: String): Chip {
        return Chip(context).apply {
            text = title
            isClickable = false
            isCheckable = false

            // Secondary tint at ~10% opacity, secondary text.
            chipBackgroundColor = ColorStateList.valueOf(context.getColor(R.color.ds_secondary_10))
            setTextColor(context.getColor(R.color.ds_secondary))
            textSize = 10f

            chipStrokeWidth = 0f
            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, 999f)
                .build()
        }
    }
}

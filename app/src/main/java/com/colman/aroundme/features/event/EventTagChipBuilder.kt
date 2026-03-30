package com.colman.aroundme.features.event

import android.content.Context
import android.content.res.ColorStateList
import com.colman.aroundme.R
import com.google.android.material.chip.Chip
import com.google.android.material.shape.CornerFamily

object EventTagChipBuilder {

    fun create(context: Context, rawTag: String): Chip {
        val tag = rawTag.trim().removePrefix("#")
        return Chip(context).apply {
            text = "#${tag.uppercase()}"
            isClickable = false
            isCheckable = false

            setTextColor(context.getColor(R.color.ds_primary))
            chipBackgroundColor = ColorStateList.valueOf(context.getColor(R.color.ds_primary_10))
            chipStrokeWidth = 0f

            shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, 999f)
                .build()
        }
    }
}

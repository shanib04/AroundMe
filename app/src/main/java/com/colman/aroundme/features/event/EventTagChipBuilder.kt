package com.colman.aroundme.features.event

import android.content.Context
import com.google.android.material.chip.Chip

object EventTagChipBuilder {

    fun create(context: Context, text: String): Chip {
        return Chip(context).apply {
            this.text = text
            isClickable = false
            isCheckable = false
        }
    }
}


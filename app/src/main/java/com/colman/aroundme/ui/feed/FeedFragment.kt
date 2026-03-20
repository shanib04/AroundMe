package com.colman.aroundme.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.colman.aroundme.R

class FeedFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_feed, container, false)
        val text = root.findViewById<TextView>(R.id.text_feed)
        text.text = "Feed Page"
        return root
    }
}


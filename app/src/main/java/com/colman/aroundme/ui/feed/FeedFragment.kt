package com.colman.aroundme.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.colman.aroundme.R
import com.colman.aroundme.model.Event

class FeedFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var adapter: EventAdapter
    private val viewModel: FeedViewModel by viewModels()

    private var currentList: List<Event> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_feed, container, false)
        recyclerView = root.findViewById(R.id.recyclerViewFeed)
        progressBar = root.findViewById(R.id.feedProgressBar)
        swipeRefresh = root.findViewById(R.id.swipeRefresh)

        // Setup sort dropdown
        val sortDropdown = root.findViewById<android.widget.AutoCompleteTextView>(R.id.sort_dropdown)
        val options = listOf("Newest", "Distance", "Ending Soon")
        val adapterDrop = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, options)
        sortDropdown.setAdapter(adapterDrop)
        sortDropdown.setText(options[0], false)
        sortDropdown.setOnItemClickListener { _, _, position, _ ->
            applySort(options[position])
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = EventAdapter { event ->
            // On item click show a Toast
            Toast.makeText(requireContext(), "Clicked: ${event.title}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Observe events from ViewModel
        viewModel.events.observe(viewLifecycleOwner) { list ->
            currentList = list
            adapter.submitList(list)
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            swipeRefresh.isRefreshing = loading
            progressBar.visibility = if (loading && adapter.itemCount == 0) View.VISIBLE else View.GONE
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        // Trigger an initial refresh to simulate remote sync
        viewModel.refresh()
    }

    private fun applySort(option: String) {
        val sorted = when (option) {
            "Newest" -> currentList.sortedByDescending { it.id.toIntOrNull() ?: 0 }
            "Distance" -> currentList // Placeholder: requires distance calculation (keep as-is)
            "Ending Soon" -> currentList.sortedBy { it.timeRemaining }
            else -> currentList
        }
        adapter.submitList(sorted)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}

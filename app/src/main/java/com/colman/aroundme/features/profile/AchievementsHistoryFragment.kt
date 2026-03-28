package com.colman.aroundme.features.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.colman.aroundme.R
import com.google.android.material.appbar.MaterialToolbar

class AchievementsHistoryFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels({ requireActivity() }) {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    private val adapter = AchievementHistoryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_achievements_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarAchievements)
        val recyclerView = view.findViewById<RecyclerView>(R.id.achievementsHistoryRecyclerView)
        val emptyStateText = view.findViewById<TextView>(R.id.emptyStateText)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewModel.achievementHistory.observe(viewLifecycleOwner) { history ->
            val safeHistory = history.orEmpty()
            adapter.submitList(safeHistory)
            emptyStateText.visibility = if (safeHistory.isEmpty()) View.VISIBLE else View.GONE
        }

        if (viewModel.achievementHistory.value.isNullOrEmpty()) {
            viewModel.loadCurrentUser()
        }
    }
}

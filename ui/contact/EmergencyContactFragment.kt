package com.example.rush_hz_plus.ui.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentEmergencyContactBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EmergencyContactFragment : Fragment() {

    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by viewModels()
    private lateinit var adapter: GuardianAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 수정: GuardianAdapter 인자 없음
        adapter = GuardianAdapter()

        binding.recyclerViewContacts.adapter = adapter
        binding.recyclerViewContacts.layoutManager = LinearLayoutManager(requireContext())

        // 스와이프 삭제만 설정
        val itemTouchHelper = ItemTouchHelper(
            SwipeToDeleteCallback(requireContext()) { position ->
                val guardian = adapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback
                viewModel.removeGuardian(guardian)
            }
        )
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewContacts)

        binding.btnAddContact.setOnClickListener {
            findNavController().navigate(R.id.action_emergencyContact_to_addContact)
        }

        lifecycleScope.launch {
            viewModel.guardians.collect { guardians ->
                adapter.submitList(guardians)
            }
        }

        lifecycleScope.launch {
            viewModel.operationState.collect { msg ->
                if (msg.isNotBlank()) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.loadGuardians()
    }
}
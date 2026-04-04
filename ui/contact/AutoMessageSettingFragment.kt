// ui/contact/AutoMessageSettingFragment.kt
package com.example.rush_hz_plus.ui.contact

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.databinding.FragmentAutoMessageSettingBinding
import com.example.rush_hz_plus.core.utils.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AutoMessageSettingFragment : Fragment() {

    private var _binding: FragmentAutoMessageSettingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by viewModels()
    private lateinit var adapter: AutoMessageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentAutoMessageSettingBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        adapter = AutoMessageAdapter()

        // 스와이프 삭제 적용
        val itemTouchHelper = ItemTouchHelper(
            SwipeToDeleteCallback(requireContext()) { position ->
                val message = adapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback
                viewModel.removeMessage(message)
            }
        )
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewMessages)

        binding.recyclerViewMessages.adapter = adapter
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())

        binding.btnAddMessage.setOnClickListener {
            val input = EditText(requireContext())
            AlertDialog.Builder(requireContext())
                .setTitle("새 메시지 등록")
                .setMessage("자동 발송할 메시지를 입력하세요")
                .setView(input)
                .setPositiveButton("등록") { _, _ ->
                    val content = input.text.toString().trim()
                    if (content.isNotBlank()) viewModel.addMessage(content)
                }
                .setNegativeButton("취소", null)
                .show()
        }

        lifecycleScope.launch {
            viewModel.messages.collect { list ->
                adapter.submitList(list)
            }
        }

        viewModel.loadMessages()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

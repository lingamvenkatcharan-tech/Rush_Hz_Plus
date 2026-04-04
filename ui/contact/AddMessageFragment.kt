// ui/contact/AddMessageFragment.kt
package com.example.rush_hz_plus.ui.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.rush_hz_plus.R
import com.example.rush_hz_plus.core.utils.PreferenceManager
import com.example.rush_hz_plus.databinding.FragmentAddMessageBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AddMessageFragment : Fragment() {

    private var _binding: FragmentAddMessageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 뒤로가기 버튼
        binding.imageBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 메시지 추가 버튼
        binding.btnAddMessage.setOnClickListener {
            val content = binding.etMessageText.text.toString().trim()
            if (content.isNotBlank()) {
                viewModel.addMessage(content)
                Toast.makeText(context, "메시지 추가 완료", Toast.LENGTH_SHORT).show()
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } else {
                Toast.makeText(context, "메시지를 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

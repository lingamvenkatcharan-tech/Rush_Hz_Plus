package com.example.rush_hz_plus.ui.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.rush_hz_plus.databinding.FragmentAddContactBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddContactFragment : Fragment() {

    private var _binding: FragmentAddContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentAddContactBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.imageBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        binding.btnRegisterContact.setOnClickListener {
            val name = binding.etContactName.text.toString().trim()
            val phone = binding.etContactPhone.text.toString().trim()

            if (name.isNotBlank() && phone.isNotBlank()) {
                viewModel.addGuardian(name, phone)

                lifecycleScope.launch {
                    viewModel.operationState.collectLatest { state ->
                        when {
                            state.contains("추가 완료") -> {
                                Toast.makeText(context, state, Toast.LENGTH_SHORT).show()
                                requireActivity().onBackPressedDispatcher.onBackPressed()
                            }
                            state.contains("실패") -> {
                                Toast.makeText(context, state, Toast.LENGTH_SHORT).show()
                            }
                            state.contains("권한") -> {
                                Toast.makeText(context, state, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(context, "이름과 전화번호를 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

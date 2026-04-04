package com.example.rush_hz_plus.ui.alert

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.rush_hz_plus.databinding.FragmentAlarmBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

@AndroidEntryPoint
class AlarmFragment : Fragment(), TextToSpeech.OnInitListener {

    private var _binding: FragmentAlarmBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AlertViewModel by viewModels()
    private lateinit var adapter: AlarmAdapter
    private var tts: TextToSpeech? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tts = TextToSpeech(requireContext(), this)

        adapter = AlarmAdapter { alarm ->
            speakMessage(alarm.alertMessage.ifEmpty { "위험 소리 감지: ${alarm.soundLabel}" })
        }

        binding.recyclerViewAlarms.adapter = adapter
        binding.recyclerViewAlarms.layoutManager = LinearLayoutManager(requireContext())

        binding.imageBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        // Room Flow 구독 (영구 저장소 기반)
        lifecycleScope.launch {
            viewModel.alarms.collectLatest { list ->
                adapter.submitList(list)
                if (list.isNotEmpty()) {
                    val latest = list.first()
                    speakMessage(latest.alertMessage.ifEmpty { "위험 소리 감지: ${latest.soundLabel}" })
                }
            }
        }
    }

    private fun speakMessage(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "alarm_message")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREA
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.stop()
        tts?.shutdown()
        _binding = null
    }
}
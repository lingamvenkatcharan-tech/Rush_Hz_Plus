package com.example.rush_hz_plus.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.example.rush_hz_plus.R

class ServiceInfoFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 프래그먼트의 레이아웃을 인플레이트합니다.
        return inflater.inflate(R.layout.fragment_service_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton: ImageView = view.findViewById(R.id.image_back)

        // 'finish()' 대신 부모 액티비티의 뒤로가기 기능을 호출합니다.
        backButton.setOnClickListener {
            requireActivity().onBackPressed()
        }
    }
}
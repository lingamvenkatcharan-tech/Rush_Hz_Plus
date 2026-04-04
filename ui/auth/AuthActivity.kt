package com.example.rush_hz_plus.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.rush_hz_plus.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth) // 새 레이아웃
    }
}
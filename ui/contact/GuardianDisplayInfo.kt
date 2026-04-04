package com.example.rush_hz_plus.ui.contact

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GuardianDisplayInfo(
    val uid: String,
    val phoneNumber: String,
    val nickname: String,
    val isAppUser: Boolean
) : Parcelable
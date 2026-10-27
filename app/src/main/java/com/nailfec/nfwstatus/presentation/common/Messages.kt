package com.nailfec.nfwstatus.presentation.common

import androidx.annotation.StringRes
import com.nailfec.nfwstatus.R
import com.nailfec.nfwstatus.data.remote.FailureKind

@StringRes
fun FailureKind.messageRes(): Int = when (this) {
    FailureKind.SettingsMissing -> R.string.error_settings_missing
    FailureKind.TokenRejected -> R.string.error_token_rejected
    FailureKind.Overlap -> R.string.error_overlap
    FailureKind.Invalid -> R.string.error_invalid
    FailureKind.NotFound -> R.string.error_not_found
    FailureKind.Exists -> R.string.error_exists
    FailureKind.Unreachable -> R.string.error_unreachable
    FailureKind.BadUrl -> R.string.error_bad_url
    FailureKind.Photo -> R.string.error_photo
}

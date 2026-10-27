package com.nailfec.nfwstatus.data.remote

sealed interface NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>
    data class Failure(val kind: FailureKind) : NetworkResult<Nothing>
}

enum class FailureKind {
    SettingsMissing,
    TokenRejected,
    Overlap,
    Invalid,
    NotFound,
    Exists,
    Unreachable,
    BadUrl,
    Photo,
}

data class Credentials(
    val baseUrl: String,
    val token: String,
)

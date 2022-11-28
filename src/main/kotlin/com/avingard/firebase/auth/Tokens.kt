package com.avingard.firebase.auth

import java.time.Instant

internal data class Tokens(
    val idToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Instant = Instant.now(),
    val claimsMap: Map<String, Any> = emptyMap()
)
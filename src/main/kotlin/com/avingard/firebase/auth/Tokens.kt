package com.avingard.firebase.auth

import com.avingard.firebase.extractClaimsFromIdToken
import java.time.Instant

internal data class Tokens(
    val idToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Instant = Instant.now(),
    val claimsMap: Map<String, Any> = emptyMap()
) {
    companion object {
        fun fromData(data: Requests.RegularAuthResponseData) = Tokens(
            idToken = data.idToken,
            refreshToken = data.refreshToken,
            expiresAt = Instant.now().plusSeconds(data.expiresIn.toLong()),
            claimsMap = extractClaimsFromIdToken(data.idToken)
        )

        fun fromData(data: Requests.TokenExchangeResponseData): Tokens {
            return Tokens(
                idToken = data.idToken,
                refreshToken = data.refreshToken,
                expiresAt = Instant.now().plusSeconds(data.expiresIn.toLong()),
                claimsMap = extractClaimsFromIdToken(data.idToken)
            )
        }
    }
}
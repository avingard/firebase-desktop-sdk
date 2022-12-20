package com.avingard.firebase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.slf4j.LoggerFactory
import java.security.SecureRandom

internal val LOG = LoggerFactory.getLogger("com.avingard.firebase")

private val random = SecureRandom()
private const val AUTO_ID_LENGTH = 20
private const val AUTO_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

internal fun generateAutoId(): String {
    val builder = StringBuilder()
    val maxRandom: Int = AUTO_ID_ALPHABET.length
    for (i in 0 until AUTO_ID_LENGTH) {
        builder.append(AUTO_ID_ALPHABET[random.nextInt(maxRandom)])
    }
    return builder.toString()
}

internal fun extractClaimsFromIdToken(idToken: String): Map<String, Any> {
    val jwtConsumer = JwtConsumerBuilder()
        .setDisableRequireSignature()
        .setSkipAllValidators()
        .setSkipSignatureVerification()
        .build()

    return jwtConsumer.processToClaims(idToken).claimsMap
}
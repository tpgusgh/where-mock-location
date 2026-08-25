package com.tpgusgh.wheremocklocation

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PairingToken {
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun isValid(candidate: String, expected: String): Boolean {
        val candidateBytes = candidate.toByteArray(Charsets.UTF_8)
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(candidateBytes, expectedBytes)
    }
}

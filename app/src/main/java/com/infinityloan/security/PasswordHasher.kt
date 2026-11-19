package com.infinityloan.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Versioned PBKDF2 password hashing for local credential storage.
 * Production authentication must additionally use server-side identity,
 * rate limiting, account lockout and secure session management.
 */
data class PasswordHash(
    val algorithm: String,
    val iterations: Int,
    val saltBase64: String,
    val hashBase64: String
)

object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val FORMAT = "PBKDF2_SHA256_V1"
    private const val ITERATIONS = 210_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(password: CharArray): PasswordHash {
        require(password.isNotEmpty()) { "La contraseña no puede estar vacía." }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val derived = derive(password, salt, ITERATIONS)
        return PasswordHash(
            algorithm = FORMAT,
            iterations = ITERATIONS,
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(derived)
        )
    }

    fun verify(password: CharArray, stored: PasswordHash): Boolean {
        if (password.isEmpty() || stored.algorithm != FORMAT ||
            stored.iterations !in 100_000..1_000_000
        ) return false

        return try {
            val salt = Base64.getDecoder().decode(stored.saltBase64)
            val expected = Base64.getDecoder().decode(stored.hashBase64)
            if (salt.size != SALT_BYTES || expected.size != KEY_BITS / 8) {
                return false
            }
            val actual = derive(password, salt, stored.iterations)
            MessageDigest.isEqual(expected, actual)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}

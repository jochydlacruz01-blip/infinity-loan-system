package com.infinityloan.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun verifiesCorrectPasswordAndRejectsIncorrectPassword() {
        val stored = PasswordHasher.hash("ClaveSegura-2026".toCharArray())

        assertTrue(PasswordHasher.verify("ClaveSegura-2026".toCharArray(), stored))
        assertFalse(PasswordHasher.verify("ClaveIncorrecta".toCharArray(), stored))
    }

    @Test
    fun generatesIndependentSaltsForSamePassword() {
        val first = PasswordHasher.hash("OtraClaveSegura-2026".toCharArray())
        val second = PasswordHasher.hash("OtraClaveSegura-2026".toCharArray())

        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
        assertTrue(PasswordHasher.verify("OtraClaveSegura-2026".toCharArray(), first))
        assertTrue(PasswordHasher.verify("OtraClaveSegura-2026".toCharArray(), second))
    }

    @Test
    fun rejectsUnsupportedOrMalformedStoredHash() {
        val valid = PasswordHasher.hash("ClaveDePrueba-2026".toCharArray())

        assertFalse(
            PasswordHasher.verify(
                "ClaveDePrueba-2026".toCharArray(),
                valid.copy(algorithm = "UNSUPPORTED")
            )
        )
        assertFalse(
            PasswordHasher.verify(
                "ClaveDePrueba-2026".toCharArray(),
                valid.copy(saltBase64 = "not-base64")
            )
        )
        assertFalse(
            PasswordHasher.verify(
                "ClaveDePrueba-2026".toCharArray(),
                valid.copy(iterations = 1)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyPasswordWhenCreatingHash() {
        PasswordHasher.hash(charArrayOf())
    }
}

package com.example

import com.example.crypto.TammieCipher
import com.example.i18n.AppLanguage
import com.example.i18n.stringsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun cipher_roundtrip_english_and_bengali() {
        val convId = "d_alice_bob"
        val salt = TammieCipher.generateSalt()
        val message = "Hello Tammie! কেমন আছেন? 🔒"
        val encrypted = TammieCipher.encrypt(message, convId, salt)

        assertNotEquals(message, encrypted.ciphertextBase64)
        assertTrue(encrypted.ivBase64.isNotBlank())

        val decrypted = TammieCipher.decrypt(
            encrypted.ciphertextBase64,
            encrypted.ivBase64,
            convId,
            salt
        )
        assertEquals(message, decrypted)
    }

    @Test
    fun cipher_wrong_salt_fails_decryption() {
        val encrypted = TammieCipher.encrypt("Secret", "conv_1", "salt_one_1234567890")
        val wrong = TammieCipher.decrypt(
            encrypted.ciphertextBase64,
            encrypted.ivBase64,
            "conv_1",
            "salt_two_1234567890"
        )
        assertEquals("", wrong)
    }

    @Test
    fun localization_contains_english_and_bengali() {
        val en = stringsFor(AppLanguage.EN)
        val bn = stringsFor(AppLanguage.BN)
        assertEquals("Tammie", en.appName)
        assertEquals("Tammie", bn.appName)
        assertNotEquals(en.signInWithGoogle, bn.signInWithGoogle)
    }
}

package com.example.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

object TammieCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private val secureRandom = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(conversationId: String, encryptionSalt: String): SecretKey {
        val material = "tammie-e2ee-v1:$conversationId:$encryptionSalt".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return SecretKeySpec(digest, "AES")
    }

    fun encrypt(plaintext: String, conversationId: String, encryptionSalt: String): EncryptedPayload {
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val key = deriveKey(conversationId, encryptionSalt)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = encodeBase64(encryptedBytes),
            ivBase64 = encodeBase64(iv)
        )
    }

    fun decrypt(
        ciphertextBase64: String,
        ivBase64: String,
        conversationId: String,
        encryptionSalt: String
    ): String {
        if (ciphertextBase64.isBlank() || ivBase64.isBlank()) return ""
        return try {
            val iv = decodeBase64(ivBase64)
            val cipherBytes = decodeBase64(ciphertextBase64)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = deriveKey(conversationId, encryptionSalt)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun fingerprint(conversationId: String, encryptionSalt: String): String {
        val material = "tammie-fp-v1:$conversationId:$encryptionSalt".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        return digest.take(16).joinToString("") { "%02X".format(it) }
            .chunked(4)
            .chunked(2)
            .joinToString("\n") { it.joinToString("  ") }
    }

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(data: ByteArray): String {
        val out = StringBuilder(((data.size + 2) / 3) * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i++].toInt() and 0xFF
            val b1 = if (i < data.size) data[i++].toInt() and 0xFF else -1
            val b2 = if (i < data.size) data[i++].toInt() and 0xFF else -1

            out.append(BASE64_ALPHABET[b0 ushr 2])
            out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 ushr 4 else 0)])
            out.append(if (b1 >= 0) BASE64_ALPHABET[((b1 and 0x0F) shl 2) or (if (b2 >= 0) b2 ushr 6 else 0)] else '=')
            out.append(if (b2 >= 0) BASE64_ALPHABET[b2 and 0x3F] else '=')
        }
        return out.toString()
    }

    private fun decodeBase64(input: String): ByteArray {
        val clean = input.trim().replace("\n", "").replace("\r", "")
        if (clean.isEmpty()) return ByteArray(0)
        require(clean.length % 4 == 0) { "Invalid Base64 length" }
        var padding = 0
        if (clean.endsWith("==")) padding = 2 else if (clean.endsWith("=")) padding = 1
        val out = ByteArray((clean.length / 4) * 3 - padding)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = BASE64_ALPHABET.indexOf(clean[i++])
            val c1 = BASE64_ALPHABET.indexOf(clean[i++])
            val c2Char = clean[i++]
            val c3Char = clean[i++]
            val c2 = if (c2Char == '=') 0 else BASE64_ALPHABET.indexOf(c2Char)
            val c3 = if (c3Char == '=') 0 else BASE64_ALPHABET.indexOf(c3Char)
            require(c0 >= 0 && c1 >= 0 && c2 >= 0 && c3 >= 0) { "Invalid Base64 char" }
            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIdx < out.size) out[outIdx++] = ((triple shr 16) and 0xFF).toByte()
            if (outIdx < out.size) out[outIdx++] = ((triple shr 8) and 0xFF).toByte()
            if (outIdx < out.size) out[outIdx++] = (triple and 0xFF).toByte()
        }
        return out
    }
}

package misk.crypto

import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.KeysetManager
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import okio.ByteString.Companion.toByteString
import misk.testing.MiskTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

@MiskTest
class CipherTest {

  init {
    AeadConfig.register()
  }

  private val masterKey = FakeMasterEncryptionKey()
  private val keysetHandle =  KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)

  @Test
  fun testEncryptDecryptRoundTrip() {
    val cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val plain = "plain".toByteArray().toByteString()
    val encrypted = cipher.encrypt(plain)
    val decrypted = cipher.decrypt(encrypted)
    assertThat(encrypted).isNotEqualTo(plain)
    assertThat(decrypted).isEqualTo(plain)
  }

  @Test
  fun testNoSuitableKeyFound() {
    var cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val plain = "plain".toByteArray().toByteString()
    val encrypted = cipher.encrypt(plain)
    val newKeysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    cipher = RealCipher(listOf(Pair(newKeysetHandle, masterKey)))
    assertThatThrownBy { cipher.decrypt(encrypted) }
        .isInstanceOf(NullPointerException::class.java)
  }

  @Test
  fun testKeyRotation() {
    // encrypt something
    var cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val plain = "plain".toByteArray().toByteString()
    val encrypted = cipher.encrypt(plain)
    // rotate the key
    val rotatedKeysetHandle = KeysetManager.withKeysetHandle(keysetHandle)
        .rotate(AeadKeyTemplates.AES256_GCM)
        .keysetHandle
    cipher = RealCipher(listOf(Pair(rotatedKeysetHandle, masterKey)))
    // decrpyt it
    val decrypted = cipher.decrypt(encrypted)
    assertThat(encrypted).isNotEqualTo(plain)
    assertThat(decrypted).isEqualTo(plain)
  }

  @Test
  fun testDecryptionIterateThroughKeys() {
    // encrypt something
    var cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val plain = "plain".toByteArray().toByteString()
    val encrypted = cipher.encrypt(plain)
    val newKeysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
    cipher = RealCipher(listOf(
        Pair(newKeysetHandle, masterKey),
        Pair(keysetHandle, masterKey)))
    // decrpyt it
    val decrypted = cipher.decrypt(encrypted)
    assertThat(encrypted).isNotEqualTo(plain)
    assertThat(decrypted).isEqualTo(plain)

  }

  @Test
  fun testKeyInfo() {
    val cipher = RealCipher(listOf(Pair(keysetHandle, masterKey)))
    val keysetHandleInfo = keysetHandle.keysetInfo.toString()
    assertThat(cipher.keyInfo.map { it.tinkInfo }).contains(keysetHandleInfo)
  }
}
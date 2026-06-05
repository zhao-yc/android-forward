package com.zhaoyuchen.androidforward.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 使用 Android Keystore 封装字符串加密存储。
 *
 * 这里不引入 androidx.security，减少依赖下载；AES-GCM 的 IV 和密文一起保存。
 */
object SecureStore {
    private const val PREFS_NAME = "android_forward_secure"
    private const val KEY_ALIAS = "android_forward_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val SEPARATOR = ":"

    private val lock = Any()

    /** 加密保存字符串，空值也会被正常保存，便于用户清空 Bark Key。 */
    fun putString(context: Context, name: String, value: String) {
        synchronized(lock) {
            val encrypted = encrypt(value)
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(name, encrypted)
                .apply()
        }
    }

    /** 解密读取字符串；如果密钥被系统重置或数据损坏，则返回空字符串。 */
    fun getString(context: Context, name: String): String {
        return synchronized(lock) {
            val stored = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(name, null)
                ?: return@synchronized ""
            runCatching { decrypt(stored) }.getOrDefault("")
        }
    }

    /** 删除一项加密值，用于未来扩展重置功能。 */
    fun removeString(context: Context, name: String) {
        synchronized(lock) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(name)
                .apply()
        }
    }

    /** 执行 AES-GCM 加密，并把 IV 与密文组合成可存储字符串。 */
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$iv$SEPARATOR$body"
    }

    /** 根据保存的 IV 还原 AES-GCM 明文。 */
    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR, limit = 2)
        require(parts.size == 2) { "加密数据格式不正确" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    /** 获取或创建 Android Keystore 中的 AES 密钥。 */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}

package com.feiyu.app

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
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密敏感字段（API Key 等）。
 * 零第三方依赖；卸载 App 后 Keystore 密钥一并销毁，密文不可再解。
 */
object SecureKey {

    private const val ALIAS = "whalechat_secret"
    private const val PREFIX = "enc:"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(): SecretKey {
        val ks = keyStore()
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(ctx: Context, plain: String): String {
        if (plain.isEmpty()) return ""
        if (plain.startsWith(PREFIX)) return plain
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray())
            val buf = iv + ct
            PREFIX + Base64.encodeToString(buf, Base64.NO_WRAP)
        }.getOrDefault(plain)
    }

    fun decrypt(ctx: Context, stored: String): String {
        if (stored.isEmpty()) return ""
        if (!stored.startsWith(PREFIX)) return stored // 旧版明文，原样返回，下次保存时会加密
        return runCatching {
            val buf = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = buf.copyOfRange(0, 12)
            val ct = buf.copyOfRange(12, buf.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct))
        }.getOrDefault("")
    }
}

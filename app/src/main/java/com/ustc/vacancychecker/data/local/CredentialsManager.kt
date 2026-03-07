package com.ustc.vacancychecker.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 使用 EncryptedSharedPreferences 加密存储用户凭证
 */
@Singleton
class CredentialsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun saveCredentials(username: String, password: String) {
        sharedPreferences.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }
    
    fun getUsername(): String? = sharedPreferences.getString(KEY_USERNAME, null)
    
    fun getPassword(): String? = sharedPreferences.getString(KEY_PASSWORD, null)
    
    fun hasCredentials(): Boolean = getUsername() != null && getPassword() != null
    
    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }
    
    companion object {
        private const val PREFS_NAME = "ustc_vacancy_checker_encrypted_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}

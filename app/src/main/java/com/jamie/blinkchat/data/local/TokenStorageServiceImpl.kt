package com.jamie.blinkchat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jamie.blinkchat.core.common.Constants
import com.jamie.blinkchat.repositories.TokenStorageService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFERENCES_NAME)

@Singleton
class TokenStorageServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenStorageService {

    // Define the key for the auth token
    private object PreferencesKeys {
        val AUTH_TOKEN = stringPreferencesKey(Constants.PREF_AUTH_TOKEN)
    }

    override suspend fun saveAuthToken(token: String) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.AUTH_TOKEN] = token
            }
            Timber.d("Auth token saved successfully.")
        } catch (e: IOException) {
            Timber.e(e, "Error saving auth token to DataStore.")
            // Optionally rethrow or handle as per your app's error handling strategy
        }
    }

    override fun getAuthToken(): Flow<String?> {
        return context.dataStore.data
            .catch { exception ->
                // IOException means an error while reading data
                if (exception is IOException) {
                    Timber.e(exception, "Error reading auth token from DataStore.")
                    emit(emptyPreferences()) // Emit empty preferences on error
                } else {
                    throw exception // Rethrow other exceptions
                }
            }
            .map { preferences ->
                preferences[PreferencesKeys.AUTH_TOKEN] // Returns null if key doesn't exist
            }
    }

    override suspend fun clearAuthToken() {
        try {
            context.dataStore.edit { preferences ->
                preferences.remove(PreferencesKeys.AUTH_TOKEN)
            }
            Timber.d("Auth token cleared successfully.")
        } catch (e: IOException) {
            Timber.e(e, "Error clearing auth token from DataStore.")
        }
    }
}
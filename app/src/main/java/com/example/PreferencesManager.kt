package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    private val PAT_KEY = stringPreferencesKey("github_pat")

    val patFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PAT_KEY]
    }

    suspend fun savePat(pat: String) {
        context.dataStore.edit { preferences ->
            preferences[PAT_KEY] = pat
        }
    }
    
    suspend fun clearPat() {
        context.dataStore.edit { preferences ->
            preferences.remove(PAT_KEY)
        }
    }
}

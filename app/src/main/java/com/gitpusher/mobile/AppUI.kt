package com.gitpusher.mobile

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import retrofit2.HttpException

class GithubViewModel : ViewModel() {
    var pat by mutableStateOf<String?>(null)
    var user by mutableStateOf<GithubUser?>(null)
    var repos by mutableStateOf<List<GithubRepo>>(emptyList())
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun login(token: String, onSuccess: () -> Unit) {
        isLoading = true
        error = null
        val bearer = "Bearer \$token"
        // Coroutine launched in view
    }
}

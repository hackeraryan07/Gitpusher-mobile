package com.example

import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()
    
    var startDestination by remember { mutableStateOf("splash") }
    var userPat by remember { mutableStateOf<String?>(null) }
    var currentUser by remember { mutableStateOf<GithubUser?>(null) }

    LaunchedEffect(Unit) {
        prefs.patFlow.collect { pat ->
            if (pat != null) {
                try {
                    currentUser = GithubApiManager.api.getUser("Bearer $pat")
                    userPat = pat
                    navController.navigate("dashboard") {
                        popUpTo("splash") { inclusive = true }
                    }
                } catch (e: Exception) {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            } else {
                navController.navigate("login") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable("login") {
            var patInput by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var err by remember { mutableStateOf<String?>(null) }

            Scaffold { padding ->
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Login with GitHub PAT", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = patInput,
                        onValueChange = { patInput = it },
                        label = { Text("Personal Access Token") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (err != null) {
                        Text(err!!, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        loading = true
                        err = null
                        scope.launch {
                            try {
                                currentUser = GithubApiManager.api.getUser("Bearer $patInput")
                                userPat = patInput
                                prefs.savePat(patInput)
                                navController.navigate("dashboard") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } catch (e: Exception) {
                                err = "Failed to login: ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !loading && patInput.isNotBlank()) {
                        if(loading) CircularProgressIndicator(modifier=Modifier.size(24.dp))
                        else Text("Login")
                    }
                }
            }
        }
        composable("dashboard") {
            var repos by remember { mutableStateOf<List<GithubRepo>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                try {
                    repos = GithubApiManager.api.getRepos("Bearer $userPat")
                } catch (e: Exception) {
                } finally {
                    loading = false
                }
            }

            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(onClick = { navController.navigate("create_repo") }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Repo")
                    }
                }
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Hello, ${currentUser?.name ?: currentUser?.login}", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = {
                            scope.launch {
                                prefs.clearPat()
                                navController.navigate("login") { popUpTo(0) }
                            }
                        }) { Text("Logout") }
                    }
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(repos) { repo ->
                                Card(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clickable { 
                                            navController.navigate("push/${repo.full_name}")
                                        }
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(repo.name, style = MaterialTheme.typography.titleMedium)
                                        if (!repo.description.isNullOrEmpty()) {
                                            Text(repo.description, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        Text(if (repo.private) "Private" else "Public", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        composable("create_repo") {
            var name by remember { mutableStateOf("") }
            var desc by remember { mutableStateOf("") }
            var isPrivate by remember { mutableStateOf(false) }
            var loading by remember { mutableStateOf(false) }
            
            Scaffold { padding ->
                Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                    Text("Create New Repository", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical=8.dp)) {
                        Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
                        Text("Private Repository")
                    }
                    Button(onClick = {
                        loading = true
                        scope.launch {
                            try {
                                val req = CreateRepoRequest(name, desc, isPrivate, true)
                                val repo = GithubApiManager.api.createRepo("Bearer $userPat", req)
                                navController.popBackStack()
                            } catch (e: Exception) {
                            } finally {
                                loading = false
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !loading && name.isNotBlank()) {
                        Text("Create")
                    }
                }
            }
        }
        composable("push/{owner}/{repo}") { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repoName = backStackEntry.arguments?.getString("repo") ?: ""
            var path by remember { mutableStateOf("README.md") }
            var content by remember { mutableStateOf("") }
            var commitMsg by remember { mutableStateOf("Update from AI Studio applet") }
            var loading by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("") }
            
            val context = LocalContext.current
            val folderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    loading = true
                    status = "Scanning folder..."
                    scope.launch {
                        try {
                            val contentResolver = context.contentResolver
                            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                            if (root != null) {
                                val filesToPush = mutableListOf<Pair<String, ByteArray>>() // path relative to root, and content
                                
                                fun scan(doc: androidx.documentfile.provider.DocumentFile, currentPath: String) {
                                    for (file in doc.listFiles()) {
                                        if (file.isDirectory) {
                                            scan(file, if (currentPath.isEmpty()) file.name!! else "$currentPath/${file.name}")
                                        } else {
                                            val filePath = if (currentPath.isEmpty()) file.name!! else "$currentPath/${file.name}"
                                            contentResolver.openInputStream(file.uri)?.use { stream ->
                                                filesToPush.add(filePath to stream.readBytes())
                                            }
                                        }
                                    }
                                }
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    scan(root, "")
                                }
                                
                                status = "Found ${filesToPush.size} files. Pushing..."
                                var pushedCount = 0
                                
                                for ((filePath, fileBytes) in filesToPush) {
                                    try {
                                        status = "Pushing $filePath (${pushedCount + 1}/${filesToPush.size})..."
                                        var sha: String? = null
                                        try {
                                            val res = GithubApiManager.api.getFileContent("Bearer $userPat", owner, repoName, filePath)
                                            sha = res.sha
                                        } catch (e: HttpException) {
                                            if (e.code() != 404) throw e
                                        }
                                        val b64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                                        val req = PutFileRequest("Upload folder: $filePath", b64, sha, "main")
                                        GithubApiManager.api.createOrUpdateFile("Bearer $userPat", owner, repoName, filePath, req)
                                        pushedCount++
                                    } catch(e: Exception) {
                                        // Ignore individual file error to continue pushing others
                                    }
                                }
                                status = "Success! Pushed $pushedCount/${filesToPush.size} files."
                            } else {
                                status = "Error mapping folder."
                            }
                        } catch (e: Exception) {
                             status = "Error: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                }
            }

            Scaffold { p ->
                Column(Modifier.padding(p).fillMaxSize().padding(16.dp)) {
                    Text("Push File to $owner/$repoName", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("File Path") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = commitMsg, onValueChange = { commitMsg = it }, label = { Text("Commit Message") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("File Content") }, modifier = Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(8.dp))
                    Text(status, color = if(status.contains("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            loading = true
                            status = "Pushing single file..."
                            scope.launch {
                                try {
                                    // 1. Get file SHA if exists
                                    var sha: String? = null
                                    try {
                                        val res = GithubApiManager.api.getFileContent("Bearer $userPat", owner, repoName, path)
                                        sha = res.sha
                                    } catch (e: HttpException) {
                                        if (e.code() != 404) throw e
                                    }
                                    
                                    val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
                                    val req = PutFileRequest(commitMsg, b64, sha, "main")
                                    GithubApiManager.api.createOrUpdateFile("Bearer $userPat", owner, repoName, path, req)
                                    status = "Success! File pushed."
                                } catch (e: Exception) {
                                    status = "Error: ${e.message}"
                                } finally {
                                    loading = false
                                }
                            }
                        }, modifier = Modifier.weight(1f), enabled = !loading) {
                            Text("Push File")
                        }
                        
                        OutlinedButton(onClick = {
                            folderLauncher.launch(null)
                        }, modifier = Modifier.weight(1f), enabled = !loading) {
                            Text("Push Folder")
                        }
                    }
                }
            }
        }
    }
}

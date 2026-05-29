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
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
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

import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
                                            navController.navigate("repo/${repo.full_name}?path=")
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
        composable(
            "repo/{owner}/{repo}?path={path}",
            arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repoName = backStackEntry.arguments?.getString("repo") ?: ""
            val path = backStackEntry.arguments?.getString("path") ?: ""
            
            var items by remember { mutableStateOf<List<GithubContentItem>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }

            LaunchedEffect(path) {
                loading = true
                try {
                    items = if (path.isEmpty()) {
                        GithubApiManager.api.getRootContent("Bearer $userPat", owner, repoName)
                    } else {
                        GithubApiManager.api.getDirectoryContent("Bearer $userPat", owner, repoName, path)
                    }
                } catch (e: Exception) {
                } finally {
                    loading = false
                }
            }
            
            Scaffold { p ->
                Column(Modifier.padding(p).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text(if (path.isEmpty()) repoName else path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (path.isEmpty()) {
                            TextButton(onClick = { navController.navigate("actions/$owner/$repoName") }) {
                                Text("Actions")
                            }
                        }
                        IconButton(onClick = { navController.navigate("push/$owner/$repoName?targetPath=$path") }) {
                            Icon(Icons.Default.Add, contentDescription = "Upload Here")
                        }
                    }
                    HorizontalDivider()
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(items) { item ->
                                val isDir = item.type == "dir"
                                ListItem(
                                    headlineContent = { Text(item.name) },
                                    leadingContent = { 
                                        Icon(if (isDir) Icons.Default.List else Icons.Default.Info, contentDescription = item.type)
                                    },
                                    modifier = Modifier.clickable {
                                        if (isDir) {
                                            navController.navigate("repo/$owner/$repoName?path=${item.path}")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        composable(
            "push/{owner}/{repo}?targetPath={targetPath}",
            arguments = listOf(navArgument("targetPath") { type = NavType.StringType; defaultValue = "" })
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repoName = backStackEntry.arguments?.getString("repo") ?: ""
            val targetPath = backStackEntry.arguments?.getString("targetPath") ?: ""
            var path by remember { mutableStateOf(if (targetPath.isEmpty()) "README.md" else "$targetPath/README.md") }
            var content by remember { mutableStateOf("") }
            var commitMsg by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("") }
            var uploadProgress by remember { mutableStateOf(0f) }
            var currentStage by remember { mutableStateOf("idle") } // "idle", "scanning", "uploading", "creating_tree", "creating_commit", "updating_ref", "success", "error"
            
            val context = LocalContext.current
            
            // Local metadata for file mapping. Stores pointer uri instead of loading entire file bytes to prevent crash
            class UploadFilePointer(
                val relativePath: String,
                val uri: android.net.Uri,
                val name: String,
                val size: Long
            )

            val EXCLUDE_DIRS = remember {
                setOf(".git", ".gradle", "build", "node_modules", ".idea", ".vscode", "bin", "obj", "out", ".build-outputs")
            }

            val folderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    loading = true
                    currentStage = "scanning"
                    status = "Scanning folder structure..."
                    uploadProgress = 0f
                    scope.launch {
                        try {
                            val contentResolver = context.contentResolver
                            val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                            if (root != null) {
                                val filesToPush = mutableListOf<UploadFilePointer>()
                                var scannedCount = 0
                                
                                fun scan(doc: androidx.documentfile.provider.DocumentFile, currentSubPath: String) {
                                    val list = doc.listFiles()
                                    for (file in list) {
                                        val name = file.name ?: continue
                                        if (file.isDirectory) {
                                            if (EXCLUDE_DIRS.contains(name)) continue
                                            scan(file, if (currentSubPath.isEmpty()) name else "$currentSubPath/$name")
                                        } else {
                                            scannedCount++
                                            val filePath = if (currentSubPath.isEmpty()) name else "$currentSubPath/$name"
                                            filesToPush.add(UploadFilePointer(filePath, file.uri, name, file.length()))
                                            status = "Scanned $scannedCount files (found: $name)"
                                        }
                                    }
                                }
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    scan(root, "")
                                }
                                
                                val totalFiles = filesToPush.size
                                if (totalFiles == 0) {
                                    status = "Error: Folder is empty or all contents are filter-ignored."
                                    currentStage = "error"
                                    loading = false
                                    return@launch
                                }
                                
                                status = "Fetched repository metadata..."
                                val branchRes = GithubApiManager.api.getBranch("Bearer $userPat", owner, repoName, "main")
                                val latestCommitSha = branchRes.commit.sha
                                val baseTreeSha = branchRes.commit.commit.tree.sha
                                
                                currentStage = "uploading"
                                var uploadedCount = 0
                                val lock = Any()
                                val semaphore = Semaphore(permits = 3) // maximum 3 concurrent uploads to prevent network & RAM overload
                                
                                val treeItems = filesToPush.map { filePtr ->
                                    async {
                                        semaphore.withPermit {
                                            // Lazily load file bytes into memory ONLY during upload turn
                                            val fileBytes = try {
                                                contentResolver.openInputStream(filePtr.uri)?.use { stream ->
                                                    stream.readBytes()
                                                } ?: ByteArray(0)
                                            } catch (e: Exception) {
                                                ByteArray(0)
                                            }
                                            
                                            val b64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                                            val blobSha = GithubApiManager.api.createBlob(
                                                "Bearer $userPat", owner, repoName, CreateBlobRequest(b64, "base64")
                                            ).sha
                                            
                                            val fullPath = if (targetPath.isEmpty()) filePtr.relativePath else "$targetPath/${filePtr.relativePath}"
                                            
                                            synchronized(lock) {
                                                uploadedCount++
                                                status = "Uploaded $uploadedCount/$totalFiles files: ${filePtr.name}"
                                                uploadProgress = uploadedCount.toFloat() / totalFiles
                                            }
                                            
                                            TreeItem(path = fullPath, mode = "100644", type = "blob", sha = blobSha)
                                        }
                                    }
                                }.awaitAll()
                                
                                currentStage = "creating_tree"
                                status = "Compiling Git tree registry on GitHub..."
                                val newTreeSha = GithubApiManager.api.createTree(
                                    "Bearer $userPat", owner, repoName, CreateTreeRequest(baseTreeSha, treeItems)
                                ).sha
                                
                                currentStage = "creating_commit"
                                status = "Creating git commit on main..."
                                val newCommitSha = GithubApiManager.api.createCommit(
                                    "Bearer $userPat", owner, repoName, 
                                    CreateCommitRequest(commitMsg, newTreeSha, listOf(latestCommitSha))
                                ).sha
                                
                                currentStage = "updating_ref"
                                status = "Updating Git main head branch..."
                                GithubApiManager.api.updateRef(
                                    "Bearer $userPat", owner, repoName, "main", UpdateRefRequest(newCommitSha, false)
                                )
                                
                                status = "Success! Loaded & Pushed $totalFiles files."
                                currentStage = "success"
                            } else {
                                status = "Error mapping folder structure."
                                currentStage = "error"
                            }
                        } catch (e: Exception) {
                             status = "Error: ${e.message}"
                             currentStage = "error"
                        } finally {
                            loading = false
                        }
                    }
                }
            }

            Scaffold { p ->
                Column(Modifier.padding(p).fillMaxSize().padding(16.dp)) {
                    val displayPath = if (targetPath.isEmpty()) "/" else "/$targetPath"
                    Text("Upload to $owner/$repoName$displayPath", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    
                    if (loading) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                val stageHeading = when (currentStage) {
                                    "scanning" -> "Scanning Directory Files..."
                                    "uploading" -> "Concurrently Uploading Blobs..."
                                    "creating_tree" -> "Generating Git Tree..."
                                    "creating_commit" -> "Creating New Commit..."
                                    "updating_ref" -> "Finalizing Branch Reference..."
                                    else -> "Processing task..."
                                }
                                Text(stageHeading, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                if (currentStage == "uploading") {
                                    LinearProgressIndicator(
                                        progress = uploadProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        OutlinedTextField(value = path, onValueChange = { path = it }, label = { Text("File Path") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = commitMsg,
                            onValueChange = { commitMsg = it },
                            label = { Text("Commit Message") },
                            isError = commitMsg.isBlank(),
                            supportingText = {
                                if (commitMsg.isBlank()) {
                                    Text("Commit message is required", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("File Content") }, modifier = Modifier.fillMaxWidth().weight(1f))
                        Spacer(Modifier.height(8.dp))
                        if (status.isNotEmpty()) {
                            Text(status, color = if(status.contains("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            loading = true
                            currentStage = "uploading"
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
                                    currentStage = "success"
                                } catch (e: Exception) {
                                    status = "Error: ${e.message}"
                                    currentStage = "error"
                                } finally {
                                    loading = false
                                }
                            }
                        }, modifier = Modifier.weight(1f), enabled = !loading && commitMsg.isNotBlank()) {
                            Text("Push File")
                        }
                        
                        OutlinedButton(onClick = {
                            folderLauncher.launch(null)
                        }, modifier = Modifier.weight(1f), enabled = !loading && commitMsg.isNotBlank()) {
                            Text("Push Folder")
                        }
                    }
                }
            }
        }
        composable("actions/{owner}/{repo}") { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repoName = backStackEntry.arguments?.getString("repo") ?: ""
            
            var runs by remember { mutableStateOf<List<WorkflowRun>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                loading = true
                try {
                    val res = GithubApiManager.api.getWorkflowRuns("Bearer $userPat", owner, repoName)
                    runs = res.workflow_runs
                } catch (e: Exception) {
                } finally {
                    loading = false
                }
            }
            
            Scaffold { p ->
                Column(Modifier.padding(p).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text("Actions - $repoName", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(runs) { run ->
                                ListItem(
                                    headlineContent = { Text(run.display_title ?: run.name ?: "Run #${run.id}") },
                                    supportingContent = { Text("${run.status} - ${run.conclusion ?: "pending"}") },
                                    modifier = Modifier.clickable {
                                        navController.navigate("action_run/$owner/$repoName/${run.id}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        composable(
            "action_run/{owner}/{repo}/{run_id}",
            arguments = listOf(navArgument("run_id") { type = NavType.LongType })
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner") ?: ""
            val repoName = backStackEntry.arguments?.getString("repo") ?: ""
            val runId = backStackEntry.arguments?.getLong("run_id") ?: 0L
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            
            var jobs by remember { mutableStateOf<List<WorkflowJob>>(emptyList()) }
            var artifacts by remember { mutableStateOf<List<Artifact>>(emptyList()) }
            var loading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                loading = true
                try {
                    val jobsRes = async { GithubApiManager.api.getWorkflowRunJobs("Bearer $userPat", owner, repoName, runId) }
                    val artifactsRes = async { GithubApiManager.api.getRunArtifacts("Bearer $userPat", owner, repoName, runId) }
                    jobs = jobsRes.await().jobs
                    artifacts = artifactsRes.await().artifacts
                } catch (e: Exception) {
                } finally {
                    loading = false
                }
            }
            
            Scaffold { p ->
                Column(Modifier.padding(p).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Text("Run #$runId", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (artifacts.isNotEmpty()) {
                                item {
                                    Text("Artifacts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                                }
                                items(artifacts) { artifact ->
                                    var isDownloading by remember { mutableStateOf(false) }
                                    var progress by remember { mutableStateOf(-1) }
                                    val sizeMb = artifact.size_in_bytes / (1024 * 1024.0)
                                    ListItem(
                                        headlineContent = { Text(artifact.name ?: "Unknown") },
                                        supportingContent = { Text(String.format("%.2f MB", sizeMb)) },
                                        trailingContent = {
                                            if (!artifact.expired) {
                                                                val startDownload = {
                                                                    isDownloading = true
                                                                    progress = 0
                                                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                                        val fileName = artifact.name ?: "artifact"
                                                                        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                                                                        val channelId = "downloads_gh"
                                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                                            val channel = android.app.NotificationChannel(channelId, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW)
                                                                            notificationManager.createNotificationChannel(channel)
                                                                        }
                                                                        val notificationId = artifact.id?.hashCode() ?: fileName.hashCode()
                                                                        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                                                                            .setContentTitle("Downloading $fileName")
                                                                            .setSmallIcon(android.R.drawable.stat_sys_download)
                                                                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                                                                            .setOngoing(true)
                                                                        
                                                                        try {
                                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                                android.widget.Toast.makeText(context, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
                                                                            }
                                                                            
                                                                            val url = artifact.archive_download_url
                                                                            val request = okhttp3.Request.Builder()
                                                                                .url(url)
                                                                                .header("Authorization", "Bearer $userPat")
                                                                                .build()
                                                                            
                                                                            val clientNoRedirects = GithubApiManager.client.newBuilder()
                                                                                .followRedirects(false)
                                                                                .followSslRedirects(false)
                                                                                .build()
                                                                            
                                                                            val response = clientNoRedirects.newCall(request).execute()
                                                                            val finalUrl = response.header("Location") ?: response.request.url.toString()
                                                                            response.close()
                                                                            
                                                                            val downloadReq = okhttp3.Request.Builder().url(finalUrl).build()
                                                                            val downloadResp = GithubApiManager.client.newCall(downloadReq).execute()
                                                                            val body = downloadResp.body
                                                                            if (body == null) throw Exception("Empty response body")
                                                                            
                                                                            val totalSize = body.contentLength()
                                                                            var downloadedSize = 0L
                                                                            
                                                                            val resolver = context.contentResolver
                                                                            val contentUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                                                val values = android.content.ContentValues().apply {
                                                                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "$fileName.ghdownload")
                                                                                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                                                                                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                                                                                }
                                                                                resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                                                            } else null
                                                                            
                                                                            var outputStream: java.io.OutputStream? = null
                                                                            var outputFile: java.io.File? = null
                                                                            
                                                                            if (contentUri != null) {
                                                                                outputStream = resolver.openOutputStream(contentUri)
                                                                            } else {
                                                                                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                                                                                if (!dir.exists()) dir.mkdirs()
                                                                                outputFile = java.io.File(dir, "$fileName.ghdownload")
                                                                                outputStream = java.io.FileOutputStream(outputFile)
                                                                            }
                                                                            
                                                                            if (outputStream == null) {
                                                                                // Fallback to app directory
                                                                                val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                                                                if (dir != null) {
                                                                                    if (!dir.exists()) dir.mkdirs()
                                                                                    outputFile = java.io.File(dir, "$fileName.ghdownload")
                                                                                    outputStream = java.io.FileOutputStream(outputFile)
                                                                                }
                                                                            }
                                                                            
                                                                            if (outputStream == null) throw Exception("Could not create output file")
                                                                            
                                                                            val inputStream = body.byteStream()
                                                                            val buffer = ByteArray(8192)
                                                                            var read: Int
                                                                            var lastUpdate = System.currentTimeMillis()
                                                                            var currentProgress = 0
                                                                            
                                                                            while (inputStream.read(buffer).also { read = it } != -1) {
                                                                                outputStream.write(buffer, 0, read)
                                                                                downloadedSize += read
                                                                                val p = if (totalSize > 0) ((downloadedSize * 100) / totalSize).toInt() else 0
                                                                                if (p > currentProgress) {
                                                                                    currentProgress = p
                                                                                    val now = System.currentTimeMillis()
                                                                                    if (now - lastUpdate > 500 || p == 100) {
                                                                                        lastUpdate = now
                                                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { progress = p }
                                                                                        builder.setProgress(100, p, totalSize <= 0)
                                                                                        notificationManager.notify(notificationId, builder.build())
                                                                                    }
                                                                                }
                                                                            }
                                                                            outputStream.flush()
                                                                            outputStream.close()
                                                                            inputStream.close()
                                                                            
                                                                            if (contentUri != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                                                val values = android.content.ContentValues().apply {
                                                                                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "$fileName.zip")
                                                                                    put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                                                                                }
                                                                                resolver.update(contentUri, values, null, null)
                                                                            } else if (outputFile != null) {
                                                                                val newFile = java.io.File(outputFile.parent, "$fileName.zip")
                                                                                if (outputFile.exists()) outputFile.renameTo(newFile)
                                                                            }
                                                                            
                                                                            builder.setContentTitle("$fileName downloaded")
                                                                                .setProgress(0, 0, false)
                                                                                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                                                                                .setOngoing(false)
                                                                            notificationManager.notify(notificationId, builder.build())
                                                                            
                                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                                android.widget.Toast.makeText(context, "Download complete", android.widget.Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            e.printStackTrace()
                                                                            builder.setContentTitle("Download failed: $fileName")
                                                                                .setContentText(e.message)
                                                                                .setProgress(0, 0, false)
                                                                                .setOngoing(false)
                                                                            notificationManager.notify(notificationId, builder.build())
                                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                                android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                                            }
                                                                        } finally {
                                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                                isDownloading = false
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                Button(
                                                                    onClick = {
                                                                        if (android.os.Build.VERSION.SDK_INT <= 28 &&
                                                                            androidx.core.content.ContextCompat.checkSelfPermission(
                                                                                context,
                                                                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                                                        ) {
                                                                            val activity = context as? android.app.Activity
                                                                            if (activity != null) {
                                                                                androidx.core.app.ActivityCompat.requestPermissions(
                                                                                    activity,
                                                                                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                                                                    101
                                                                                )
                                                                                android.widget.Toast.makeText(context, "Requesting storage permission. Please grant it and click download again.", android.widget.Toast.LENGTH_LONG).show()
                                                                            } else {
                                                                                startDownload()
                                                                            }
                                                                        } else {
                                                                            startDownload()
                                                                        }
                                                                    },
                                                                    enabled = !isDownloading
                                                                ) {
                                                                    if (isDownloading) {
                                                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Text(if (progress >= 0) "$progress%" else "...")
                                                                    } else {
                                                                        Text("Download")
                                                                    }
                                                                }
                                            } else {
                                                Text("Expired", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    )
                                }
                            }
                            
                            item {
                                Text("Jobs & Logs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                            }
                            items(jobs) { job ->
                                var expanded by remember { mutableStateOf(false) }
                                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { expanded = !expanded }) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(job.name ?: "Job", style = MaterialTheme.typography.titleMedium)
                                        Text("Status: ${job.status ?: "unknown"} - ${job.conclusion ?: "pending"}", style = MaterialTheme.typography.bodySmall)
                                        
                                        if (expanded) {
                                            Spacer(Modifier.height(8.dp))
                                            job.steps?.forEach { step ->
                                                val statusColor = when (step.conclusion) {
                                                    "success" -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                    "failure" -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                    Text("• ", color = statusColor)
                                                    Text(step.name ?: "Step", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                    Text(step.conclusion ?: step.status ?: "", style = MaterialTheme.typography.bodySmall, color = statusColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.gitpusher.mobile

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@JsonClass(generateAdapter = true)
data class GithubUser(
    val login: String,
    val name: String?,
    val avatar_url: String?
)

@JsonClass(generateAdapter = true)
data class GithubRepo(
    val name: String,
    val description: String?,
    val private: Boolean,
    val full_name: String,
    val html_url: String,
    val default_branch: String = "main"
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    val auto_init: Boolean = true
)

@JsonClass(generateAdapter = true)
data class FileCommitRequest(
    val message: String,
    val content: String,
    val branch: String
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val content: ContentData?,
    val sha: String?
)

@JsonClass(generateAdapter = true)
data class ContentData(
    val name: String,
    val sha: String
)

@JsonClass(generateAdapter = true)
data class PutFileRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String
)

@JsonClass(generateAdapter = true)
data class GithubContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val type: String // "file" or "dir"
)

@JsonClass(generateAdapter = true)
data class BranchResponse(val commit: BranchCommit)

@JsonClass(generateAdapter = true)
data class BranchCommit(val sha: String, val commit: CommitData)

@JsonClass(generateAdapter = true)
data class CommitData(val tree: TreeData)

@JsonClass(generateAdapter = true)
data class TreeData(val sha: String)

@JsonClass(generateAdapter = true)
data class CreateBlobRequest(val content: String, val encoding: String = "base64")

@JsonClass(generateAdapter = true)
data class BlobResponse(val sha: String)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(val base_tree: String, val tree: List<TreeItem>)

@JsonClass(generateAdapter = true)
data class TreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String? = null,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class GitCommitResponse(val sha: String)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(val message: String, val tree: String, val parents: List<String>)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(val sha: String, val force: Boolean = false)

@JsonClass(generateAdapter = true)
data class WorkflowRunsResponse(
    val total_count: Int,
    val workflow_runs: List<WorkflowRun>
)

@JsonClass(generateAdapter = true)
data class WorkflowRun(
    val id: Long,
    val name: String?,
    val display_title: String?,
    val status: String?,
    val conclusion: String?,
    val head_branch: String?,
    val created_at: String?,
    val updated_at: String?
)

@JsonClass(generateAdapter = true)
data class WorkflowRunJobsResponse(
    val total_count: Int,
    val jobs: List<WorkflowJob>
)

@JsonClass(generateAdapter = true)
data class WorkflowJob(
    val id: Long,
    val name: String?,
    val status: String?,
    val conclusion: String?,
    val html_url: String?,
    val steps: List<WorkflowStep>?
)

@JsonClass(generateAdapter = true)
data class WorkflowStep(
    val name: String?,
    val status: String?,
    val conclusion: String?,
    val number: Int
)

@JsonClass(generateAdapter = true)
data class ArtifactsResponse(
    val total_count: Int,
    val artifacts: List<Artifact>
)

@JsonClass(generateAdapter = true)
data class Artifact(
    val id: Long,
    val name: String?,
    val size_in_bytes: Long,
    val archive_download_url: String,
    val expired: Boolean
)

interface GithubApiService {
    @GET("user")
    suspend fun getUser(@Header("Authorization") token: String): GithubUser

    @GET("user/repos?sort=updated")
    suspend fun getRepos(@Header("Authorization") token: String): List<GithubRepo>

    @POST("user/repos")
    suspend fun createRepo(
        @Header("Authorization") token: String,
        @Body request: CreateRepoRequest
    ): GithubRepo

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): ContentResponse

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: PutFileRequest
    )
    
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getDirectoryContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): List<GithubContentItem>

    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRootContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GithubContentItem>

    @GET("repos/{owner}/{repo}/branches/{branch}")
    suspend fun getBranch(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String
    ): BranchResponse

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateBlobRequest
    ): BlobResponse

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateTreeRequest
    ): TreeData

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateCommitRequest
    ): GitCommitResponse

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body request: UpdateRefRequest
    )

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): WorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/jobs")
    suspend fun getWorkflowRunJobs(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): WorkflowRunJobsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getRunArtifacts(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): ArtifactsResponse
}

object GithubApiManager {
    val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()
        
    private val r = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        
    val api: GithubApiService = r.create(GithubApiService::class.java)
}

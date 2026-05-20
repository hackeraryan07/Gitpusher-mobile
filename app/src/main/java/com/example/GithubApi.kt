package com.example

import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
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
    val html_url: String
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
    val content: String, // Base64 encoded inside the request mapped by ViewModel, wait no Github expects base64 content
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
    ): ContentResponse // May fail with 404 if not exists

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: PutFileRequest
    )
}

object GithubApiManager {
    private val r = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply{level=HttpLoggingInterceptor.Level.BODY}).build())
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        
    val api: GithubApiService = r.create(GithubApiService::class.java)
}

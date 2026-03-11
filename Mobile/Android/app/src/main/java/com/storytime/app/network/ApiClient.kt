package com.storytime.app.network

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.storytime.app.data.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PLACEHOLDER_BASE = "http://placeholder.local/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private var preferencesManager: PreferencesManager? = null
    private var _retrofit: Retrofit? = null
    private var _service: ApiService? = null
    private var _sseClient: SSEClient? = null

    fun init(prefs: PreferencesManager) {
        preferencesManager = prefs

        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(prefs))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        _retrofit = Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        _service = _retrofit!!.create(ApiService::class.java)

        // SSE client with longer timeout
        val sseOkHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        _sseClient = SSEClient(sseOkHttp, json)
    }

    val service: ApiService
        get() = _service ?: throw IllegalStateException("ApiClient not initialized")

    val sseClient: SSEClient
        get() = _sseClient ?: throw IllegalStateException("ApiClient not initialized")

    fun getBaseUrl(): String {
        val prefs = preferencesManager ?: return "http://10.0.2.2:3002"
        return runBlocking { prefs.serverUrl.first() }
    }

    /** Build a full URL for image loading (Coil). */
    fun imageUrl(path: String): String {
        val base = getBaseUrl().trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return base + cleanPath
    }
}

private class DynamicBaseUrlInterceptor(
    private val prefs: PreferencesManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val baseUrl = runBlocking { prefs.serverUrl.first() }.trimEnd('/')

        // Replace the placeholder host with the real base URL
        val originalUrl = original.url.toString()
        val path = original.url.encodedPath
        val query = original.url.query

        val newUrlStr = buildString {
            append(baseUrl)
            append(path)
            if (query != null) {
                append("?")
                append(query)
            }
        }

        Log.d("ApiClient", "Request: ${original.method} $originalUrl -> $newUrlStr")

        val newUrl = newUrlStr.toHttpUrl()
        val newRequest = original.newBuilder().url(newUrl).build()
        val response = chain.proceed(newRequest)

        Log.d("ApiClient", "Response: ${response.code} ${response.message} for $newUrlStr (content-type=${response.header("Content-Type")}, content-length=${response.header("Content-Length")})")

        return response
    }
}

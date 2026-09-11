package com.example.data.api

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.util.NetworkMonitor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {
  private const val TAG = "ApiClient"

  val isEmulator: Boolean
    get() = (Build.FINGERPRINT.startsWith("generic")
        || Build.FINGERPRINT.startsWith("unknown")
        || Build.MODEL.contains("google_sdk")
        || Build.MODEL.contains("Emulator")
        || Build.MODEL.contains("Android SDK built for x86")
        || Build.MANUFACTURER.contains("Genymotion")
        || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        || "google_sdk" == Build.PRODUCT)

  const val DEV_LAN_IP = "192.168.11.16"
  const val EMULATOR_HOST = "10.0.2.2"
  const val USB_LOCAL_HOST = "127.0.0.1"
  const val PRODUCTION_RENDER_URL = "https://quickbite-server-pjaf.onrender.com/api/"

  val candidateHosts: List<String>
    get() = if (isEmulator) {
      listOf(EMULATOR_HOST, DEV_LAN_IP, "192.168.17.21", USB_LOCAL_HOST)
    } else {
      listOf(DEV_LAN_IP, "192.168.17.21", USB_LOCAL_HOST, EMULATOR_HOST)
    }

  var baseUrl: String = PRODUCTION_RENDER_URL
    set(value) {
      field = if (value.endsWith("/")) value else "$value/"
      retrofitInstance = buildRetrofit()
      apiServiceInstance = retrofitInstance.create(QuickbiteApiService::class.java)
    }

  @Volatile
  private var httpCache: Cache? = null

  fun initialize(context: Context) {
    if (httpCache == null) {
      synchronized(this) {
        if (httpCache == null) {
          try {
            val cacheDir = File(context.applicationContext.cacheDir, "quickbite_http_cache")
            val cacheSize = 50L * 1024L * 1024L // 50 MiB
            httpCache = Cache(cacheDir, cacheSize)
            okHttpClientInstance = buildOkHttpClient()
            retrofitInstance = buildRetrofit()
            apiServiceInstance = retrofitInstance.create(QuickbiteApiService::class.java)
            Log.i(TAG, "Initialized OkHttp disk cache (50MB) at ${cacheDir.absolutePath}")
          } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize disk cache: ${e.message}")
          }
        }
      }
    }
  }

  private val hostFallbackInterceptor = Interceptor { chain ->
    val originalRequest = chain.request()
    val originalUrl = originalRequest.url
    val originalHost = originalUrl.host

    var originalResponse: okhttp3.Response? = null
    var originalException: IOException? = null

    try {
      val res = chain.proceed(originalRequest)
      // If production Render returned 404 on /queue specifically (e.g. pending deployment), allow trying local candidate hosts
      if (!(res.code == 404 && originalUrl.encodedPath.contains("/queue"))) {
        return@Interceptor res
      }
      originalResponse = res
    } catch (e: IOException) {
      originalException = e
    }

    // Try candidate hosts on local dev port 5000
    val hostsToTry = candidateHosts.toMutableList()
    hostsToTry.remove(originalHost)

    for (targetHost in hostsToTry) {
      try {
        val newUrl = originalUrl.newBuilder()
          .scheme("http")
          .host(targetHost)
          .port(5000)
          .build()
        val newRequest = originalRequest.newBuilder()
          .url(newUrl)
          .build()

        val response = chain.proceed(newRequest)
        if (response.isSuccessful || response.code < 500) {
          Log.i(TAG, "Connected to backend on $targetHost:5000 (auto-fallback from $originalHost)")
          originalResponse?.close()
          return@Interceptor response
        }
        response.close()
      } catch (e: IOException) {
        // Continue to next candidate host
      }
    }

    if (originalResponse != null) {
      return@Interceptor originalResponse
    }
    throw originalException ?: IOException("Unable to reach backend. Tried: $originalHost and $hostsToTry:5000")
  }

  /**
   * Offline Cache Interceptor:
   * If the device is currently offline, force reading from disk cache.
   * If a GET request fails due to network drop or timeout, seamlessly fallback to cached response.
   */
  private val offlineCacheInterceptor = Interceptor { chain ->
    var request = chain.request()
    val isGet = request.method.equals("GET", ignoreCase = true)

    if (isGet && !NetworkMonitor.isCurrentlyOnline()) {
      // Force cache if completely offline
      request = request.newBuilder()
        .cacheControl(
          CacheControl.Builder()
            .onlyIfCached()
            .maxStale(7, TimeUnit.DAYS)
            .build()
        )
        .build()
    }

    try {
      chain.proceed(request)
    } catch (e: IOException) {
      if (isGet) {
        Log.w(TAG, "Network request failed (${e.message}), attempting stale cache fallback...")
        val fallbackRequest = request.newBuilder()
          .cacheControl(
            CacheControl.Builder()
              .onlyIfCached()
              .maxStale(7, TimeUnit.DAYS)
              .build()
          )
          .build()
        try {
          val fallbackResponse = chain.proceed(fallbackRequest)
          if (fallbackResponse.isSuccessful || fallbackResponse.code == 304) {
            Log.i(TAG, "Served cached response for ${request.url}")
            return@Interceptor fallbackResponse
          }
        } catch (ignored: Exception) {
          // Fall back to original error if no cache entry exists
        }
      }
      throw e
    }
  }

  /**
   * Network Interceptor:
   * Rewrites response headers for GET requests so OkHttp caches responses for 15s when online,
   * deduplicating repeated calls during scrolling, recomposition, or switching tabs.
   */
  private val responseCacheInterceptor = Interceptor { chain ->
    val response = chain.proceed(chain.request())
    val path = chain.request().url.encodedPath
    if (chain.request().method.equals("GET", ignoreCase = true)) {
      if (path.contains("/queue")) {
        // Never cache live queue responses - always serve freshest server state
        response.newBuilder()
          .header("Cache-Control", "no-cache, no-store, must-revalidate")
          .removeHeader("Pragma")
          .build()
      } else {
        response.newBuilder()
          .header("Cache-Control", "public, max-age=15")
          .removeHeader("Pragma")
          .build()
      }
    } else {
      response
    }
  }

  private val moshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private fun buildOkHttpClient(): OkHttpClient {
    val builder = OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .addInterceptor(offlineCacheInterceptor)
      .addInterceptor(hostFallbackInterceptor)
      .addNetworkInterceptor(responseCacheInterceptor)
      .addInterceptor(
        HttpLoggingInterceptor().apply {
          level = HttpLoggingInterceptor.Level.BASIC
        }
      )

    httpCache?.let { builder.cache(it) }
    return builder.build()
  }

  private var okHttpClientInstance: OkHttpClient = buildOkHttpClient()
  private var retrofitInstance: Retrofit = buildRetrofit()
  private var apiServiceInstance: QuickbiteApiService = retrofitInstance.create(QuickbiteApiService::class.java)

  val apiService: QuickbiteApiService
    get() = apiServiceInstance

  private fun buildRetrofit(): Retrofit {
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClientInstance)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }
}

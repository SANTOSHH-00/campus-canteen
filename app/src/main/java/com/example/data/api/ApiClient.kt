// ApiClient: Core utility and helper functions
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

  const val DEV_LAN_IP = "10.35.31.231"
  const val EMULATOR_HOST = "10.0.2.2"
  const val USB_LOCAL_HOST = "127.0.0.1"
  const val PRODUCTION_RENDER_URL = "https://quickbite-server-pjaf.onrender.com/api/"

  val candidateHosts: List<String>
    get() = if (isEmulator) {
      listOf(EMULATOR_HOST, DEV_LAN_IP, USB_LOCAL_HOST)
    } else {
      listOf(DEV_LAN_IP, USB_LOCAL_HOST, EMULATOR_HOST)
    }

  var baseUrl: String = PRODUCTION_RENDER_URL
    set(value) {
      field = if (value.endsWith("/")) value else "$value/"
      retrofitInstance = buildRetrofit()
      apiServiceInstance = retrofitInstance.create(QuickbiteApiService::class.java)
    }

  fun initialize(context: Context) {
  }

  private val noCacheInterceptor = Interceptor { chain ->
    val request = chain.request().newBuilder()
      .header("Cache-Control", "no-cache, no-store")
      .header("Pragma", "no-cache")
      .build()
    chain.proceed(request)
  }

  private val hostFallbackInterceptor = Interceptor { chain ->
    val originalRequest = chain.request()
    val originalUrl = originalRequest.url
    val originalHost = originalUrl.host

    var originalException: IOException? = null

    try {
      val res = chain.proceed(originalRequest)
      // Return response immediately - do not delay on 4xx/5xx with slow fallback loops
      return@Interceptor res
    } catch (e: IOException) {
      originalException = e
    }

    // Only attempt local emulator/localhost fallback if not targeting production Render
    if (!originalHost.contains("onrender.com")) {
      val targetFallback = if (isEmulator) EMULATOR_HOST else USB_LOCAL_HOST
      if (targetFallback != originalHost) {
        try {
          val newUrl = originalUrl.newBuilder()
            .scheme("http")
            .host(targetFallback)
            .port(5000)
            .build()
          val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()
          val response = chain.proceed(newRequest)
          if (response.isSuccessful || response.code < 500) {
            Log.i(TAG, "Connected to backend on $targetFallback:5000 (fallback from $originalHost)")
            return@Interceptor response
          }
          response.close()
        } catch (_: Exception) {}
      }
    }

    throw originalException ?: IOException("Unable to reach backend: $originalHost")
  }

  val moshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private fun buildOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(10, TimeUnit.SECONDS)
      .writeTimeout(10, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
      .addInterceptor(noCacheInterceptor)
      .addInterceptor(hostFallbackInterceptor)
      .addInterceptor(
        HttpLoggingInterceptor().apply {
          level = HttpLoggingInterceptor.Level.BASIC
        }
      )
      .build()
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

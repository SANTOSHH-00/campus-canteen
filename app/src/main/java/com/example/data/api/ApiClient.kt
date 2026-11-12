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

  const val DEV_LAN_IP = "192.168.17.26"
  const val EMULATOR_HOST = "10.0.2.2"
  const val USB_LOCAL_HOST = "127.0.0.1"
  const val PRODUCTION_RENDER_URL = "https://quickbite-server-pjaf.onrender.com/api/"

  val candidateHosts: List<String>
    get() = if (isEmulator) {
      listOf(EMULATOR_HOST, DEV_LAN_IP, "192.168.11.16", "192.168.17.21", USB_LOCAL_HOST)
    } else {
      listOf(DEV_LAN_IP, "192.168.11.16", "192.168.17.21", USB_LOCAL_HOST, EMULATOR_HOST)
    }

  var baseUrl: String = PRODUCTION_RENDER_URL
    set(value) {
      field = if (value.endsWith("/")) value else "$value/"
      retrofitInstance = buildRetrofit()
      apiServiceInstance = retrofitInstance.create(QuickbiteApiService::class.java)
    }

  fun initialize(context: Context) {
    // No-op: disk response caching removed per requirement
  }

  private val hostFallbackInterceptor = Interceptor { chain ->
    val originalRequest = chain.request()
    val originalUrl = originalRequest.url
    val originalHost = originalUrl.host

    var originalException: IOException? = null

    try {
      val res = chain.proceed(originalRequest)
      // If production request succeeded or returned normal 2xx/3xx, return it immediately
      if (res.isSuccessful || (res.code != 404 && res.code < 500)) {
        return@Interceptor res
      }
      // If Render returned 404 or 5xx, CLOSE the response before attempting local candidate hosts
      res.close()
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
          return@Interceptor response
        }
        response.close()
      } catch (e: Exception) {
        // Continue to next candidate host
      }
    }

    throw originalException ?: IOException("Unable to reach backend on Render or local candidate hosts: $hostsToTry:5000")
  }

  val moshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private fun buildOkHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .retryOnConnectionFailure(true)
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

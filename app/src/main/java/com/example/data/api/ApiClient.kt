package com.example.data.api

import android.os.Build
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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

  const val DEV_LAN_IP = "192.168.17.21"
  const val EMULATOR_HOST = "10.0.2.2"
  const val USB_LOCAL_HOST = "127.0.0.1"

  val candidateHosts: List<String>
    get() = if (isEmulator) {
      listOf(EMULATOR_HOST, DEV_LAN_IP, USB_LOCAL_HOST)
    } else {
      listOf(DEV_LAN_IP, USB_LOCAL_HOST, EMULATOR_HOST)
    }

  var baseUrl: String = if (isEmulator) {
    "http://$EMULATOR_HOST:5000/api/"
  } else {
    "http://$DEV_LAN_IP:5000/api/"
  }
    set(value) {
      field = if (value.endsWith("/")) value else "$value/"
      retrofitInstance = buildRetrofit()
      apiServiceInstance = retrofitInstance.create(QuickbiteApiService::class.java)
    }

  private val hostFallbackInterceptor = Interceptor { chain ->
    val originalRequest = chain.request()
    val originalUrl = originalRequest.url
    val originalHost = originalUrl.host

    // If not communicating with backend port 5000, pass through
    if (originalUrl.port != 5000) {
      return@Interceptor chain.proceed(originalRequest)
    }

    val hostsToTry = candidateHosts.toMutableList()
    hostsToTry.remove(originalHost)
    hostsToTry.add(0, originalHost)

    var lastException: IOException? = null

    for (targetHost in hostsToTry) {
      try {
        val newUrl = originalUrl.newBuilder()
          .host(targetHost)
          .build()
        val newRequest = originalRequest.newBuilder()
          .url(newUrl)
          .build()

        val response = chain.proceed(newRequest)
        if (targetHost != originalHost) {
          Log.i(TAG, "Connected to backend on $targetHost:5000 (auto-switched from $originalHost)")
        }
        return@Interceptor response
      } catch (e: IOException) {
        lastException = e
        Log.w(TAG, "Connection to $targetHost:5000 failed: ${e.message}. Attempting fallback...")
      }
    }

    throw lastException ?: IOException("Unable to reach backend on port 5000. Tried: $hostsToTry")
  }

  private val moshi: Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .addInterceptor(hostFallbackInterceptor)
    .addInterceptor(
      HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
      }
    )
    .build()

  private var retrofitInstance: Retrofit = buildRetrofit()
  private var apiServiceInstance: QuickbiteApiService = retrofitInstance.create(QuickbiteApiService::class.java)

  val apiService: QuickbiteApiService
    get() = apiServiceInstance

  private fun buildRetrofit(): Retrofit {
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }
}

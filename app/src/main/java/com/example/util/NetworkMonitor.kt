package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
  private const val TAG = "NetworkMonitor"

  private val _isOnline = MutableStateFlow(true)
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  @Volatile
  private var connectivityManager: ConnectivityManager? = null

  @Synchronized
  fun initialize(context: Context) {
    if (connectivityManager != null) return
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    connectivityManager = cm

    if (cm == null) {
      _isOnline.value = true
      return
    }

    _isOnline.value = checkCurrentConnectivity(cm)

    try {
      val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

      cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          Log.i(TAG, "Network connection restored (online)")
          _isOnline.value = true
        }

        override fun onLost(network: Network) {
          val stillOnline = checkCurrentConnectivity(cm)
          Log.w(TAG, "Network lost. Online: $stillOnline")
          _isOnline.value = stillOnline
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
          val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
          _isOnline.value = hasInternet
        }
      })
    } catch (e: Exception) {
      Log.w(TAG, "Failed to register network callback: ${e.message}")
    }
  }

  fun isCurrentlyOnline(): Boolean {
    val cm = connectivityManager ?: return _isOnline.value
    return checkCurrentConnectivity(cm)
  }

  private fun checkCurrentConnectivity(cm: ConnectivityManager): Boolean {
    return try {
      val activeNetwork = cm.activeNetwork ?: return false
      val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
      capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
      true
    }
  }
}

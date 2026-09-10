package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

/**
 * Centralized Firebase provider and initializer for MessQ.
 * Gracefully handles both fully-configured Firebase environments (via google-services.json)
 * and development/offline environments without crashing.
 */
object FirebaseConfig {
  private const val TAG = "MessQFirebase"

  // Collection Names per MessQ Architecture Specification
  const val COLLECTION_USERS = "users"
  const val COLLECTION_OWNERS = "owners"
  const val COLLECTION_CANTEENS = "canteens"
  const val COLLECTION_ITEMS = "items"
  const val COLLECTION_ORDERS = "orders"
  const val COLLECTION_NOTIFICATIONS = "notifications"

  @Volatile
  private var isInitialized = false

  var hasLiveFirebaseConfig = false
    private set

  fun initialize(context: Context) {
    if (isInitialized) return
    synchronized(this) {
      if (isInitialized) return
      try {
        if (FirebaseApp.getApps(context).isEmpty()) {
          // If google-services.json was omitted, provide local fallback options
          val options = FirebaseOptions.Builder()
            .setApplicationId("com.aistudio.campuscanteen.fxdz")
            .setApiKey("AIzaSyMessQDummyApiKeyForLocalInit0000")
            .setProjectId("campus-canteen-messq")
            .setStorageBucket("campus-canteen-messq.appspot.com")
            .build()
          FirebaseApp.initializeApp(context, options)
          hasLiveFirebaseConfig = false
          Log.i(TAG, "Firebase initialized with local fallback configuration.")
        } else {
          hasLiveFirebaseConfig = true
          Log.i(TAG, "Firebase initialized via Google Services provider.")
        }

        // Configure Firestore offline persistence
        try {
          val firestore = FirebaseFirestore.getInstance()
          val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
              com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build()
            )
            .build()
          firestore.firestoreSettings = settings
        } catch (e: Exception) {
          Log.w(TAG, "Could not apply Firestore offline settings: ${e.message}")
        }

        isInitialized = true
      } catch (e: Exception) {
        Log.e(TAG, "Firebase initialization warning: ${e.message}", e)
      }
    }
  }

  val auth: FirebaseAuth?
    get() = try {
      FirebaseAuth.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseAuth unavailable: ${e.message}")
      null
    }

  val firestore: FirebaseFirestore?
    get() = try {
      FirebaseFirestore.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseFirestore unavailable: ${e.message}")
      null
    }

  val storage: FirebaseStorage?
    get() = try {
      FirebaseStorage.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseStorage unavailable: ${e.message}")
      null
    }

  val messaging: FirebaseMessaging?
    get() = try {
      FirebaseMessaging.getInstance()
    } catch (e: Exception) {
      Log.w(TAG, "FirebaseMessaging unavailable: ${e.message}")
      null
    }
}

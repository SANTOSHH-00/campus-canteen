package com.example.data.session

import android.content.Context
import android.content.SharedPreferences
import com.example.data.firebase.OwnerDocument
import com.example.ui.state.UserProfile

/**
 * Centralized session manager backed by Android SharedPreferences.
 * Persists login state for both students and canteen owners so sessions
 * remain active across app restarts until explicit logout.
 */
object SessionManager {
  private const val PREFS_NAME = "campus_canteen_session_prefs"

  // Student Session Keys
  private const val KEY_IS_STUDENT_LOGGED_IN = "is_student_logged_in"
  private const val KEY_STUDENT_UID = "student_uid"
  private const val KEY_STUDENT_NAME = "student_name"
  private const val KEY_STUDENT_REG_NUMBER = "student_reg_number"
  private const val KEY_STUDENT_DEPARTMENT = "student_department"
  private const val KEY_STUDENT_EMAIL = "student_email"
  private const val KEY_STUDENT_AVATAR_ID = "student_avatar_id"
  private const val KEY_STUDENT_PHONE = "student_phone"
  private const val KEY_STUDENT_COURSE = "student_course"
  private const val KEY_STUDENT_FAVORITES = "student_favorites"
  private const val KEY_STUDENT_AUTH_TOKEN = "student_auth_token"

  // Canteen Owner Session Keys
  private const val KEY_IS_OWNER_LOGGED_IN = "is_owner_logged_in"
  private const val KEY_OWNER_UID = "owner_uid"
  private const val KEY_OWNER_NAME = "owner_name"
  private const val KEY_OWNER_EMAIL = "owner_email"
  private const val KEY_OWNER_ROLE = "owner_role"
  private const val KEY_OWNER_CANTEEN_ASSIGNED = "owner_canteen_assigned"
  private const val KEY_OWNER_CANTEEN_ID = "owner_canteen_id"
  private const val KEY_OWNER_BLOCK = "owner_block"
  private const val KEY_OWNER_PHONE = "owner_phone"
  private const val KEY_OWNER_FCM_TOKEN = "owner_fcm_token"
  private const val KEY_OWNER_AUTH_TOKEN = "owner_auth_token"

  @Volatile
  private var prefs: SharedPreferences? = null
  @Volatile
  var appContext: Context? = null
    private set

  fun initialize(context: Context) {
    appContext = context.applicationContext
    if (prefs == null) {
      synchronized(this) {
        if (prefs == null) {
          prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
      }
    }
    com.example.data.api.ApiClient.initialize(context)
    com.example.util.NetworkMonitor.initialize(context)
  }

  // ── Student Session Management ─────────────────────────────────────────────

  fun saveStudentSession(profile: UserProfile) {
    prefs?.edit()?.apply {
      putBoolean(KEY_IS_STUDENT_LOGGED_IN, true)
      putString(KEY_STUDENT_UID, profile.registrationNumber.ifBlank { profile.email })
      putString(KEY_STUDENT_NAME, profile.name)
      putString(KEY_STUDENT_REG_NUMBER, profile.registrationNumber)
      putString(KEY_STUDENT_DEPARTMENT, profile.department)
      putString(KEY_STUDENT_EMAIL, profile.email)
      putString(KEY_STUDENT_AVATAR_ID, profile.avatarId)
      putString(KEY_STUDENT_PHONE, profile.phone)
      putString(KEY_STUDENT_COURSE, profile.course)
      // Deactivate owner session if student logs in
      putBoolean(KEY_IS_OWNER_LOGGED_IN, false)
      apply()
    }
  }

  fun saveUserSession(doc: com.example.data.firebase.UserDocument) {
    prefs?.edit()?.apply {
      putBoolean(KEY_IS_STUDENT_LOGGED_IN, true)
      putString(KEY_STUDENT_UID, doc.uid)
      putString(KEY_STUDENT_NAME, doc.name)
      putString(KEY_STUDENT_REG_NUMBER, doc.registrationNumber)
      putString(KEY_STUDENT_DEPARTMENT, doc.department)
      putString(KEY_STUDENT_EMAIL, doc.email)
      putString(KEY_STUDENT_AVATAR_ID, doc.avatarId)
      putString(KEY_STUDENT_PHONE, doc.phone)
      putString(KEY_STUDENT_COURSE, doc.course)
      // Deactivate owner session if student logs in
      putBoolean(KEY_IS_OWNER_LOGGED_IN, false)
      apply()
    }
  }

  fun getUserSession(): com.example.data.firebase.UserDocument? {
    val p = prefs ?: return null
    return try {
      if (!p.getBoolean(KEY_IS_STUDENT_LOGGED_IN, false)) return null
      val email = p.getString(KEY_STUDENT_EMAIL, null) ?: return null
      val uid = p.getString(KEY_STUDENT_UID, null) ?: "usr_${email.hashCode()}"
      com.example.data.firebase.UserDocument(
        uid = uid,
        name = p.getString(KEY_STUDENT_NAME, "") ?: "",
        email = email,
        registrationNumber = p.getString(KEY_STUDENT_REG_NUMBER, "") ?: "",
        department = p.getString(KEY_STUDENT_DEPARTMENT, "") ?: "",
        avatarId = p.getString(KEY_STUDENT_AVATAR_ID, "scholar") ?: "scholar",
        phone = p.getString(KEY_STUDENT_PHONE, "") ?: "",
        course = p.getString(KEY_STUDENT_COURSE, "") ?: "",
      )
    } catch (e: Exception) {
      null
    }
  }

  fun clearUserSession() {
    clearStudentSession()
  }

  fun getStudentSession(): UserProfile? {
    val p = prefs ?: return null
    return try {
      if (!p.getBoolean(KEY_IS_STUDENT_LOGGED_IN, false)) return null
      val email = p.getString(KEY_STUDENT_EMAIL, null)
      if (email.isNullOrBlank()) return null
      val name = p.getString(KEY_STUDENT_NAME, "") ?: ""
      val reg = p.getString(KEY_STUDENT_REG_NUMBER, "") ?: ""
      val dept = p.getString(KEY_STUDENT_DEPARTMENT, "") ?: ""
      val avatarId = p.getString(KEY_STUDENT_AVATAR_ID, "scholar") ?: "scholar"
      val phone = p.getString(KEY_STUDENT_PHONE, "") ?: ""
      val course = p.getString(KEY_STUDENT_COURSE, "") ?: ""

      UserProfile(
        name = name,
        registrationNumber = reg,
        department = dept,
        email = email,
        avatarId = avatarId,
        phone = phone,
        course = course,
      )
    } catch (e: Exception) {
      null
    }
  }

  fun isStudentLoggedIn(): Boolean {
    return try {
      prefs?.getBoolean(KEY_IS_STUDENT_LOGGED_IN, false) ?: false
    } catch (e: Exception) {
      false
    }
  }

  fun clearStudentSession() {
    prefs?.edit()?.apply {
      putBoolean(KEY_IS_STUDENT_LOGGED_IN, false)
      remove(KEY_STUDENT_UID)
      remove(KEY_STUDENT_NAME)
      remove(KEY_STUDENT_REG_NUMBER)
      remove(KEY_STUDENT_DEPARTMENT)
      remove(KEY_STUDENT_EMAIL)
      remove(KEY_STUDENT_AVATAR_ID)
      remove(KEY_STUDENT_PHONE)
      remove(KEY_STUDENT_COURSE)
      apply()
    }
  }

  // ── Owner Session Management ───────────────────────────────────────────────

  fun saveOwnerSession(owner: OwnerDocument) {
    prefs?.edit()?.apply {
      putBoolean(KEY_IS_OWNER_LOGGED_IN, true)
      putString(KEY_OWNER_UID, owner.uid)
      putString(KEY_OWNER_NAME, owner.name)
      putString(KEY_OWNER_EMAIL, owner.email)
      putString(KEY_OWNER_ROLE, owner.role)
      putBoolean(KEY_OWNER_CANTEEN_ASSIGNED, owner.canteenAssigned)
      putString(KEY_OWNER_CANTEEN_ID, owner.canteenId)
      putString(KEY_OWNER_BLOCK, owner.block)
      putString(KEY_OWNER_PHONE, owner.phone)
      putString(KEY_OWNER_FCM_TOKEN, owner.fcmToken)
      // Deactivate student session if owner logs in
      putBoolean(KEY_IS_STUDENT_LOGGED_IN, false)
      apply()
    }
  }

  fun getOwnerSession(): OwnerDocument? {
    val p = prefs ?: return null
    return try {
      if (!p.getBoolean(KEY_IS_OWNER_LOGGED_IN, false)) return null
      val uid = p.getString(KEY_OWNER_UID, null)
      if (uid.isNullOrBlank()) return null

      OwnerDocument(
        uid = uid,
        name = p.getString(KEY_OWNER_NAME, "") ?: "",
        email = p.getString(KEY_OWNER_EMAIL, "") ?: "",
        role = p.getString(KEY_OWNER_ROLE, "owner") ?: "owner",
        canteenAssigned = p.getBoolean(KEY_OWNER_CANTEEN_ASSIGNED, false),
        canteenId = p.getString(KEY_OWNER_CANTEEN_ID, "") ?: "",
        block = p.getString(KEY_OWNER_BLOCK, "") ?: "",
        phone = p.getString(KEY_OWNER_PHONE, "") ?: "",
        fcmToken = p.getString(KEY_OWNER_FCM_TOKEN, "") ?: "",
      )
    } catch (e: Exception) {
      null
    }
  }

  fun isOwnerLoggedIn(): Boolean {
    return try {
      prefs?.getBoolean(KEY_IS_OWNER_LOGGED_IN, false) ?: false
    } catch (e: Exception) {
      false
    }
  }

  fun clearOwnerSession() {
    prefs?.edit()?.apply {
      putBoolean(KEY_IS_OWNER_LOGGED_IN, false)
      remove(KEY_OWNER_UID)
      remove(KEY_OWNER_NAME)
      remove(KEY_OWNER_EMAIL)
      remove(KEY_OWNER_ROLE)
      remove(KEY_OWNER_CANTEEN_ASSIGNED)
      remove(KEY_OWNER_CANTEEN_ID)
      remove(KEY_OWNER_BLOCK)
      remove(KEY_OWNER_PHONE)
      remove(KEY_OWNER_FCM_TOKEN)
      remove(KEY_OWNER_AUTH_TOKEN)
      apply()
    }
  }

  fun saveOwnerToken(token: String) {
    prefs?.edit()?.putString(KEY_OWNER_AUTH_TOKEN, token.trim())?.apply()
  }

  fun getOwnerToken(): String {
    return prefs?.getString(KEY_OWNER_AUTH_TOKEN, "") ?: ""
  }

  fun saveStudentToken(token: String) {
    prefs?.edit()?.putString(KEY_STUDENT_AUTH_TOKEN, token.trim())?.apply()
  }

  fun getStudentToken(): String {
    return prefs?.getString(KEY_STUDENT_AUTH_TOKEN, "") ?: ""
  }

  fun clearAllSessions() {
    clearStudentSession()
    clearOwnerSession()
  }

  // ── Canteen Settings Persistence (Timings, Profile Picture, Open/Closed) ────

  fun saveCanteenProfile(canteenId: String, imageUrl: String?, timings: String?, isOpen: Boolean?, closeReason: String? = null) {
    prefs?.edit()?.apply {
      if (imageUrl != null) putString("canteen_img_$canteenId", imageUrl)
      if (timings != null) putString("canteen_timings_$canteenId", timings)
      if (isOpen != null) putBoolean("canteen_open_$canteenId", isOpen)
      if (closeReason != null) putString("canteen_reason_$canteenId", closeReason)
      apply()
    }
  }

  fun getDefaultCanteenImage(canteenId: String): String {
    return ""
  }

  fun getCanteenImage(canteenId: String): String? {
    val saved = prefs?.getString("canteen_img_$canteenId", null)?.ifBlank { null }
    if (saved != null) {
      // If saved is an old transient content URI that causes permission revocation, ignore it
      if (saved.startsWith("content://")) {
        return null
      }
      return saved
    }
    return null
  }

  fun getCanteenTimings(canteenId: String): String? {
    return prefs?.getString("canteen_timings_$canteenId", null)?.ifBlank { null }
  }

  fun isCanteenOpen(canteenId: String, defaultOpen: Boolean = true): Boolean {
    return prefs?.getBoolean("canteen_open_$canteenId", defaultOpen) ?: defaultOpen
  }

  fun getCanteenCloseReason(canteenId: String): String? {
    return prefs?.getString("canteen_reason_$canteenId", null)?.ifBlank { null }
  }

  // ── Password Reset Pending Email Persistence ──────────────────────────────

  private const val KEY_PENDING_RESET_EMAIL = "pending_reset_email"

  fun savePendingResetEmail(email: String) {
    prefs?.edit()?.putString(KEY_PENDING_RESET_EMAIL, email.trim().lowercase())?.apply()
  }

  fun getPendingResetEmail(): String? {
    return prefs?.getString(KEY_PENDING_RESET_EMAIL, null)?.ifBlank { null }
  }

  fun clearPendingResetEmail() {
    prefs?.edit()?.remove(KEY_PENDING_RESET_EMAIL)?.apply()
  }

  // ── Cart Persistence (Preserves cart during network drops or app restarts) ─

  private const val KEY_SAVED_CART_JSON = "saved_cart_items_json"

  fun saveCartJson(json: String) {
    prefs?.edit()?.putString(KEY_SAVED_CART_JSON, json)?.apply()
  }

  fun getCartJson(): String? {
    return prefs?.getString(KEY_SAVED_CART_JSON, null)?.ifBlank { null }
  }

  fun clearCartJson() {
    prefs?.edit()?.remove(KEY_SAVED_CART_JSON)?.apply()
  }

  // ── Notification Persistence ──────────────────────────────────────────────

  private const val KEY_SAVED_NOTIFICATIONS_JSON = "saved_notifications_json"

  private var lastSavedTitle = ""
  private var lastSavedTime = 0L

  fun saveIncomingNotification(title: String, message: String) {
    try {
      val now = System.currentTimeMillis()
      val cleanTitle = title.trim()

      val existing = getSavedNotificationsJson()
      val array = if (existing != null) org.json.JSONArray(existing) else org.json.JSONArray()

      // Extract token number from title if present (e.g., "Order #902: Preparing" or "Order #902 in Kitchen")
      val tokenRegex = Regex("""#([A-Za-z0-9]+)""")
      val extractedToken = tokenRegex.find(cleanTitle)?.groupValues?.get(1)?.lowercase()

      // Identify milestone keyword
      val milestone = when {
        cleanTitle.contains("Preparing", ignoreCase = true) || cleanTitle.contains("Kitchen", ignoreCase = true) -> "PREPARING"
        cleanTitle.contains("Ready", ignoreCase = true) -> "READY"
        cleanTitle.contains("Picked Up", ignoreCase = true) || cleanTitle.contains("Completed", ignoreCase = true) -> "COMPLETED"
        cleanTitle.contains("Cancelled", ignoreCase = true) -> "CANCELLED"
        else -> ""
      }

      // Check for duplicates in existing notifications history
      for (i in 0 until array.length()) {
        val existingObj = array.getJSONObject(i)
        val exTitle = existingObj.optString("title", "").trim()
        val exToken = tokenRegex.find(exTitle)?.groupValues?.get(1)?.lowercase()

        // 1. Exact title duplicate
        if (exTitle.equals(cleanTitle, ignoreCase = true)) {
          return
        }

        // 2. Same order token and milestone duplicate (e.g. "Order #902: Preparing" vs "Order #902 in Kitchen")
        if (!extractedToken.isNullOrBlank() && extractedToken == exToken && milestone.isNotBlank()) {
          val exMilestone = when {
            exTitle.contains("Preparing", ignoreCase = true) || exTitle.contains("Kitchen", ignoreCase = true) -> "PREPARING"
            exTitle.contains("Ready", ignoreCase = true) -> "READY"
            exTitle.contains("Picked Up", ignoreCase = true) || exTitle.contains("Completed", ignoreCase = true) -> "COMPLETED"
            exTitle.contains("Cancelled", ignoreCase = true) -> "CANCELLED"
            else -> ""
          }
          if (milestone == exMilestone) {
            // Already has this milestone recorded for this order, ignore duplicate!
            return
          }
        }
      }

      val obj = org.json.JSONObject().apply {
        put("id", java.util.UUID.randomUUID().toString())
        put("title", cleanTitle)
        put("message", message)
        put("timestamp", now)
        put("isUnread", true)
      }
      val newArray = org.json.JSONArray()
      newArray.put(obj)
      for (i in 0 until array.length().coerceAtMost(29)) {
        newArray.put(array.get(i))
      }
      prefs?.edit()?.putString(KEY_SAVED_NOTIFICATIONS_JSON, newArray.toString())?.apply()
    } catch (e: Exception) {
      android.util.Log.e("SessionManager", "Failed to save notification: ${e.message}")
    }
  }

  fun getSavedNotificationsJson(): String? {
    return prefs?.getString(KEY_SAVED_NOTIFICATIONS_JSON, null)?.ifBlank { null }
  }

  fun saveAllNotificationsJson(json: String) {
    prefs?.edit()?.putString(KEY_SAVED_NOTIFICATIONS_JSON, json)?.apply()
  }

  fun clearNotifications() {
    prefs?.edit()?.remove(KEY_SAVED_NOTIFICATIONS_JSON)?.apply()
  }

  // ── Active Order Background Tracking Persistence ────────────────────────────
  private const val KEY_ACTIVE_ORDER_ID = "active_tracking_order_id"
  private const val KEY_ACTIVE_ORDER_TOKEN = "active_tracking_order_token"
  private const val KEY_ACTIVE_ORDER_CANTEEN_ID = "active_tracking_order_canteen_id"
  private const val KEY_ACTIVE_ORDER_CANTEEN_NAME = "active_tracking_order_canteen_name"
  private const val KEY_ACTIVE_ORDER_BLOCK = "active_tracking_order_block"
  private const val KEY_ACTIVE_ORDER_LAST_STATUS = "active_tracking_last_status"
  private const val KEY_ACTIVE_ORDER_COUNTER = "active_tracking_pickup_counter"

  fun saveActiveOrderTracking(
    orderId: String,
    tokenNumber: String,
    canteenId: String = "",
    canteenName: String = "",
    block: String = "",
    lastStatus: String = "NEW",
    counter: String = "Counter 1"
  ) {
    prefs?.edit()?.apply {
      putString(KEY_ACTIVE_ORDER_ID, orderId)
      putString(KEY_ACTIVE_ORDER_TOKEN, tokenNumber)
      putString(KEY_ACTIVE_ORDER_CANTEEN_ID, canteenId)
      putString(KEY_ACTIVE_ORDER_CANTEEN_NAME, canteenName)
      putString(KEY_ACTIVE_ORDER_BLOCK, block)
      putString(KEY_ACTIVE_ORDER_LAST_STATUS, lastStatus)
      putString(KEY_ACTIVE_ORDER_COUNTER, counter)
      apply()
    }
  }

  fun updateActiveOrderStatus(status: String, counter: String = "") {
    prefs?.edit()?.apply {
      putString(KEY_ACTIVE_ORDER_LAST_STATUS, status)
      if (counter.isNotBlank()) putString(KEY_ACTIVE_ORDER_COUNTER, counter)
      apply()
    }
  }

  fun getActiveOrderId(): String? = prefs?.getString(KEY_ACTIVE_ORDER_ID, null)?.ifBlank { null }
  fun getActiveOrderToken(): String = prefs?.getString(KEY_ACTIVE_ORDER_TOKEN, "") ?: ""
  fun getActiveOrderCanteenId(): String = prefs?.getString(KEY_ACTIVE_ORDER_CANTEEN_ID, "") ?: ""
  fun getActiveOrderCanteenName(): String = prefs?.getString(KEY_ACTIVE_ORDER_CANTEEN_NAME, "Canteen Kitchen") ?: "Canteen Kitchen"
  fun getActiveOrderBlock(): String = prefs?.getString(KEY_ACTIVE_ORDER_BLOCK, "Campus") ?: "Campus"
  fun getActiveOrderLastStatus(): String = prefs?.getString(KEY_ACTIVE_ORDER_LAST_STATUS, "") ?: ""
  fun getActiveOrderCounter(): String = prefs?.getString(KEY_ACTIVE_ORDER_COUNTER, "Counter 1") ?: "Counter 1"

  fun clearActiveOrderTracking() {
    prefs?.edit()?.apply {
      remove(KEY_ACTIVE_ORDER_ID)
      remove(KEY_ACTIVE_ORDER_TOKEN)
      remove(KEY_ACTIVE_ORDER_CANTEEN_ID)
      remove(KEY_ACTIVE_ORDER_CANTEEN_NAME)
      remove(KEY_ACTIVE_ORDER_BLOCK)
      remove(KEY_ACTIVE_ORDER_LAST_STATUS)
      remove(KEY_ACTIVE_ORDER_COUNTER)
      apply()
    }
  }

  // ── Student Favorites Persistence ──────────────────────────────────────────

  fun saveFavoriteItemIds(ids: Set<String>) {
    prefs?.edit()?.apply {
      putStringSet(KEY_STUDENT_FAVORITES, ids)
      apply()
    }
  }

  fun getFavoriteItemIds(): Set<String> {
    return prefs?.getStringSet(KEY_STUDENT_FAVORITES, emptySet()) ?: emptySet()
  }
}


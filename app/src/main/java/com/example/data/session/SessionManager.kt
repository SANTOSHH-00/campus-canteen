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
  }

  // ── Student Session Management ─────────────────────────────────────────────

  fun saveStudentSession(profile: UserProfile) {
    prefs?.edit()?.apply {
      putBoolean(KEY_IS_STUDENT_LOGGED_IN, true)
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
      apply()
    }
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
}

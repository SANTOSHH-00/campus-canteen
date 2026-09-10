# QuickBite Production ProGuard & R8 Rules
# ─────────────────────────────────────────────────────────────

# Preserve source file names and line numbers for Google Play crash reporting
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep All QuickBite Data & API Models (Moshi / Serialization)
-keep class com.example.data.** { *; }
-keep class com.example.ui.state.** { *; }

# Moshi rules
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonQualifier <fields>;
}
-keep class * implements com.squareup.moshi.JsonAdapter {
    public <init>(...);
}

# Kotlin Serialization
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit 2 & OkHttp 3
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Android Architecture Components (Room, Lifecycle, ViewModel)
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Google Play Services & Firebase
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Compose runtime rules
-keep class androidx.compose.runtime.** { *; }

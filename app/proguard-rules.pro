# ProGuard rules for Emergency Response Patient App
# Reference: https://developer.android.com/build/shrink-code

# ─── Keep application entry points ────────────────────────────────────────────
-keep public class com.emergency.patient.activities.** { *; }
-keep public class com.emergency.patient.services.** { *; }

# ─── Security: NEVER log JWT or sensitive token values in release ────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# Keep w() and e() so runtime errors are still visible in crash reports

# ─── Retrofit + OkHttp ────────────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keepattributes Signature, Exceptions
-keepattributes *Annotation*
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── Socket.IO ────────────────────────────────────────────────────────────────
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# ─── Firebase / FCM ───────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ─── ZXing ────────────────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# ─── AndroidX Security (EncryptedSharedPreferences) ──────────────────────────
-keep class androidx.security.crypto.** { *; }

# ─── Gson (used by Retrofit converter) ───────────────────────────────────────
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ─── Google Play Services ─────────────────────────────────────────────────────
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt

# Keep all classes used by reflection
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Keep Hilt components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.climtech.adlcollector.core.data.db.** { *; }

# Keep Moshi adapters and JSON classes
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class com.climtech.adlcollector.**.net.** { *; }
-keep class com.climtech.adlcollector.core.model.** { *; }

# Keep Retrofit interfaces
-keep interface com.climtech.adlcollector.**.net.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep OkHttp and Retrofit
-keep class okhttp3.** { *; }
-keep class retrofit2.** { *; }
-dontwarn okio.**
-dontwarn retrofit2.**

# Keep OAuth AppAuth classes
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# Keep WorkManager classes
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# Keep Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Keep coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove debug logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Optimization settings
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Don't warn about missing classes that are not used
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn kotlin.coroutines.jvm.internal.**

# Keep data classes used in network responses
-keep class com.climtech.adlcollector.feature.**.data.net.** { *; }

# Keep observation entities and related classes
-keep class com.climtech.adlcollector.feature.observations.** { *; }
-keep class com.climtech.adlcollector.feature.stations.** { *; }

# Keep notification helper
-keep class com.climtech.adlcollector.core.util.NotificationHelper { *; }
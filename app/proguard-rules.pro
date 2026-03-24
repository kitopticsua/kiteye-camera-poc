# Sentry
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# Glide excluded from AUSBC — suppress R8 missing class warnings
-dontwarn com.bumptech.glide.**
-dontwarn com.zlc.glide.**

# USB/UVC classes
-keep class com.kitoptics.thermalview.usb.** { *; }
-keep class com.jiangdg.** { *; }
-keep class com.serenegiant.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

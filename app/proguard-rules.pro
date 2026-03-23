# Sentry
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# USB/UVC classes
-keep class com.kitoptics.thermalview.usb.** { *; }
-keep class com.jiangdg.** { *; }
-keep class com.serenegiant.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

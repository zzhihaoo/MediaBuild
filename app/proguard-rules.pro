# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Keep data classes
-keep class com.example.mediabuild.model.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Application
-keep class com.example.mediabuild.MediaDownloadApp { *; }
-keep class com.example.mediabuild.MainActivity { *; }

# Accessibility Service
-keep class com.example.mediabuild.accessibility.** { *; }

# Services
-keep class com.example.mediabuild.service.** { *; }

# Receiver
-keep class com.example.mediabuild.receiver.** { *; }

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class androidx.activity.compose.** { *; }
-keep class androidx.lifecycle.compose.** { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

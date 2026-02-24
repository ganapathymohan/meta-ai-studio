# Add project specific ProGuard rules here.

# Keep WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep WebView
-keepclassmembers class android.webkit.WebView {
   public *;
}

# Keep Download Manager
-keep class android.app.DownloadManager { *; }
-keep class android.app.DownloadManager$Request { *; }

# Keep our app classes
-keep class com.metaai.studio.** { *; }

# AndroidX WebKit
-keep class androidx.webkit.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Prevent stripping of important Android classes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# Suppress warnings for known safe items
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**

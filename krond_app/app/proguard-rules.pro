-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# junixsocket
-keep class org.newsclub.net.unix.** { *; }
-keep class com.kohlschutter.junixsocket.** { *; }
-dontwarn com.kohlschutter.**
-dontwarn org.eclipse.jdt.**
-dontwarn java.rmi.**
-dontwarn com.google.errorprone.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep JSON
-keepattributes Signature
-keepattributes *Annotation*

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Voyager-specific optimizations
# Remove debug and verbose logging calls in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Remove ProductionLogger debug calls in release builds
-assumenosideeffects class com.cosmiclaboratory.voyager.utils.ProductionLogger {
    public *** d(...);
    public *** v(...);
    public *** dataFlow(...);
}

# Keep error and warning logs for crash reporting
-keep class com.cosmiclaboratory.voyager.utils.ProductionLogger {
    public *** e(...);
    public *** w(...);
    public *** i(...);
    public *** perf(...);
    public *** analytics(...);
    public *** recovery(...);
}

# CRITICAL FIX: Enhanced Hilt Worker protection
-keep @androidx.hilt.work.HiltWorker class * {
    <init>(...);
    *;
}

# Keep specific PlaceDetectionWorker constructors
-keep class com.cosmiclaboratory.voyager.data.worker.PlaceDetectionWorker {
    public <init>(...);
    public <init>(android.content.Context, androidx.work.WorkerParameters, ...);
}

# Keep FallbackPlaceDetectionWorker 
-keep class com.cosmiclaboratory.voyager.data.worker.FallbackPlaceDetectionWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
    *;
}

# Keep all WorkManager workers and their required constructors
-keep class com.cosmiclaboratory.voyager.data.worker.** {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
    public <init>(android.content.Context, androidx.work.WorkerParameters, ...);
    *;
}

# Keep Dagger/Hilt assisted injection annotations and methods
-keep @dagger.assisted.AssistedInject class * {
    <init>(...);
    *;
}

-keep @dagger.assisted.Assisted class * {
    *;
}

-keep class dagger.assisted.** {
    *;
}

# Keep Hilt Worker factory and related classes
-keep class androidx.hilt.work.HiltWorkerFactory {
    *;
}

-keep class androidx.hilt.work.** {
    *;
}

# Preserve WorkManager reflection usage comprehensively
-keep class androidx.work.** {
    *;
}

-keepclassmembers class androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclassmembers class androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Keep reflection methods that might be called from workers
-keepclassmembers class * {
    java.lang.reflect.Method invoke(...);
    java.lang.reflect.Constructor newInstance(...);
}

# CRITICAL FIX: Enhanced Android framework reflection protection
-keep class android.** {
    *;
}

-keepclassmembers class android.** {
    public *;
    protected *;
    !private *;
}

# Keep methods that are commonly accessed via reflection
-keepclassmembers class * {
    *** get*(...);
    *** set*(...);
    *** is*(...);
    *** has*(...);
    *** toString(...);
    *** hashCode(...);
    *** equals(...);
}

# Protect against reflection NPEs in system services
-keep class android.content.Context {
    public android.content.Context getApplicationContext();
    public java.lang.Object getSystemService(java.lang.String);
    public android.content.pm.PackageManager getPackageManager();
    public android.content.res.Resources getResources();
}

# Protect system service getters
-keep class * extends android.content.Context {
    public java.lang.Object getSystemService(java.lang.String);
}

# Keep Android system service interfaces and implementations
-keep interface android.** {
    *;
}

-keep class * implements android.** {
    public *;
    protected *;
}

# CRITICAL: Keep reflection-related classes and methods
-keep class java.lang.reflect.** {
    *;
}

-keepclassmembers class java.lang.reflect.** {
    *;
}

# Prevent obfuscation of method and field names that might be accessed via reflection
-keepnames class * {
    *;
}

# Keep annotation classes that might be used for reflection
-keep @interface * {
    *;
}

-keep class * {
    @*.* *;
}

# Additional protection for commonly reflected Android framework methods
-keepclassmembers class android.view.View {
    *** findViewById(...);
    *** setVisibility(...);
    *** getVisibility(...);
}

-keepclassmembers class android.content.Intent {
    *** putExtra(...);
    *** getExtra(...);
    *** getStringExtra(...);
    *** getIntExtra(...);
    *** getBooleanExtra(...);
}

# Protect lifecycle methods that might be accessed via reflection
-keepclassmembers class * extends android.app.Activity {
    protected void onCreate(...);
    protected void onResume(...);
    protected void onPause(...);
    protected void onDestroy(...);
}

-keepclassmembers class * extends android.app.Service {
    public int onStartCommand(...);
    public void onCreate(...);
    public void onDestroy(...);
}
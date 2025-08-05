# Voyager ProGuard / R8 rules
#
# Philosophy: trust R8. The Android Gradle plugin's default rules
# (proguard-android-optimize.txt) already cover Activity/Service/Fragment lifecycle,
# View constructors, parcelables, and standard reflection. We only add rules where
# our code or libraries genuinely need them.
#
# Avoid blanket -keep rules — they defeat optimization and obfuscation.

# ── Crash report readability ──────────────────────────────────────────
# Keep source file + line number info so stack traces in HealthLog are usable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Logging strip (release builds only need errors/warnings/info) ────
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-assumenosideeffects class com.cosmiclaboratory.voyager.utils.ProductionLogger {
    public *** d(...);
    public *** v(...);
    public *** dataFlow(...);
}

# ── Hilt Workers ──────────────────────────────────────────────────────
# WorkManager + HiltWorkerFactory instantiate workers reflectively by class name.
-keep @androidx.hilt.work.HiltWorker class * {
    <init>(...);
}

-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Room ──────────────────────────────────────────────────────────────
# Room generates `_Impl` classes by reflection from @Database/@Dao classes.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.paging.**

# ── SQLCipher ─────────────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ── Kotlinx Serialization ─────────────────────────────────────────────
# Companion KSerializer and serializer() lookups are reflective.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static **$* *;
}

-keepclasseswithmembers class **$$serializer {
    static ** INSTANCE;
}

# Keep generated serializers
-keep,includedescriptorclasses class com.cosmiclaboratory.voyager.**$$serializer { *; }
-keepclassmembers class com.cosmiclaboratory.voyager.** {
    *** Companion;
}
-keepclasseswithmembers class com.cosmiclaboratory.voyager.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Retrofit / Gson (geocoding response models) ──────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep geocoding API DTOs deserialized by Gson/Ktor (not all are @Serializable).
-keep class com.cosmiclaboratory.voyager.data.api.** { *; }

# ── MapLibre ──────────────────────────────────────────────────────────
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# ── Hilt assisted injection ──────────────────────────────────────────
-keep,allowobfuscation,allowshrinking @dagger.assisted.AssistedInject class *
-keep,allowobfuscation,allowshrinking @dagger.assisted.AssistedFactory interface *

# ── Suppressed warnings (transitive deps not present at runtime) ─────
# slf4j-api is pulled in transitively but the impl binding isn't included; safe to ignore.
-dontwarn org.slf4j.impl.StaticLoggerBinder

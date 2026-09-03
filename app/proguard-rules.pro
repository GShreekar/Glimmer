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

# ── SEC-04 ───────────────────────────────────────────────────────────────────────────────────
# isMinifyEnabled was false until now, so R8 has literally never run on this code — these are
# defensive keep rules for the paths most likely to break under shrinking/obfuscation. Room's and
# kotlinx-serialization's own AARs ship consumer-rules.txt that cover the bulk of this already;
# these are the app-specific pieces those consumer rules can't know about.

# Room: keep entities and DAOs, which are referenced by Room's generated code via reflection.
-keep class com.glimmer.app.data.Birthday { *; }
-keep interface com.glimmer.app.data.BirthdayDao { *; }
-keep class com.glimmer.app.data.AppDatabase_Impl { *; }

# kotlinx.serialization's official recommended R8 rules (used here for Birthday and the
# @Serializable navigation route objects in ui/GlimmerApp.kt) — without these, R8 can strip the
# generated $$serializer companions that kotlinx.serialization looks up reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.glimmer.app.**$$serializer { *; }
-keepclassmembers class com.glimmer.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.glimmer.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# SQLCipher's JNI bridge calls back into these classes by name.
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

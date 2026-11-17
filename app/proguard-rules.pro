# ProGuard / R8 release rules for SafeTrack

# Keep Firestore & Domain models intact for reflection-based deserialization
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep @Keep annotated classes and members
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Jetpack Credential Manager & Google Identity Services
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Keep source file and line numbers for Crash/StackTrace readability
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

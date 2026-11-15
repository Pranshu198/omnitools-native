# OmniTools Native — R8 config. App classes are referenced from the manifest
# and by reflection-free framework entry points; keep them whole. Library
# behaviour comes from the AARs' own consumer proguard.txt files (passed
# separately by build-native.sh) plus webkit-proguard.txt.
-keep class com.jackbhai.omnitools.nativ.** { *; }
-keep class androidx.media3.session.MediaButtonReceiver { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
# Media3 1.5.1 references this API-35 framework class (guarded at runtime);
# our --lib is android-34, where it does not exist yet.
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener

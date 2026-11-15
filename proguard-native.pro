# OmniTools Native — R8 config: minification + obfuscation ON.
#
# Only what must keep its name is kept:
#  - the four components the manifest names (aapt2 has no AGP here to
#    generate these keeps automatically),
#  - Sec, whose native methods bind to libomnisec by JNI name,
#  - Amplituda's API, whose methods bind to its .so by JNI name,
#  - MediaButtonReceiver (named by media3-session's own manifest merger
#    in a Gradle world; here it is in our manifest).
# Everything else — all app logic, the vendored BlurView/visualizer
# sources, and every library class not covered by an AAR's own consumer
# rules — is free to shrink and rename.
-keep public class com.jackbhai.omnitools.nativ.MainActivity { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.WatchActivity { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.WebFrameActivity { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.PlaybackService { public <init>(); }
-keep class com.jackbhai.omnitools.nativ.Sec { *; }
-keep class linc.com.amplituda.** { *; }
-keep class androidx.media3.session.MediaButtonReceiver { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-allowaccessmodification
-overloadaggressively
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
# Lottie + waveform reference appcompat classes we do not ship; the code
# paths we use (LottieDrawable on a plain ImageView, the waveform view
# itself) never load them.
-dontwarn androidx.appcompat.**
# Media3 1.5.1 references this API-35 framework class (guarded at runtime);
# our --lib is android-34, where it does not exist yet.
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener
# Batch 2 — native games host + widgets (manifest-named components) and
# the two libraries that resolve classes reflectively / are kept whole
# defensively.
-keep public class com.jackbhai.omnitools.nativ.games.GameActivity { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.WidgetConfigActivity { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.PlayerMiniWidgetProvider { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.PlayerBigWidgetProvider { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.VerseWidgetProvider { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.QuickToolsWidgetProvider { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.SongsSearchWidgetProvider { public <init>(); }
-keep public class com.jackbhai.omnitools.nativ.widget.DownloadsWidgetProvider { public <init>(); }
-keep class org.jaudiotagger.** { *; }
-keep class com.jackbhai.omnitools.nativ.audio.mcu.** { *; }
# jaudiotagger is a desktop-origin library: its image-handling paths
# reference AWT / ImageIO / Swing, which do not exist on Android. The
# app never calls those paths (the tag view reads text + format fields
# only), so the missing references are safe to ignore.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

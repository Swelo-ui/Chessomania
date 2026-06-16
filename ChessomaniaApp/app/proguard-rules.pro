# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keep class com.chessomania.app.chess.** { *; }
-keep class com.chessomania.app.net.** { *; }
-dontwarn com.google.gson.**

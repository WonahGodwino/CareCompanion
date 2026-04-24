-keepattributes *Annotation*
-keepclassmembers class kotlinx.serialization.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ─── SecuGen FDx SDK Pro for Android ────────────────────────────────────────
# Keep all SecuGen public API classes, methods, and their native bindings.
# The SDK ships pre-compiled .so files (libjnisgfplib.so etc.) that are
# referenced via JNI; renaming the Java-side classes would break those bindings.
-keep class SecuGen.FDxSDKPro.** { *; }
-keepclassmembers class SecuGen.FDxSDKPro.** { *; }
-dontwarn SecuGen.FDxSDKPro.**
-dontwarn SecuGen.Driver.**

# Keep native method bindings – required for JNI callbacks from libjnisgfplib.so
-keepclasseswithmembernames class SecuGen.FDxSDKPro.** {
    native <methods>;
}

# Keep SGDeviceInfoParam fields (accessed reflectively by the SDK)
-keepclassmembers class SecuGen.FDxSDKPro.SGDeviceInfoParam {
    public <fields>;
}

# Keep our own scanner wrapper classes so Hilt can inject them
-keep class com.carecompanion.biometric.** { *; }
# ────────────────────────────────────────────────────────────────────────────
# What R8 must not take away.
#
# Minification is on, and the thing it can quietly break here is parsing. The
# panel's answers are decoded by kotlinx.serialization through generated
# serializers that nothing in the source refers to by name, so R8 sees them as
# dead code and removes them. A debug build never notices. A release build
# signs in, asks for the channel list, and fails to read the reply — which
# looks for all the world like the provider's fault.

# -- kotlinx.serialization ------------------------------------------------
#
# The generated $$serializer classes and the Companion objects that reach
# them. Named by the plugin, never by the code, so they have to be kept by
# pattern.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# This app's own models, which are all in core. Everything the panel sends is
# decoded into one of these.
-keep,includedescriptorclasses class com.ipterebi.core.**$$serializer { *; }
-keepclassmembers class com.ipterebi.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.ipterebi.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The hand-written ones. FlexibleIntSerializer and FlexibleStringSerializer
# exist because the same field arrives as a number from one panel and a string
# from the next; they are referenced only from annotations, which is exactly
# the shape R8 cannot see through.
-keep class com.ipterebi.core.**Serializer { *; }

# -- Media3 ---------------------------------------------------------------
#
# The player is built by name in places and its session service is resolved
# through the manifest, neither of which R8 follows.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# -- OkHttp and Okio ------------------------------------------------------
#
# Both ship their own rules; these only silence warnings about optional
# platform classes that are absent on Android by design.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -- Keep the crash readable ----------------------------------------------
#
# Without this a stack trace from a release build names obfuscated classes,
# and the one thing worse than a crash is a crash nobody can place.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

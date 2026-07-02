# Consumer ProGuard rules for update-lib

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.mqd.updatelib.**$$serializer { *; }
-keepclassmembers class com.mqd.updatelib.** {
    *** Companion;
}
-keepclasseswithmembers class com.mqd.updatelib.** {
    kotlinx.serialization.KSerializer serializer(...);
}

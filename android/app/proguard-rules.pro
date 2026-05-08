# Keep kotlinx.serialization metadata
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.baseras.fieldpharma.**$$serializer { *; }
-keepclassmembers class com.baseras.fieldpharma.** {
    *** Companion;
}
-keepclasseswithmembers class com.baseras.fieldpharma.** {
    kotlinx.serialization.KSerializer serializer(...);
}

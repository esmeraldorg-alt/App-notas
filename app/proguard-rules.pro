# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.notasseguras.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.notasseguras.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.notasseguras.app.**$$serializer { *; }

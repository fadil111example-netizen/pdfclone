# PDFBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn org.w3c.dom.**
-dontwarn org.xmlpull.**

# kotlinx.serialization keeps generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose / lifecycle safe mode
-dontwarn androidx.compose.**
-keepclassmembers class * implements androidx.lifecycle.ViewModel { <init>(...); }
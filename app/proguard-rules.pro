# Release builds write nothing to logcat: no document names, paths or page numbers.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# PdfBox-Android reflects on some of its own classes and reads font resources by name.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**

# BouncyCastle is pulled in by PdfBox for encryption. It references optional JDK classes.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# kotlinx.serialization: keep generated serializers for the WorkManager operation spec.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.leaf.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.leaf.app.**$$serializer { *; }

# Room keeps its own generated code; this guards the database class lookup.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Navigation routes are serialised by name.
-keep class com.leaf.app.ui.navigation.** { *; }

# androidx.pdf runs its document service in an isolated process and loads it by class name.
-keep class androidx.pdf.service.** { *; }

# Tesseract4Android calls back into these classes from JNI by name.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

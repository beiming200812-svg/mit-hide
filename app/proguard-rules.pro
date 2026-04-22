-optimizationpasses 6
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep class com.android.system.daemon.** { *; }

-keepclassmembers class java.lang.Process { *; }
-dontwarn java.lang.reflect.**

-keepclasseswithmembernames class * { native <methods>; }
-keepclassmembers class **.R$* { public static <fields>; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

-dontwarn android.**
-dontwarn androidx.**
-dontwarn kotlin.**

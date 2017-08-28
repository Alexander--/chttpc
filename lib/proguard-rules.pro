-dontpreverify
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-verbose

-optimizations !method/inlining/short

-keep public class net.sf.chttpc.** { public protected *; }

-keepclassmembers,allowoptimization enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature
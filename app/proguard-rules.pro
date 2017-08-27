-dontpreverify
-useuniqueclassmembernames
-allowaccessmodification
-dontskipnonpubliclibraryclasses

-optimizationpasses 10

-repackageclasses net.sf.chttpc

-optimizations !code/simplification/cast,!field/*,!class/merging/*

-keep @android.support.annotation.Keep class * {*;}

-keepclassmembers class * implements android.os.Parcelable {
    !private android.os.Parcelable$Creator CREATOR;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <methods>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <fields>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <init>(...);
}

-keepclassmembers class **.R$* {
    public static <fields>;
}
-dontpreverify
-dontobfuscate

-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 10

-keep class net.sf.chttpc.HeaderMap {
    static net.sf.chttpc.HeaderMap create(int);
    void justPut(java.lang.String, java.util.List);
}

-repackageclasses net.sf.chttpc

-keepattributes Signature

-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
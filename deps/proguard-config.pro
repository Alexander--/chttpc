-dontpreverify

-verbose

-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

-optimizationpasses 10

-keep class net.sf.chttpc.HeaderMap {
    static net.sf.chttpc.HeaderMap create(int);
    void append(java.lang.String, java.lang.String[], int, int);
    void append(java.lang.String, java.lang.String);
}

-keep class net.sf.chttpc.MutableUrl {
    void append(...);
    void setLength(int);
    <init>(...);
    int elementsCount;
    char[] buffer;
}

-repackageclasses net.sf.chttpc

-keepattributes Signature

-dontwarn javax.annotation.**
-dontwarn edu.umd.cs.findbugs.annotations.**
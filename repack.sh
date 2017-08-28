#!/bin/bash
rm *.aar
rm -r ./release-package/

pushd upx
make all
popd

pushd strip-nondeterminism
perl Makefile.PL PREFIX=../tools
make
make install
popd

unzip -o lib/build/outputs/aar/lib-release.aar -d release-package
cd release-package
zip --out classes-fixed.jar --fix classes.jar
zip --out annotations-fixed.zip --fix annotations.zip
mv classes-fixed.jar classes.jar
mv annotations-fixed.zip annotations.zip
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 classes.jar
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 annotations.zip

# non-compressed build
zip -Z store -D -R ../release.aar "*"
advzip -z -4 ../release.aar
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 ../release.aar
sha256sum ../release.aar

# UPX-compressed build
chmod +x jni/*/*.so
../upx/src/upx.out --best --ultra-brute --android-shlib jni/x86/*.so
../upx/src/upx.out --best --ultra-brute --android-shlib jni/armeabi-v7a/*.so
zip -Z store -D -R ../release-compressed.aar "*"
advzip -z -4 ../release-compressed.aar
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 ../release-compressed.aar
sha256sum ../release-compressed.aar

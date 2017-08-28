#!/bin/bash -e
sha256sum lib/build/outputs/aar/lib-release.aar

rm *.aar &> /dev/null || true
rm -r ./release-package/ &> /dev/null || true

pushd upx
make all
popd

pushd strip-nondeterminism
mkdir -p ../tools
perl Makefile.PL INSTALL_BASE=$(realpath ../tools)
make
make install
export PERL5LIB=$(realpath ../tools/lib/perl5/)
popd

unzip -o lib/build/outputs/aar/lib-release.aar -d release-package
cd release-package
unzip -o classes.jar -d classes-repacked
rm classes.jar
pushd classes-repacked
zip -X -D -Z store -R ../classes.jar "*"
popd
rm -r classes-repacked
zip --out annotations-fixed.zip --fix annotations.zip
mv annotations-fixed.zip annotations.zip
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 classes.jar
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 annotations.zip

# non-compressed build
zip -X -D -Z store -R ../release.aar "*"
advzip -z -4 ../release.aar
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 ../release.aar
sha256sum ../release.aar

# UPX-compressed build
chmod +x jni/*/*.so
../upx/src/upx.out --best --ultra-brute --android-shlib jni/x86/*.so
../upx/src/upx.out --best --ultra-brute --android-shlib jni/armeabi-v7a/*.so
zip -X -D -Z store -R ../release-compressed.aar "*"
advzip -z -4 ../release-compressed.aar
../tools/bin/strip-nondeterminism -v -t zip -T 1000000000 ../release-compressed.aar
sha256sum ../release-compressed.aar

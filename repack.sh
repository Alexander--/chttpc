#!/bin/bash -ex

export TZ=UTC
export LC_ALL=C

STARTTIME=1000000000
STARTDATE='2001-09-09 01:46:00 i0,0'

rm *.aar &> /dev/null || true
rm -r ./release-package/ &> /dev/null || true

pushd upx
make all
popd

unzip -o lib/build/outputs/aar/lib-release.aar -d release-package
cd release-package

unzip -o classes.jar -d classes-repacked
rm classes.jar
pushd classes-repacked
find . -type f | sort | faketime -f "$STARTDATE" fastjar -@c0M > ../classes.jar
popd
rm -r classes-repacked/

unzip -o ../deps/build/libs/deps-proguard.jar -d libs-repacked
pushd libs-repacked
find . -type f | sort | faketime -f "$STARTDATE" fastjar -@c0M > ../libs/deps.jar
popd
rm -r libs-repacked/

unzip -o annotations.zip -d annotations-repacked
rm annotations.zip
pushd annotations-repacked
find . -type f | sort | faketime -f "$STARTDATE" fastjar -@c0M > ../annotations.zip
popd
rm -r annotations-repacked/

# no UPX
find . -type f | sort | faketime -f "$STARTDATE" fastjar -@c0M > ../release.aar
sha256sum ../release.aar

# no UPX + advancecomp
advzip -z -4 ../release.aar
sha256sum ../release.aar

# with UPX (should be repeatable, since we build UPX above, but who knows...)
chmod +x jni/*/*.so
../upx/src/upx.out --best --ultra-brute --android-shlib jni/x86/*.so
../upx/src/upx.out --best --ultra-brute --android-shlib jni/armeabi-v7a/*.so
find . -type f | sort | faketime -f "$STARTDATE" fastjar -@c0M > ../release-compressed.aar
sha256sum ../release-compressed.aar

# UPX + advancecomp
advzip -z -4 ../release-compressed.aar
sha256sum ../release-compressed.aar

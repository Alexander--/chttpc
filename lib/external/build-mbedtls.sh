#!/bin/bash -e

. ./setenv-simple.sh

pushd mbedtls

export CFLAGS="-pipe -fPIC -Os"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

make clean
make lib && make install DESTDIR="$A2_ROOT"
popd

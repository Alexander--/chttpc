#!/bin/bash -e

. ./setenv-simple.sh

pushd mbedtls

export CFLAGS="-pipe -Os -g0 -fuse-ld=bfd -fpic -flto -fomit-frame-pointer -fvisibility=hidden -ffunction-sections -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -frandom-seed=frkj23tfje4 -Werror=date-time"

echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

export LDFLAGS="$LDFLAGS $CFLAGS"

make clean
make lib && make install DESTDIR="$A2_ROOT"
popd

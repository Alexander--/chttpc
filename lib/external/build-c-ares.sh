#!/bin/bash -ex
. ./setenv-simple.sh
pushd c-ares
declare -rx VERSION=1.9.1
declare -rx PACKAGE_VERSION=1.9.1

export CFLAGS="-pipe -fPIC -Os"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

autoreconf -fvi
./configure --host="$A2_COMPILER" --disable-shared --prefix="$A2_ROOT"
make clean
make && make install
sed -i 's/Version: -/Version: 1.9.1/' "$A2_ROOT/lib/pkgconfig/libcares.pc"
popd

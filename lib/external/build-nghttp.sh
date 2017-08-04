#!/bin/bash -e

. ./setenv-simple.sh

pushd nghttp2

export CFLAGS="-pipe -fPIC -Os"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

autoreconf -fvi

./configure \
    --prefix="$A2_ROOT" \
    --host=$A2_COMPILER \
    --enable-static --disable-shared \
    --enable-lib-only --with-gnu-ld --with-boost=no \
    --with-xml-prefix="$A2_ROOT" \
    --without-libxml2 --without-jemalloc \
    --disable-threads \
    CXXFLAGS="-Os -g" \
    CFLAGS="-Os -g $CFLAGS" \
    LDFLAGS="-L$A2_TOOLCHAIN/lib $LDFLAGS" \
    PKG_CONFIG_LIBDIR="$A2_ROOT/lib/pkgconfig"

make clean
make && make install
popd

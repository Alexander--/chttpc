#!/bin/bash -e

. ./setenv-simple.sh

pushd nghttp2

export CFLAGS="-pipe -fPIC -Os -g0 -flto -fvisibility=hidden -ffunction-sections -fdata-sections"

echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

export LDFLAGS="$LDFLAGS $CFLAGS"

autoreconf -fvi

./configure \
    --prefix="$A2_ROOT" \
    --host=$A2_COMPILER \
    --enable-static --disable-shared \
    --enable-lib-only --with-gnu-ld --with-boost=no \
    --with-xml-prefix="$A2_ROOT" \
    --without-libxml2 --without-jemalloc \
    --disable-threads \
    CXXFLAGS="-Os -g0 $CFLAGS" \
    CFLAGS="-Os -g0 $CFLAGS" \
    LDFLAGS="-L$A2_TOOLCHAIN/lib $LDFLAGS" \
    PKG_CONFIG_LIBDIR="$A2_ROOT/lib/pkgconfig" \
    ac_cv_c_bigendian=no

make clean
make && make install
popd

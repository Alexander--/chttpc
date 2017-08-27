#!/bin/bash -e

export A2_COMPILER=$1
export A2_ABI=$2
export A2_GCC=$3
export A2_ARCH=$4

export A2_TOOLCHAIN=$(realpath build/toolchain/$A2_ABI)

mkdir -p build/native-libs/$A2_ABI libs/$A2_ABI src/main/jniLibs/$A2_ABI

export A2_ROOT=$(realpath build/native-libs/$A2_ABI)

export A2_DEST=$(realpath src/main/jniLibs/$A2_ABI)

#(./build-openssl.sh)

(./build-mbedtls.sh)

#(./build-c-ares.sh)

#(./build-nghttp.sh)

export PATH="$A2_TOOLCHAIN/bin:$PATH"

. ./setenv-simple.sh

cd curl

#-flto

export CFLAGS="$CFLAGS -pipe -fuse-ld=bfd -fpic -flto -fomit-frame-pointer -fvisibility=hidden -fno-function-sections -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -Wl,--warn-shared-textrel -Wl,--fatal-warnings"

echo "$A2_ABI" | grep  -q  "mips" && CFLAGS="$CFLAGS -mno-split-addresses -mno-explicit-relocs"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

export LDFLAGS="$LDFLAGS $CFLAGS"

autoreconf -fvi

./configure \
    --host=$A2_COMPILER \
    --enable-static --disable-shared \
    --disable-scp --disable-dict --disable-ftp --disable-ftps --disable-sftp --disable-tftp \
    --disable-ldap --disable-ldaps --disable-rtsp --disable-rtmp --disable-pop3 --disable-imap \
    --disable-telnet --disable-smb --disable-smtp --disable-gopher --disable-file --disable-manual \
    --disable-unix-sockets --disable-sspi --disable-crypto-auth --disable-ntlm-wb --disable-tls-srp \
    --without-gnutls --without-polarssl --without-axtls --without-cyassl --without-nss --without-libmetalink \
    --without-ca-bundle --without-ca-path --without-ca-fallback  --without-libidn2 --disable-cookies \
    --without-librtmp --without-zsh-functions-dir  --without-libssh2 --disable-libcurl-option \
    --without-ssl \
    --without-nghttp2 \
    --with-mbedtls="$A2_ROOT" \
    --enable-threaded-resolver \
    --with-libz --with-libz-prefix="$A2_TOOLCHAIN" \
    ac_cv_func_getpwuid=no \
    ac_cv_func_getpwuid_r=no \
    CXXFLAGS="-Os -g0 $CFLAGS" \
    CFLAGS="-Os -g0 $CFLAGS" \
    CPPFLAGS="-DHAVE_GETTIMEOFDAY=1 -DHAVE_GETADDRINFO=1" \
    LDFLAGS="-L$A2_TOOLCHAIN/lib $LDFLAGS" \
    PKG_CONFIG_LIBDIR="$A2_ROOT/lib/pkgconfig" \
    ZLIB_LIBS="-lz" \
    ZLIB_CFLAGS="-I$A2_TOOLCHAIN/sysroot/usr/include"

#--without-mbedtls \
#     --enable-ares="$A2_ROOT" \
#     --with-nghttp2="$A2_ROOT" \
#

make clean && make && make install prefix="$A2_ROOT"

#if [ $USE_PIE -a $A2_ABI = "x86" ]; then
#  # stripping everything has unwanted side-effect of adding text relocations to assembly routines
#  "${A2_TOOLCHAIN}/bin/${A2_COMPILER}-strip" -g -o src/aria2c-stripped src/aria2c
#fi

#install -D src/aria2c-stripped "$A2_DEST/$A2_BIN"

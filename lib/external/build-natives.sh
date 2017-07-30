#!/bin/bash -e

export A2_COMPILER=$1
export A2_ABI=$2
export A2_GCC=$3
export A2_ARCH=$4

export A2_TOOLCHAIN=$(realpath build/toolchain/$A2_ABI)

export A2_OPENSSL=$(realpath jni/openssl)
#export A2_CARES=$(realpath jni/c-ares)

mkdir -p build/native-libs/$A2_ABI libs/$A2_ABI src/main/jniLibs/$A2_ABI

export A2_ROOT=$(realpath build/native-libs/$A2_ABI)

export A2_DEST=$(realpath src/main/jniLibs/$A2_ABI)

(./build-openssl.sh)

#(./build-c-ares.sh)

export PATH="$A2_TOOLCHAIN/bin:$PATH"

cd curl

export CFLAGS="$CFLAGS -pipe -fPIC -Wl,--warn-shared-textrel -Wl,--fatal-warnings"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && CFLAGS="$CFLAGS -march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16"
echo "$A2_ABI" | grep  -q  "armeabi-v7a" && LDFLAGS="$LDFLAGS -march=armv7-a -Wl,--fix-cortex-a8"

autoreconf -fvi

./configure \
    --host=$A2_COMPILER \
    --enable-static --disable-shared \
    --disable-scp --disable-dict --disable-ftp --disable-ftps --disable-sftp --disable-tftp \
    --disable-ldap --disable-ldaps --disable-rtsp --disable-rtmp --disable-pop3 --disable-imap \
    --disable-telnet --disable-smb --disable-smtp --disable-gopher --disable-file --disable-manual \
    --disable-unix-sockets --disable-sspi --disable-crypto-auth --disable-ntlm-wb --disable-tls-srp \
    --without-gnutls --without-polarssl --without-mbedtls --without-cyassl --without-nss --without-libmetalink \
    --without-ca-bundle --without-ca-path --without-ca-fallback  --without-libidn2 --without-nghttp2 \
    --without-librtmp --without-zsh-functions-dir  --without-libssh2 --disable-ares --disable-cookies --disable-libcurl-option \
    --with-ssl="$A2_ROOT" --enable-threaded-resolver \
    --with-libz --with-libz-prefix="$A2_TOOLCHAIN" \
    ac_cv_func_getpwuid=no \
    ac_cv_func_getpwuid_r=no \
    CXXFLAGS="-Os -g" \
    CFLAGS="-Os -g $CFLAGS" \
    CPPFLAGS="-DHAVE_GETTIMEOFDAY=1 -DHAVE_GETADDRINFO=1" \
    LDFLAGS="-L$A2_TOOLCHAIN/lib $LDFLAGS" \
    PKG_CONFIG_LIBDIR="$A2_ROOT/lib/pkgconfig" \
    ZLIB_LIBS="-lz" \
    ZLIB_CFLAGS="-I$A2_TOOLCHAIN/sysroot/usr/include"

cd lib/

make clean && make && make install prefix="$A2_ROOT"

#if [ $USE_PIE -a $A2_ABI = "x86" ]; then
#  # stripping everything has unwanted side-effect of adding text relocations to assembly routines
#  "${A2_TOOLCHAIN}/bin/${A2_COMPILER}-strip" -g -o src/aria2c-stripped src/aria2c
#fi

#install -D src/aria2c-stripped "$A2_DEST/$A2_BIN"

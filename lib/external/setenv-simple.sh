#!/bin/bash -e
. ./setenv-generic.sh
export CC="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc --sysroot=$SYSROOT -isystem $ANDROID_NDK/sysroot/usr/include/ -isystem $ANDROID_NDK/sysroot/usr/include/$A2_COMPILER"
export CPP="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}cpp --sysroot=$SYSROOT -isystem $ANDROID_NDK/sysroot/usr/include/ -isystem $ANDROID_NDK/sysroot/usr/include/$A2_COMPILER"
export CXX="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}g++ --sysroot=$SYSROOT -isystem $ANDROID_NDK/sysroot/usr/include/ -isystem $ANDROID_NDK/sysroot/usr/include/$A2_COMPILER"
export AR="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc-ar"
export RANLIB="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc-ranlib"
export NM="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc-nm"
#!/bin/bash -e
. ./setenv-generic.sh
export CC="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc --sysroot=$SYSROOT"
export CPP="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}cpp --sysroot=$SYSROOT"
export CXX="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}g++ --sysroot=$SYSROOT"
export AR="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc-ar"
export RANLIB="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc-ranlib"
export NM="$ANDROID_TOOLCHAIN/${CROSS_COMPILE}gcc-nm"
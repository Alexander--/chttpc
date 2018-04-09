cmake_minimum_required(VERSION 3.6)

set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../external/build/native-libs)

add_library(lib_curl STATIC IMPORTED)
add_library(libmbedtls STATIC IMPORTED)
add_library(libmbedx509 STATIC IMPORTED)
add_library(libmbedcrypto STATIC IMPORTED)
#add_library(lib_nghttp2 STATIC IMPORTED)
#add_library(c-ares STATIC IMPORTED)
#add_library(lib_ssl STATIC IMPORTED)
#add_library(lib_crypto STATIC IMPORTED)

set_target_properties(lib_curl PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libcurl.a)
set_target_properties(libmbedtls PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libmbedtls.a)
set_target_properties(libmbedx509 PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libmbedx509.a)
set_target_properties(libmbedcrypto PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libmbedcrypto.a)
#set_target_properties(lib_nghttp2 PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libnghttp2.a)
#set_target_properties(c-ares PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libcares.a)
#set_target_properties(lib_ssl PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libssl.a)
#set_target_properties(lib_crypto PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libcrypto.a)

add_library(chttpc-${CHTTPC_NATIVE_VER} SHARED hashmap.c native-lib.c)

set_target_properties(chttpc-${CHTTPC_NATIVE_VER} PROPERTIES LINK_DEPENDS ${CMAKE_CURRENT_LIST_DIR}/options.ld)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Os -g0 -Wl,--exclude-libs,ALL")

set(CURL_BASE_CFLAGS -Os -g0 -fuse-ld=bfd -fpic -flto -fomit-frame-pointer -fvisibility=hidden -ffunction-sections -fno-exceptions -fno-unwind-tables -fno-asynchronous-unwind-tables -frandom-seed=frkj23tfje4 -Werror=date-time)

if(${ANDROID_ABI} MATCHES "^mips$")
  set(CURL_FULL_CFLAGS ${CURL_BASE_CFLAGS} -mno-split-addresses -mno-explicit-relocs)
else()
  set(CURL_FULL_CFLAGS ${CURL_BASE_CFLAGS})
endif()

target_compile_options(chttpc-${CHTTPC_NATIVE_VER} PRIVATE ${CURL_FULL_CFLAGS})

target_include_directories(chttpc-${CHTTPC_NATIVE_VER} PRIVATE ${distribution_DIR}/${ANDROID_ABI}/include)

find_library(log-lib log)
find_library(zlib z)
find_library(android-lib android)

target_link_libraries(chttpc-${CHTTPC_NATIVE_VER}
                      lib_curl
                      libmbedtls
                      libmbedx509
                      libmbedcrypto
                      ${zlib}
                      ${log-lib}
                      ${android-lib}
                      -Wl,-T${CMAKE_CURRENT_LIST_DIR}/options.ld -Wl,--relax -Wl,--build-id=none -Wl,--gc-sections -Wl,--exclude-libs,ALL ${CURL_FULL_CFLAGS})
#
#
#lib_nghttp2
#c-ares
cmake_minimum_required(VERSION 3.4.1)

set(distribution_DIR ${CMAKE_SOURCE_DIR}/../../../external/build/native-libs)

add_library(lib_curl STATIC IMPORTED)
add_library(libmbedtls STATIC IMPORTED)
add_library(libmbedx509 STATIC IMPORTED)
add_library(libmbedcrypto STATIC IMPORTED)
add_library(lib_nghttp2 STATIC IMPORTED)
add_library(c-ares STATIC IMPORTED)
#add_library(lib_ssl STATIC IMPORTED)
#add_library(lib_crypto STATIC IMPORTED)

set_target_properties(lib_curl PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libcurl.a)
set_target_properties(libmbedtls PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libmbedtls.a)
set_target_properties(libmbedx509 PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libmbedx509.a)
set_target_properties(libmbedcrypto PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libmbedcrypto.a)
set_target_properties(lib_nghttp2 PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libnghttp2.a)
set_target_properties(c-ares PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libcares.a)
#set_target_properties(lib_ssl PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libssl.a)
#set_target_properties(lib_crypto PROPERTIES IMPORTED_LOCATION ${distribution_DIR}/${ANDROID_ABI}/lib/libcrypto.a)

add_library(curl-wrapper SHARED hashmap.c native-lib.c)

target_compile_options(curl-wrapper PRIVATE -Os -fvisibility=hidden -Wl,--build-id=sha1)

target_include_directories(curl-wrapper PRIVATE ${distribution_DIR}/${ANDROID_ABI}/include)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wl,--exclude-libs,ALL")

find_library(log-lib log)
find_library(zlib z)

target_link_libraries(curl-wrapper
                      lib_curl
                      libmbedtls
                      libmbedx509
                      libmbedcrypto
                      lib_nghttp2
                      c-ares
                      ${zlib}
                      ${log-lib})
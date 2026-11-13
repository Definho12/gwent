cmake_minimum_required(VERSION 3.22.1)
project(gwentwebp C)

# A libwebp é baixada pelo workflow (ou pelo script de teste) para ./libwebp.
set(WEBP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libwebp)

file(GLOB WEBP_SOURCES
  ${WEBP_DIR}/src/dec/*.c
  ${WEBP_DIR}/src/demux/*.c
  ${WEBP_DIR}/src/dsp/*.c
  ${WEBP_DIR}/src/enc/*.c
  ${WEBP_DIR}/src/mux/*.c
  ${WEBP_DIR}/src/utils/*.c
  ${WEBP_DIR}/sharpyuv/*.c)

add_library(gwentwebp SHARED webp_jni.c webp_core.c ${WEBP_SOURCES})
target_include_directories(gwentwebp PRIVATE ${WEBP_DIR} ${WEBP_DIR}/src)
target_compile_options(gwentwebp PRIVATE -O2 -fvisibility=hidden -Wno-unused-parameter)
target_compile_definitions(gwentwebp PRIVATE WEBP_USE_THREAD)
target_link_libraries(gwentwebp m)
target_link_options(gwentwebp PRIVATE "-Wl,-z,max-page-size=16384")

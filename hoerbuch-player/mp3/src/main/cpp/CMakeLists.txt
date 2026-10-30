cmake_minimum_required(VERSION 3.22.1)
project(lauscher_mp3 C)

# LAME 3.100 (LGPL) is fetched at a fixed commit of its git mirror and linked statically into a
# small JNI library; only our JNI symbols are exported.
include(FetchContent)
FetchContent_Declare(
    lame
    GIT_REPOSITORY https://github.com/lameproject/lame.git
    GIT_TAG 1f5cc9487284d5950343aa5d4f70de433468070a
)
FetchContent_GetProperties(lame)
if(NOT lame_POPULATED)
    FetchContent_Populate(lame)
endif()

set(LAME_DIR ${lame_SOURCE_DIR}/libmp3lame)
add_library(mp3lame STATIC
    ${LAME_DIR}/VbrTag.c
    ${LAME_DIR}/bitstream.c
    ${LAME_DIR}/encoder.c
    ${LAME_DIR}/fft.c
    ${LAME_DIR}/gain_analysis.c
    ${LAME_DIR}/id3tag.c
    ${LAME_DIR}/lame.c
    ${LAME_DIR}/newmdct.c
    ${LAME_DIR}/presets.c
    ${LAME_DIR}/psymodel.c
    ${LAME_DIR}/quantize.c
    ${LAME_DIR}/quantize_pvt.c
    ${LAME_DIR}/reservoir.c
    ${LAME_DIR}/set_get.c
    ${LAME_DIR}/tables.c
    ${LAME_DIR}/takehiro.c
    ${LAME_DIR}/util.c
    ${LAME_DIR}/vbrquantize.c
    ${LAME_DIR}/version.c
    ${LAME_DIR}/mpglib_interface.c
)
target_include_directories(mp3lame PUBLIC ${lame_SOURCE_DIR}/include PRIVATE ${LAME_DIR})
# What configure would have detected on Android/Linux; no decoder (mpglib) is needed.
target_compile_definitions(mp3lame PRIVATE STDC_HEADERS HAVE_STDINT_H HAVE_INTTYPES_H HAVE_LIMITS_H
    HAVE_ERRNO_H HAVE_FCNTL_H HAVE_UNISTD_H "ieee754_float32_t=float")
target_compile_options(mp3lame PRIVATE -O2 -w -fPIC)

if(LAUSCHER_MP3_TEST)
    # Host build for a quick encoder test: cmake -DLAUSCHER_MP3_TEST=ON, then ./mp3_test out.mp3
    add_executable(mp3_test mp3_test.c)
    target_link_libraries(mp3_test PRIVATE mp3lame m)
else()
    add_library(lauscher_mp3 SHARED mp3_jni.c)
    set_target_properties(lauscher_mp3 PROPERTIES C_VISIBILITY_PRESET hidden)
    target_link_libraries(lauscher_mp3 PRIVATE mp3lame m)
    target_link_options(lauscher_mp3 PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")
endif()

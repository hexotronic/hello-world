cmake_minimum_required(VERSION 3.22.1)
project(lauscher_whisper C CXX)

# whisper.cpp is fetched at a fixed release and linked statically; only our JNI symbols are
# exported, so its ggml copy cannot clash with the one inside the llm library.
include(FetchContent)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.9.5
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(whisper_cpp)

if(NOT CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(ggml-base PRIVATE -O2)
    target_compile_options(ggml-cpu PRIVATE -O2)
    target_compile_options(whisper PRIVATE -O2)
endif()

add_library(lauscher_whisper SHARED whisper_jni.cpp)
set_target_properties(lauscher_whisper PROPERTIES CXX_STANDARD 17 CXX_VISIBILITY_PRESET hidden)
target_link_libraries(lauscher_whisper PRIVATE whisper)
if(ANDROID)
    target_link_libraries(lauscher_whisper PRIVATE log)
endif()
target_link_options(lauscher_whisper PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")

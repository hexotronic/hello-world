cmake_minimum_required(VERSION 3.22.1)
project(lauscher_llm C CXX)

# llama.cpp is fetched at a fixed release and linked statically; only our JNI symbols are
# exported, so its ggml copy cannot clash with the one inside the whisper library.
include(FetchContent)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)

# The "fast" build (modules llm-fast) uses the dot-product / half-precision instructions of
# ARMv8.2 phones (2018+) or AVX2 on x86. The app loads it only when the CPU supports them.
option(LAUSCHER_FAST "build with ARMv8.2 dotprod+fp16 / AVX2 instructions" OFF)
if(LAUSCHER_FAST)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_AVX ON CACHE BOOL "" FORCE)
        set(GGML_AVX2 ON CACHE BOOL "" FORCE)
        set(GGML_FMA ON CACHE BOOL "" FORCE)
        set(GGML_F16C ON CACHE BOOL "" FORCE)
    endif()
    set(LAUSCHER_LIB lauscher_llm_fast)
else()
    set(LAUSCHER_LIB lauscher_llm)
endif()

FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG v0.8.0
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama_cpp)

# Debug builds of ggml are unusably slow; always optimise.
if(NOT CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(ggml-base PRIVATE -O2)
    target_compile_options(ggml-cpu PRIVATE -O2)
    target_compile_options(llama PRIVATE -O2)
endif()

add_library(${LAUSCHER_LIB} SHARED llm_jni.cpp)
set_target_properties(${LAUSCHER_LIB} PROPERTIES CXX_STANDARD 17 CXX_VISIBILITY_PRESET hidden)
target_link_libraries(${LAUSCHER_LIB} PRIVATE llama)
if(ANDROID)
    target_link_libraries(${LAUSCHER_LIB} PRIVATE log)
endif()
target_link_options(${LAUSCHER_LIB} PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")

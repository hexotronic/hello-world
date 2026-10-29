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

add_library(lauscher_llm SHARED llm_jni.cpp)
set_target_properties(lauscher_llm PROPERTIES CXX_STANDARD 17 CXX_VISIBILITY_PRESET hidden)
target_link_libraries(lauscher_llm PRIVATE llama)
if(ANDROID)
    target_link_libraries(lauscher_llm PRIVATE log)
endif()
target_link_options(lauscher_llm PRIVATE "-Wl,--exclude-libs,ALL" "-Wl,--gc-sections")

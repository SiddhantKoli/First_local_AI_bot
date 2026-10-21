cmake_minimum_required(VERSION 3.22.1)
project(llamajni C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is not vendored in git: scripts/fetch-llama-cpp.sh clones the pinned
# tag into android-app/third_party/llama.cpp before the first build.
set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp sources not found at ${LLAMA_DIR}\n"
        "Run android-app/scripts/fetch-llama-cpp.sh before building.")
endif()

# Build only the library. Tools, tests, examples and the HTTP server would add
# megabytes to the APK and need dependencies Android does not have.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)        # never tune for the build host
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)        # no OpenMP in the NDK sysroot
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)        # CPU only, no Vulkan SDK here
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)  # would be fetched at configure time

add_subdirectory(${LLAMA_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

add_library(llamajni SHARED llama_jni.cpp)
target_link_libraries(llamajni PRIVATE llama android log)

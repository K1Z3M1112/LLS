cmake_minimum_required(VERSION 3.22.1)
project(lsfg-android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ---------------------------------------------------------------------------
# Optional NCNN AI frame-generation backend
# Enable by passing -DLSFGVK_ENABLE_NCNN=ON and pointing ncnn_DIR at your
# ncnn prebuilt installation:
#   cmake ... -DLSFGVK_ENABLE_NCNN=ON -Dncnn_DIR=/path/to/ncnn/lib/cmake/ncnn
# ---------------------------------------------------------------------------
option(LSFGVK_ENABLE_NCNN "Enable NCNN AI frame-gen backend" OFF)

# ncnn must be built separately per Android ABI (arm64-v8a, x86_64 here).
# Rather than requiring a different `-Dncnn_DIR=...` per ABI (which Gradle's
# externalNativeBuild.cmake.arguments can't express -- it's one flat list
# applied to every ABI invocation), point NCNN_ANDROID_ROOT at a directory
# laid out as:
#   <NCNN_ANDROID_ROOT>/arm64-v8a/lib/cmake/ncnn
#   <NCNN_ANDROID_ROOT>/x86_64/lib/cmake/ncnn
# and this picks the right one using ${ANDROID_ABI}, which CMake sets
# per-invocation automatically. The CI workflow builds and populates this
# layout; see .github/workflows/build-apk.yml.
if(LSFGVK_ENABLE_NCNN AND NOT DEFINED ncnn_DIR AND DEFINED NCNN_ANDROID_ROOT)
    set(ncnn_DIR "${NCNN_ANDROID_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn")
endif()

# Reach into the Linux project tree for sources we want to reuse as-is.
set(LSFG_ROOT "${CMAKE_SOURCE_DIR}/../../../../../framegen-native-lib")
if(NOT EXISTS "${LSFG_ROOT}/framegen/CMakeLists.txt")
    message(FATAL_ERROR
        "Can't find framegen-native-lib sources at ${LSFG_ROOT}. "
        "Did you clone the submodules?")
endif()

# ---------------------------------------------------------------------------
# volk — Vulkan loader
# ---------------------------------------------------------------------------
add_library(volk STATIC "${LSFG_ROOT}/thirdparty/vulkan-loader/volk.c")
target_include_directories(volk PUBLIC "${LSFG_ROOT}/thirdparty/vulkan-loader")
target_compile_definitions(volk PUBLIC VK_USE_PLATFORM_ANDROID_KHR=1)

# ---------------------------------------------------------------------------
# pe-parse — PE/COFF parser (unicode_minimal.cpp substitutes ICU)
# ---------------------------------------------------------------------------
set(PEPARSE_DIR "${LSFG_ROOT}/thirdparty/pe-dll-parser/pe-parser-library")
add_library(pe-parse STATIC
    "${PEPARSE_DIR}/src/buffer.cpp"
    "${PEPARSE_DIR}/src/parse.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/unicode_minimal.cpp"
)
target_include_directories(pe-parse PUBLIC "${PEPARSE_DIR}/include")
target_compile_features(pe-parse PUBLIC cxx_std_17)

# ---------------------------------------------------------------------------
# dxbc — DXVK DXBC→SPIR-V translator
# ---------------------------------------------------------------------------
add_subdirectory(
    "${LSFG_ROOT}/thirdparty/dxbc-to-spirv"
    "${CMAKE_BINARY_DIR}/dxbc-to-spirv"
    EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# framegen — Vulkan frame-generation pipeline (submodule)
# ---------------------------------------------------------------------------
file(GLOB_RECURSE FRAMEGEN_WATCH_SOURCES CONFIGURE_DEPENDS
    "${LSFG_ROOT}/framegen/src/*.cpp"
    "${LSFG_ROOT}/framegen/src/*.hpp"
    "${LSFG_ROOT}/framegen/include/*.hpp"
    "${LSFG_ROOT}/framegen/public/*.hpp"
    "${LSFG_ROOT}/framegen/v3.1_src/*.cpp"
    "${LSFG_ROOT}/framegen/v3.1p_src/*.cpp"
)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
    ${FRAMEGEN_WATCH_SOURCES})
add_subdirectory(
    "${LSFG_ROOT}/framegen"
    "${CMAKE_BINARY_DIR}/framegen"
    EXCLUDE_FROM_ALL)

# ---------------------------------------------------------------------------
# lsfg-android — JNI shared library
# ---------------------------------------------------------------------------
find_library(log-lib           log)
find_library(neuralnetworks-lib neuralnetworks)

set(LSFG_ANDROID_SOURCES
    lsfg_jni.cpp
    android_shader_loader.cpp
    android_vk_probe.cpp
    android_vk_session.cpp
    ahb_image_bridge.cpp
    gpu_postprocess.cpp
    lsfg_render_loop.cpp
    nnapi_npu.cpp
    nnapi_postprocess.cpp
    cpu_postprocess.cpp
    crash_reporter.cpp
)

if(LSFGVK_ENABLE_NCNN)
    list(APPEND LSFG_ANDROID_SOURCES ncnn_backend.cpp)
endif()

add_library(lsfg-android SHARED ${LSFG_ANDROID_SOURCES})

target_link_libraries(lsfg-android
    PRIVATE
        lsfg-vk-framegen
        dxbc
        pe-parse
        volk
        ${log-lib}
        ${neuralnetworks-lib}
        android
)

if(LSFGVK_ENABLE_NCNN)
    # Expects ncnn prebuilt for Android (per-ABI). Either pass
    # -Dncnn_DIR=<install>/lib/cmake/ncnn directly, or pass
    # -DNCNN_ANDROID_ROOT=<root> with the per-ABI layout described above
    # (this is what CI does). Build ncnn from source with:
    #   cmake -DNCNN_VULKAN=ON -DANDROID_ABI=<abi> -DANDROID_NDK=... \
    #         -DCMAKE_TOOLCHAIN_FILE=<ndk>/build/cmake/android.toolchain.cmake ..
    find_package(ncnn REQUIRED)
    target_link_libraries(lsfg-android PRIVATE ncnn)
    target_compile_definitions(lsfg-android PRIVATE LSFGVK_ENABLE_NCNN=1)
    message(STATUS "NCNN AI backend: ENABLED")
else()
    message(STATUS "NCNN AI backend: DISABLED (pass -DLSFGVK_ENABLE_NCNN=ON to enable)")
endif()

cmake_minimum_required(VERSION 3.22.1)
project(lsfg-android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Reach into the Linux project tree for sources we want to reuse as-is.
set(LSFG_ROOT "${CMAKE_SOURCE_DIR}/../../../../../framegen-native-lib")
if(NOT EXISTS "${LSFG_ROOT}/framegen/CMakeLists.txt")
    message(FATAL_ERROR "Can't find framegen-native-lib sources at ${LSFG_ROOT}. Did you clone the submodules?")
endif()

# -----------------------------------------------------------------------------
# volk — Vulkan loader. On Android we enable the Android surface platform.
# We avoid add_subdirectory() because volk's default install/include rules get
# in the way; compiling the two-file library directly is trivial.
# -----------------------------------------------------------------------------
add_library(volk STATIC "${LSFG_ROOT}/thirdparty/vulkan-loader/volk.c")
target_include_directories(volk PUBLIC "${LSFG_ROOT}/thirdparty/vulkan-loader")
target_compile_definitions(volk PUBLIC VK_USE_PLATFORM_ANDROID_KHR=1)

# -----------------------------------------------------------------------------
# pe-parse — PE/COFF parser. We skip the upstream CMakeLists (it requires ICU)
# and build the minimal translation units ourselves, substituting our own
# UTF-16 → UTF-8 helper from unicode_minimal.cpp.
# -----------------------------------------------------------------------------
set(PEPARSE_DIR "${LSFG_ROOT}/thirdparty/pe-dll-parser/pe-parser-library")
add_library(pe-parse STATIC
    "${PEPARSE_DIR}/src/buffer.cpp"
    "${PEPARSE_DIR}/src/parse.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/unicode_minimal.cpp"
)
target_include_directories(pe-parse PUBLIC "${PEPARSE_DIR}/include")
target_compile_features(pe-parse PUBLIC cxx_std_17)

# -----------------------------------------------------------------------------
# dxbc — DXVK's DXBC → SPIR-V translator (static lib). Upstream CMake is clean
# enough to add_subdirectory without changes.
# -----------------------------------------------------------------------------
add_subdirectory("${LSFG_ROOT}/thirdparty/dxbc-to-spirv" "${CMAKE_BINARY_DIR}/dxbc-to-spirv" EXCLUDE_FROM_ALL)

# -----------------------------------------------------------------------------
# framegen — the Vulkan frame-generation pipeline. Reused verbatim.
#
# Tell Gradle's externalNativeBuild task that this project's "input set"
# extends into the submodule sources. Without this, AGP's incremental build
# only watches files under app/src/main/cpp/ and silently skips re-running
# CMake when files in ../../../../../framegen-native-lib/framegen/src change —
# leaving the cached lsfg-vk-framegen.a in place even after a real source
# edit. CMAKE_CONFIGURE_DEPENDS makes the configure step depend on every
# .cpp/.hpp under the framegen tree, so any edit there triggers a full
# regeneration on the next build.
# -----------------------------------------------------------------------------
file(GLOB_RECURSE FRAMEGEN_WATCH_SOURCES CONFIGURE_DEPENDS
    "${LSFG_ROOT}/framegen/src/*.cpp"
    "${LSFG_ROOT}/framegen/src/*.hpp"
    "${LSFG_ROOT}/framegen/include/*.hpp"
    "${LSFG_ROOT}/framegen/public/*.hpp"
    "${LSFG_ROOT}/framegen/v3.1_src/*.cpp"
    "${LSFG_ROOT}/framegen/v3.1p_src/*.cpp"
)
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS
    ${FRAMEGEN_WATCH_SOURCES}
)
add_subdirectory("${LSFG_ROOT}/framegen" "${CMAKE_BINARY_DIR}/framegen" EXCLUDE_FROM_ALL)

# -----------------------------------------------------------------------------
# ncnn — inference engine for the alternative "ncnn" frame-gen engine
# (frame_interpolator_v2 model, see ncnn_framegen.cpp). Vendored the same way
# as the other thirdparty/ deps above: a git submodule pointing at upstream
# Tencent/ncnn, registered in the root .gitmodules. Building ncnn from source
# (rather than pulling a prebuilt AAR) keeps it inside this repo's normal
# submodule-sync flow and avoids pinning to a specific NDK ABI's prebuilt.
#
# NCNN_BUILD_TOOLS/EXAMPLES/BENCHMARK are off — we only need the library.
# NCNN_VULKAN stays on (default) so ncnn can use its own Vulkan compute
# backend when RenderLoopConfig::ncnnUseVulkanCompute is true; it falls back
# to CPU automatically per-Net if no suitable device is found at runtime.
# -----------------------------------------------------------------------------
set(NCNN_ROOT "${LSFG_ROOT}/thirdparty/ncnn")
if(NOT EXISTS "${NCNN_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "Can't find ncnn sources at ${NCNN_ROOT}. Did you run "
        "'git submodule update --init --recursive'? See README.md.")
endif()
set(NCNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(NCNN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(NCNN_PIXEL_ROTATE OFF CACHE BOOL "" FORCE)
set(NCNN_DISABLE_RTTI OFF CACHE BOOL "" FORCE) # ncnn_framegen.cpp uses dynamic_cast-free code, but keep RTTI for our own std::runtime_error/what()
add_subdirectory("${NCNN_ROOT}" "${CMAKE_BINARY_DIR}/ncnn" EXCLUDE_FROM_ALL)

# -----------------------------------------------------------------------------
# lsfg-android — JNI shared library.
# Phase 2 only exposes nativeVersion() plus placeholder symbols; later phases
# add the real shader extraction and Vulkan context entry points.
# -----------------------------------------------------------------------------
find_library(log-lib log)
find_library(neuralnetworks-lib neuralnetworks)

add_library(lsfg-android SHARED
    lsfg_jni.cpp
    android_shader_loader.cpp
    android_vk_probe.cpp
    android_vk_session.cpp
    ahb_image_bridge.cpp
    lsfg_render_loop.cpp
    ncnn_framegen.cpp
    nnapi_npu.cpp
    crash_reporter.cpp
)

target_link_libraries(lsfg-android
    PRIVATE
        lsfg-vk-framegen
        dxbc
        pe-parse
        volk
        ncnn
        ${log-lib}
        ${neuralnetworks-lib}
        android
)

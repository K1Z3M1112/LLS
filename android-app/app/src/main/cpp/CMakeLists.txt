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
# (frame_interpolator_v2 model, see ncnn_framegen.cpp).
#
# Consumed as the official prebuilt Android+Vulkan package, NOT built from
# source: CI downloads ncnn-<version>-android-vulkan.zip from Tencent/ncnn's
# GitHub releases and exports its extracted path as $NCNN_ANDROID_ROOT before
# Gradle/CMake run (see .github/workflows — "Download ncnn Android+Vulkan
# prebuilt" + "Export NCNN_ANDROID_ROOT"). That package ships a per-ABI
# ncnnConfig.cmake under <ABI>/lib/cmake/ncnn, so find_package() picks up a
# ready-made `ncnn` imported target — no add_subdirectory, no submodule, no
# building ncnn's own dependency graph (protobuf etc.) on every CI run.
#
# Building locally: download the same release zip for your ABI(s), extract
# it, and export NCNN_ANDROID_ROOT to point at the extracted folder (the one
# containing arm64-v8a/, x86_64/, ...) before invoking Gradle, e.g.:
#   export NCNN_ANDROID_ROOT=/path/to/ncnn-android
#   ./gradlew :app:assembleDebug
# -----------------------------------------------------------------------------
if(NOT DEFINED ENV{NCNN_ANDROID_ROOT} OR "$ENV{NCNN_ANDROID_ROOT}" STREQUAL "")
    message(FATAL_ERROR "NCNN_ANDROID_ROOT is not set. Download the ncnn "
        "Android+Vulkan prebuilt (see README.md) and export "
        "NCNN_ANDROID_ROOT=/path/to/ncnn-android before building.")
endif()
set(NCNN_ABI_ROOT "$ENV{NCNN_ANDROID_ROOT}/${ANDROID_ABI}")
if(NOT EXISTS "${NCNN_ABI_ROOT}/lib/cmake/ncnn/ncnnConfig.cmake")
    message(FATAL_ERROR "No ncnn prebuilt for ABI ${ANDROID_ABI} at "
        "${NCNN_ABI_ROOT}. Expected .../${ANDROID_ABI}/lib/cmake/ncnn/ncnnConfig.cmake "
        "— check NCNN_ANDROID_ROOT and that the downloaded package covers this ABI.")
endif()
find_package(ncnn REQUIRED CONFIG PATHS "${NCNN_ABI_ROOT}/lib/cmake/ncnn"
    NO_DEFAULT_PATH NO_CMAKE_FIND_ROOT_PATH)

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

# -----------------------------------------------------------------------------
# The NDK's android.toolchain.cmake bakes "-frtti -fexceptions" into
# CMAKE_CXX_FLAGS by default (has done since NDK r14), but AGP's CMake/Ninja
# integration *also* appends its own "-fno-rtti -fno-exceptions" to every
# target's per-source compile command, and it does so after everything else —
# after CMAKE_CXX_FLAGS, after target_compile_options(), after all of it.
# clang honors whichever of a conflicting pair comes last, so no amount of
# target_compile_options(-fexceptions -frtti) or CMAKE_CXX_FLAGS editing can
# win; this is a long-standing, well-documented NDK/CMake behavior (see
# android/ndk#975, android/ndk#1351), not a mistake in this file.
#
# The one thing CMake's Ninja generator is confirmed to emit *after* the
# toolchain's default flags is a source file's own COMPILE_OPTIONS property
# (other projects hitting this same bug — e.g. godot-cpp#1880 — fixed it the
# same way). Setting it per-file here is what actually wins the flag race.
#
# framegen-native-lib (reused verbatim from the Linux project) and the DXBC
# translator legitimately throw on error (e.g. missing Vulkan extension —
# see lsfg_render_loop.cpp's initFramegen/createFramegenContext, which catch
# to fall back to capture-only mode instead of crashing), so these three
# translation units need exceptions and RTTI actually enabled, not just
# requested.
# -----------------------------------------------------------------------------
set_source_files_properties(
    android_shader_loader.cpp
    lsfg_render_loop.cpp
    ncnn_framegen.cpp
    PROPERTIES COMPILE_OPTIONS "-fexceptions;-frtti"
)

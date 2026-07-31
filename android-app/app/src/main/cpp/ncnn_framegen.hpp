#pragma once

// Alternative frame-generation engine, backed by an ncnn interpolation model
// ("frame_interpolator_v2") instead of the reverse-engineered Lossless.dll
// Vulkan pipeline (LSFG_3_1 / LSFG_3_1P). Selected at runtime via the
// "Frame Gen Engine" setting (LsfgPreferences.FrameGenEngine) and requires
// no user-supplied DLL — the .param/.bin weights ship as app assets.
//
// Mirrors the subset of the LSFG_3_1 namespace that lsfg_render_loop.cpp
// calls, so the render loop can branch on `g.engine` with a like-for-like
// substitution at each call site instead of a parallel code path:
//
//   LSFG_3_1::initialize(...)          -> NcnnFG::initialize(...)
//   LSFG_3_1::createContextFromAHB(...) -> NcnnFG::createContextFromAHB(...)
//   LSFG_3_1::presentContext(...)      -> NcnnFG::presentContext(...)
//   LSFG_3_1::waitIdle()               -> NcnnFG::waitIdle()
//   LSFG_3_1::deleteContext(id)        -> NcnnFG::deleteContext(id)
//   LSFG_3_1::finalize()               -> NcnnFG::finalize()
//
// Architectural difference from LSFG_3_1: this engine does NOT run inside
// the Android app's own Vulkan device/queue. ncnn manages its own Vulkan
// (or CPU) compute context internally. Frames are moved in and out via
// AHardwareBuffer CPU locks (AHardwareBuffer_lock/_unlock) rather than a
// shared VkImage import, which is simpler and more portable across GPU
// vendors at the cost of one CPU-side copy per frame in each direction.
// See ncnn_framegen.cpp for the tiling strategy required by this model's
// fixed 256x256 internal resolution.
//
// Threading: presentContext() runs the interpolation synchronously on the
// calling thread (the render loop's own worker thread) — there is no
// separate GPU queue to hand work off to and wait on later the way the
// LSFG_3_1 semaphore-based path does. inSem/outSem are accepted only for
// call-site compatibility and are ignored (documented in the .cpp).

#include <cstdint>
#include <string>
#include <vector>

struct AHardwareBuffer;

// Minimal stand-ins so this header doesn't need <vulkan/vulkan_core.h> for
// callers that don't already include it; both are binary-compatible with
// the real Vulkan types and lsfg_render_loop.cpp already has the real ones
// in scope when it calls us.
#ifndef VK_DEFINE_NON_DISPATCHABLE_HANDLE
#include <vulkan/vulkan_core.h>
#endif

namespace NcnnFG {

    // Precision variant to load. Mirrors the fp16/fp32 pair the models were
    // exported in (frame_interpolator_v2_fp{16,32}_ncnn.{param,bin}).
    enum class Precision {
        Fp32,
        Fp16,
    };

    ///
    /// Load the ncnn interpolation model and prepare the engine. Cheap to
    /// call relative to LSFG_3_1::initialize (no device/shader compilation
    /// against the app's own VkDevice) — ncnn opens its own Vulkan instance
    /// internally only if useVulkanCompute is true and a device is available,
    /// and falls back to CPU inference otherwise.
    ///
    /// @param isHdr Accepted for interface parity with LSFG_3_1; HDR pixel
    ///   formats are not implemented for this engine yet (see .cpp) and are
    ///   currently ignored (SDR path is always used).
    /// @param flowScale Accepted for interface parity; ncnn's exported graph
    ///   has no runtime flow-scale knob (baked in at export time), so this is
    ///   currently unused. Kept in the signature so callers don't need an
    ///   `#ifdef` at the call site.
    /// @param generationCount Number of extra frames to synthesize between
    ///   each input pair (same meaning as LSFG_3_1's parameter). Because the
    ///   underlying model only predicts the exact midpoint (t=0.5, no
    ///   variable-timestep input — see ncnn_framegen.cpp), only exact powers
    ///   of two are produced cleanly via recursive bisection; non-power-of-two
    ///   values are rounded down to the nearest supported count and a warning
    ///   is logged once.
    /// @param paramPath Filesystem path to the extracted *_ncnn.param file.
    /// @param binPath Filesystem path to the matching *_ncnn.bin file.
    /// @param useVulkanCompute Let ncnn use its own Vulkan compute backend
    ///   when available; falls back to CPU automatically on failure.
    ///
    /// @throws std::runtime_error if the model files can't be loaded.
    ///
    void initialize(bool isHdr, float flowScale, uint64_t generationCount,
        const std::string &paramPath, const std::string &binPath,
        bool useVulkanCompute);

    ///
    /// Create a new interpolation context. Unlike LSFG_3_1's Vulkan context,
    /// this doesn't allocate GPU resources up front beyond what ncnn::Net
    /// already holds — it just records the buffer set and dimensions for
    /// subsequent presentContext() calls.
    ///
    /// @return A unique identifier for the created context.
    ///
    int32_t createContextFromAHB(
        AHardwareBuffer *in0, AHardwareBuffer *in1,
        const std::vector<AHardwareBuffer *> &outN,
        VkExtent2D extent, VkFormat format);

    ///
    /// Run interpolation for a context. Synchronous: by the time this
    /// returns, every buffer in outN from createContextFromAHB has been
    /// written. inSem/outSem are IGNORED — accepted only so the call site
    /// in lsfg_render_loop.cpp doesn't need engine-specific branching around
    /// the semaphore arguments themselves. Do not rely on them for ordering
    /// with this engine; the synchronous return is the ordering guarantee.
    ///
    /// @throws std::runtime_error on an AHB lock failure or inference error.
    ///
    void presentContext(int32_t id, int inSem, const std::vector<int> &outSem);

    /// No-op: presentContext() is already synchronous for this engine. Kept
    /// for call-site parity with LSFG_3_1::waitIdle().
    void waitIdle();

    /// Delete a context created by createContextFromAHB.
    void deleteContext(int32_t id);

    /// Release the loaded model and any ncnn resources.
    void finalize();

} // namespace NcnnFG

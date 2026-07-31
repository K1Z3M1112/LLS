#pragma once

#include <android/asset_manager.h>
#include <cstdint>
#include <memory>

#include "net.h"

namespace lsfg_rife {

// Loads assets/models/rife/flownet.{param,bin} (a RIFE v4.x IFNet graph,
// see rife_warp_layer.hpp for the custom op it needs) and runs frame
// interpolation between two RGBA frames.
//
// STATUS: rife_warp_layer.cpp now has both a CPU and a Vulkan forward(), so
// the whole graph *can* run on GPU — but load() still pins
// net.opt.use_vulkan_compute = false by default (see kUseVulkanCompute
// below). Nobody has run the Vulkan path on a real device yet: the shader
// was written against ncnn's public Pipeline/VkCompute/compile_spirv_module
// API from documentation, not compiled or profiled. Flip the constant once
// someone has verified on-device that (a) pipeline creation/SPIR-V compile
// doesn't fail and (b) output matches the CPU path for a known frame pair
// — ideally behind a debug toggle rather than unconditionally, in case a
// given GPU/driver rejects the shader and needs a CPU fallback.
class RifeEngine {
public:
    RifeEngine();
    ~RifeEngine();

    RifeEngine(const RifeEngine&) = delete;
    RifeEngine& operator=(const RifeEngine&) = delete;

    // Registers the rife.Warp custom layer and loads the model from the
    // app's assets (via AAssetManager, no filesystem path needed).
    // Returns 0 on success, non-zero ncnn/asset error otherwise.
    int load(AAssetManager* assetManager,
              const char* paramAssetPath = "models/rife/flownet.param",
              const char* binAssetPath = "models/rife/flownet.bin");

    bool isLoaded() const { return m_loaded; }

    // Interpolates a frame timestep of the way between frame0 and frame1.
    // rgba0/rgba1: tightly packed RGBA8888 buffers, width*height*4 bytes.
    // outRgba: caller-allocated buffer, same size, overwritten with the
    // interpolated frame (alpha always written as 255).
    // timestep: 0 = returns frame0, 1 = returns frame1, 0.5 = the
    // temporal midpoint (this is the only value the released 2x
    // multiplier path needs; higher multipliers call this multiple times
    // with different fractional timesteps).
    // Returns 0 on success.
    int interpolate(const uint8_t* rgba0, const uint8_t* rgba1,
                     int width, int height, float timestep,
                     uint8_t* outRgba) const;

private:
    std::unique_ptr<ncnn::Net> m_net;
    bool m_loaded = false;
};

} // namespace lsfg_rife

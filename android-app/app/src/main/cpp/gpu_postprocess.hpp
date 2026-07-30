#pragma once

#include "ahb_image_bridge.hpp"

#include <cstdint>

namespace lsfg_android {

struct GpuPostProcessConfig {
    int   method    = 0;
    float sharpness = 0.5f;
    float strength  = 0.5f;
};

class GpuPostProcessor {
public:
    bool process(VulkanSession &vk,
                 const AhbImage &src,
                 const AhbImage &dst,
                 const GpuPostProcessConfig &config);

    void reset(VulkanSession &vk);

private:
    bool ensurePipeline(VulkanSession &vk);

    // Helper: return a cached VkImageView for the given AhbImage, creating it
    // on first use (or when the underlying VkImage changes). `slot` is 0 (src)
    // or 1 (dst).
    VkImageView getOrCreateView(VulkanSession &vk, const AhbImage &img, int slot);

    // --- Pipeline objects (created once, reused every frame) ---
    VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;
    VkPipelineLayout      pipelineLayout      = VK_NULL_HANDLE;
    VkPipeline            pipeline            = VK_NULL_HANDLE;
    VkDescriptorPool      descriptorPool      = VK_NULL_HANDLE;
    VkDescriptorSet       descriptorSet       = VK_NULL_HANDLE;

    // --- Persistent command buffer + fence ---
    //
    // Instead of allocating + freeing a VkCommandBuffer every frame and then
    // stalling on vkQueueWaitIdle(), we pre-allocate one CB and one fence.
    // At the START of process() we wait on the fence (only if the previous
    // submit is still in-flight), reset it, then re-record and re-submit the
    // CB.  This gives CPU-GPU overlap: the CPU returns from process() while
    // the GPU is still executing the compute work; the fence is checked on the
    // next call, not immediately.
    VkCommandBuffer persistCb    = VK_NULL_HANDLE;
    VkFence         persistFence = VK_NULL_HANDLE;
    bool            fenceArmed   = false;

    // --- ImageView cache ---
    //
    // MediaProjection and framegen reuse a small pool of AHardwareBuffers, so
    // the same VkImage pointer repeats every frame.  Creating and destroying
    // VkImageView on each call costs measurable driver overhead. We keep one
    // slot per binding (src=0, dst=1) and only recreate when the underlying
    // image or format changes.
    struct CachedView {
        VkImage     image  = VK_NULL_HANDLE;
        VkFormat    format = VK_FORMAT_UNDEFINED;
        VkImageView view   = VK_NULL_HANDLE;
    };
    CachedView viewCache[2]{};
};

} // namespace lsfg_android

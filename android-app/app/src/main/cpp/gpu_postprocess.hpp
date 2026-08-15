#pragma once

#include "ahb_image_bridge.hpp"

#include <cstdint>
#include <vector>

namespace lsfg_android {

// This is the only scaling technique in the app: the single-pass
// "sharp-bilinear-simple" reconstruction described at
// https://gamingprojects.wordpress.com/2017/12/03/reducing-pixel-blur-and-distortion/
// (see gpu_postprocess.comp for the math). No FSR, no Lanczos, no Bicubic,
// no plain-bilinear/unsharp fallback path.
//
//   sharpness  - 0..1. 1.0 matches the blog's technique exactly (full
//                integer pre-scale snap). 0.0 collapses to plain bilinear.
//   smoothness - 0..1. 0.0 matches the blog's technique exactly (linear
//                blend at the pixel boundary). 1.0 eases that blend with a
//                smoothstep curve to soften banding at non-integer scales.
struct GpuPostProcessConfig {
    float sharpness = 1.0f;
    float smoothness = 0.0f;
};

class GpuPostProcessor {
public:
    bool process(VulkanSession &vk,
                 const AhbImage &src,
                 const AhbImage &dst,
                 const GpuPostProcessConfig &config,
                 VkSemaphore waitSemaphore = VK_NULL_HANDLE);

    void reset(VulkanSession &vk);

private:
    struct PendingSubmit {
        VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
        VkImageView srcView = VK_NULL_HANDLE;
        VkImageView dstView = VK_NULL_HANDLE;
        VkDescriptorSet descriptorSet = VK_NULL_HANDLE;
        VkFence fence = VK_NULL_HANDLE;
    };
    void reapPending(VulkanSession &vk);
    bool ensurePipeline(VulkanSession &vk);
    VkImageView getCachedImageView(VulkanSession &vk, const AhbImage &img);
    VkDescriptorSet getCachedDescriptorSet(VulkanSession &vk,
                                           VkImage srcImage, VkImage dstImage,
                                           VkImageView srcView, VkImageView dstView);

    VkDescriptorSetLayout descriptorSetLayout = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    VkDescriptorSet descriptorSet = VK_NULL_HANDLE; // legacy single-set handle
    struct CachedImageView {
        VkImage image = VK_NULL_HANDLE;
        VkImageView view = VK_NULL_HANDLE;
    };
    struct CachedDescriptor {
        VkImage srcImage = VK_NULL_HANDLE;
        VkImage dstImage = VK_NULL_HANDLE;
        VkDescriptorSet set = VK_NULL_HANDLE;
    };
    std::vector<CachedImageView> imageViews;
    std::vector<CachedDescriptor> descriptorSets;
    std::vector<PendingSubmit> pending;
};

} // namespace lsfg_android

#include "gpu_postprocess.hpp"
#include "gpu_postprocess_spv.hpp"

#include <algorithm>

namespace lsfg_android {

namespace {

struct PushConstants {
    int32_t srcW;
    int32_t srcH;
    int32_t dstW;
    int32_t dstH;
    int32_t method;
    float   sharpness;
    float   strength;
    float   pad0;
};

} // namespace

// ---------------------------------------------------------------------------
// ImageView cache
// ---------------------------------------------------------------------------

VkImageView GpuPostProcessor::getOrCreateView(VulkanSession &vk,
                                               const AhbImage &img,
                                               int slot) {
    CachedView &cv = viewCache[slot];

    // Re-use cached view if the underlying image + format haven't changed.
    if (cv.view != VK_NULL_HANDLE &&
            cv.image  == img.image &&
            cv.format == img.format) {
        return cv.view;
    }

    // Destroy stale view before replacing it.
    if (cv.view != VK_NULL_HANDLE) {
        vk.fn.vkDestroyImageView(vk.device, cv.view, nullptr);
        cv.view = VK_NULL_HANDLE;
    }

    const VkImageViewCreateInfo info{
        .sType    = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO,
        .image    = img.image,
        .viewType = VK_IMAGE_VIEW_TYPE_2D,
        .format   = img.format,
        .components = {
            VK_COMPONENT_SWIZZLE_IDENTITY,
            VK_COMPONENT_SWIZZLE_IDENTITY,
            VK_COMPONENT_SWIZZLE_IDENTITY,
            VK_COMPONENT_SWIZZLE_IDENTITY,
        },
        .subresourceRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1},
    };
    VkImageView view = VK_NULL_HANDLE;
    if (vk.fn.vkCreateImageView(vk.device, &info, nullptr, &view) != VK_SUCCESS) {
        return VK_NULL_HANDLE;
    }

    cv.image  = img.image;
    cv.format = img.format;
    cv.view   = view;
    return view;
}

// ---------------------------------------------------------------------------
// Pipeline creation (once per lifetime)
// ---------------------------------------------------------------------------

bool GpuPostProcessor::ensurePipeline(VulkanSession &vk) {
    if (pipeline != VK_NULL_HANDLE) return true;

    VkDescriptorSetLayoutBinding bindings[2]{
        {
            .binding         = 0,
            .descriptorType  = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
            .descriptorCount = 1,
            .stageFlags      = VK_SHADER_STAGE_COMPUTE_BIT,
        },
        {
            .binding         = 1,
            .descriptorType  = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
            .descriptorCount = 1,
            .stageFlags      = VK_SHADER_STAGE_COMPUTE_BIT,
        },
    };
    const VkDescriptorSetLayoutCreateInfo dslInfo{
        .sType        = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
        .bindingCount = 2,
        .pBindings    = bindings,
    };
    if (vk.fn.vkCreateDescriptorSetLayout(vk.device, &dslInfo, nullptr,
            &descriptorSetLayout) != VK_SUCCESS) {
        reset(vk);
        return false;
    }

    const VkPushConstantRange pushRange{
        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
        .offset     = 0,
        .size       = sizeof(PushConstants),
    };
    const VkPipelineLayoutCreateInfo plInfo{
        .sType                  = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
        .setLayoutCount         = 1,
        .pSetLayouts            = &descriptorSetLayout,
        .pushConstantRangeCount = 1,
        .pPushConstantRanges    = &pushRange,
    };
    if (vk.fn.vkCreatePipelineLayout(vk.device, &plInfo, nullptr,
            &pipelineLayout) != VK_SUCCESS) {
        reset(vk);
        return false;
    }

    const VkShaderModuleCreateInfo smInfo{
        .sType    = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
        .codeSize = kGpuPostprocessCompSpvSize,
        .pCode    = kGpuPostprocessCompSpv,
    };
    VkShaderModule shader = VK_NULL_HANDLE;
    if (vk.fn.vkCreateShaderModule(vk.device, &smInfo, nullptr, &shader) != VK_SUCCESS) {
        reset(vk);
        return false;
    }
    const VkPipelineShaderStageCreateInfo stage{
        .sType  = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
        .stage  = VK_SHADER_STAGE_COMPUTE_BIT,
        .module = shader,
        .pName  = "main",
    };
    const VkComputePipelineCreateInfo pipeInfo{
        .sType  = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
        .stage  = stage,
        .layout = pipelineLayout,
    };
    const bool pipeOk = vk.fn.vkCreateComputePipelines(
        vk.device, VK_NULL_HANDLE, 1, &pipeInfo, nullptr, &pipeline) == VK_SUCCESS;
    vk.fn.vkDestroyShaderModule(vk.device, shader, nullptr);
    if (!pipeOk) {
        reset(vk);
        return false;
    }

    const VkDescriptorPoolSize poolSize{
        .type            = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
        .descriptorCount = 2,
    };
    const VkDescriptorPoolCreateInfo poolInfo{
        .sType         = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
        .maxSets       = 1,
        .poolSizeCount = 1,
        .pPoolSizes    = &poolSize,
    };
    if (vk.fn.vkCreateDescriptorPool(vk.device, &poolInfo, nullptr,
            &descriptorPool) != VK_SUCCESS) {
        reset(vk);
        return false;
    }

    const VkDescriptorSetAllocateInfo allocInfo{
        .sType              = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
        .descriptorPool     = descriptorPool,
        .descriptorSetCount = 1,
        .pSetLayouts        = &descriptorSetLayout,
    };
    if (vk.fn.vkAllocateDescriptorSets(vk.device, &allocInfo, &descriptorSet) != VK_SUCCESS) {
        reset(vk);
        return false;
    }

    // --- Persistent command buffer -------------------------------------------
    // Allocated once; re-recorded every frame via vkResetCommandBuffer.
    // Uses TRANSIENT_BIT so the driver may place it in fast scratch memory.
    VkCommandBufferAllocateInfo cbai{
        .sType              = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
        .commandPool        = vk.commandPool,
        .level              = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
        .commandBufferCount = 1,
    };
    if (vk.fn.vkAllocateCommandBuffers(vk.device, &cbai, &persistCb) != VK_SUCCESS) {
        reset(vk);
        return false;
    }

    // --- Persistent fence (starts unsignaled) --------------------------------
    const VkFenceCreateInfo fci{ .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO };
    if (vk.fn.vkCreateFence(vk.device, &fci, nullptr, &persistFence) != VK_SUCCESS) {
        reset(vk);
        return false;
    }
    fenceArmed = false;

    return true;
}

// ---------------------------------------------------------------------------
// Per-frame processing
// ---------------------------------------------------------------------------

bool GpuPostProcessor::process(VulkanSession &vk,
                                const AhbImage &src,
                                const AhbImage &dst,
                                const GpuPostProcessConfig &config) {
    if (src.image == VK_NULL_HANDLE || dst.image == VK_NULL_HANDLE) return false;
    if (!ensurePipeline(vk)) return false;

    // --- Wait for the previous frame's GPU work to finish -------------------
    // Only blocks if the prior submit hasn't retired yet (CPU-GPU overlap for
    // the common case where the GPU is fast enough).  On timeout we bail out
    // rather than risk resetting an armed fence that isn't signaled (UB per
    // Vulkan spec, observed to corrupt driver state on Adreno).
    if (fenceArmed) {
        const VkResult wr = vk.fn.vkWaitForFences(vk.device, 1, &persistFence,
                                                   VK_TRUE, 100ULL * 1'000'000ULL);
        if (wr != VK_SUCCESS) return false;
        vk.fn.vkResetFences(vk.device, 1, &persistFence);
        fenceArmed = false;
    }

    // --- Get (or create) cached image views ---------------------------------
    VkImageView srcView = getOrCreateView(vk, src, 0);
    VkImageView dstView = getOrCreateView(vk, dst, 1);
    if (srcView == VK_NULL_HANDLE || dstView == VK_NULL_HANDLE) return false;

    // --- Update descriptor set with current views ---------------------------
    const VkDescriptorImageInfo srcInfo{
        .imageView   = srcView,
        .imageLayout = VK_IMAGE_LAYOUT_GENERAL,
    };
    const VkDescriptorImageInfo dstInfo{
        .imageView   = dstView,
        .imageLayout = VK_IMAGE_LAYOUT_GENERAL,
    };
    VkWriteDescriptorSet writes[2]{
        {
            .sType           = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
            .dstSet          = descriptorSet,
            .dstBinding      = 0,
            .descriptorCount = 1,
            .descriptorType  = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
            .pImageInfo      = &srcInfo,
        },
        {
            .sType           = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
            .dstSet          = descriptorSet,
            .dstBinding      = 1,
            .descriptorCount = 1,
            .descriptorType  = VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
            .pImageInfo      = &dstInfo,
        },
    };
    vk.fn.vkUpdateDescriptorSets(vk.device, 2, writes, 0, nullptr);

    // --- Re-record the persistent command buffer ----------------------------
    if (vk.fn.vkResetCommandBuffer(persistCb, 0) != VK_SUCCESS) return false;

    const VkCommandBufferBeginInfo bi{
        .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        .flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    };
    vk.fn.vkBeginCommandBuffer(persistCb, &bi);

    const uint32_t foreign = VK_QUEUE_FAMILY_EXTERNAL;
    VkImageMemoryBarrier toSrc{
        .sType               = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask       = 0,
        .dstAccessMask       = VK_ACCESS_SHADER_READ_BIT,
        .oldLayout           = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout           = VK_IMAGE_LAYOUT_GENERAL,
        .srcQueueFamilyIndex = foreign,
        .dstQueueFamilyIndex = vk.computeFamilyIdx,
        .image               = src.image,
        .subresourceRange    = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1},
    };
    VkImageMemoryBarrier toDst{
        .sType               = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask       = 0,
        .dstAccessMask       = VK_ACCESS_SHADER_WRITE_BIT,
        .oldLayout           = VK_IMAGE_LAYOUT_UNDEFINED,
        .newLayout           = VK_IMAGE_LAYOUT_GENERAL,
        .srcQueueFamilyIndex = foreign,
        .dstQueueFamilyIndex = vk.computeFamilyIdx,
        .image               = dst.image,
        .subresourceRange    = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1},
    };
    VkImageMemoryBarrier pre[2] = {toSrc, toDst};
    vk.fn.vkCmdPipelineBarrier(persistCb,
        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        0, 0, nullptr, 0, nullptr, 2, pre);

    const PushConstants pc{
        .srcW      = static_cast<int32_t>(src.extent.width),
        .srcH      = static_cast<int32_t>(src.extent.height),
        .dstW      = static_cast<int32_t>(dst.extent.width),
        .dstH      = static_cast<int32_t>(dst.extent.height),
        .method    = std::clamp(config.method, 0, 15),
        .sharpness = std::clamp(config.sharpness, 0.0f, 1.0f),
        .strength  = std::clamp(config.strength,  0.0f, 1.0f),
        .pad0      = 0.0f,
    };
    vk.fn.vkCmdBindPipeline(persistCb, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline);
    vk.fn.vkCmdBindDescriptorSets(persistCb, VK_PIPELINE_BIND_POINT_COMPUTE,
                                   pipelineLayout, 0, 1, &descriptorSet, 0, nullptr);
    vk.fn.vkCmdPushConstants(persistCb, pipelineLayout, VK_SHADER_STAGE_COMPUTE_BIT,
                              0, sizeof(PushConstants), &pc);
    // Workgroup is 16×8 (see gpu_postprocess.comp).
    vk.fn.vkCmdDispatch(persistCb,
        (dst.extent.width  + 15) / 16,
        (dst.extent.height +  7) /  8,
        1);

    VkImageMemoryBarrier releaseSrc{
        .sType               = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask       = VK_ACCESS_SHADER_READ_BIT,
        .dstAccessMask       = 0,
        .oldLayout           = VK_IMAGE_LAYOUT_GENERAL,
        .newLayout           = VK_IMAGE_LAYOUT_GENERAL,
        .srcQueueFamilyIndex = vk.computeFamilyIdx,
        .dstQueueFamilyIndex = foreign,
        .image               = src.image,
        .subresourceRange    = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1},
    };
    VkImageMemoryBarrier releaseDst{
        .sType               = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
        .srcAccessMask       = VK_ACCESS_SHADER_WRITE_BIT,
        .dstAccessMask       = VK_ACCESS_MEMORY_READ_BIT,
        .oldLayout           = VK_IMAGE_LAYOUT_GENERAL,
        .newLayout           = VK_IMAGE_LAYOUT_GENERAL,
        .srcQueueFamilyIndex = vk.computeFamilyIdx,
        .dstQueueFamilyIndex = foreign,
        .image               = dst.image,
        .subresourceRange    = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1},
    };
    VkImageMemoryBarrier post[2] = {releaseSrc, releaseDst};
    vk.fn.vkCmdPipelineBarrier(persistCb,
        VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
        VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
        0, 0, nullptr, 0, nullptr, 2, post);

    vk.fn.vkEndCommandBuffer(persistCb);

    // --- Submit with fence — do NOT wait here (CPU-GPU overlap) --------------
    // The fence is checked at the START of the next process() call.
    const VkSubmitInfo si{
        .sType              = VK_STRUCTURE_TYPE_SUBMIT_INFO,
        .commandBufferCount = 1,
        .pCommandBuffers    = &persistCb,
    };
    const bool ok = vk.fn.vkQueueSubmit(vk.computeQueue, 1, &si, persistFence) == VK_SUCCESS;
    if (ok) fenceArmed = true;
    return ok;
}

// ---------------------------------------------------------------------------
// Cleanup
// ---------------------------------------------------------------------------

void GpuPostProcessor::reset(VulkanSession &vk) {
    // Drain any in-flight GPU work before freeing resources.
    if (fenceArmed && vk.device != VK_NULL_HANDLE &&
            persistFence != VK_NULL_HANDLE &&
            vk.fn.vkWaitForFences != nullptr) {
        vk.fn.vkWaitForFences(vk.device, 1, &persistFence,
                               VK_TRUE, 500ULL * 1'000'000ULL);
        fenceArmed = false;
    }

    if (vk.device == VK_NULL_HANDLE) {
        descriptorSetLayout = VK_NULL_HANDLE;
        pipelineLayout      = VK_NULL_HANDLE;
        pipeline            = VK_NULL_HANDLE;
        descriptorPool      = VK_NULL_HANDLE;
        descriptorSet       = VK_NULL_HANDLE;
        persistCb           = VK_NULL_HANDLE;
        persistFence        = VK_NULL_HANDLE;
        for (auto &cv : viewCache) cv = {};
        return;
    }

    // Destroy cached image views.
    for (auto &cv : viewCache) {
        if (cv.view != VK_NULL_HANDLE && vk.fn.vkDestroyImageView != nullptr) {
            vk.fn.vkDestroyImageView(vk.device, cv.view, nullptr);
        }
        cv = {};
    }

    if (persistFence != VK_NULL_HANDLE && vk.fn.vkDestroyFence != nullptr) {
        vk.fn.vkDestroyFence(vk.device, persistFence, nullptr);
    }
    persistFence = VK_NULL_HANDLE;

    // persistCb is owned by vk.commandPool — freed implicitly by
    // vkDestroyCommandPool; no explicit vkFreeCommandBuffers needed here.
    persistCb = VK_NULL_HANDLE;

    if (descriptorPool != VK_NULL_HANDLE && vk.fn.vkDestroyDescriptorPool != nullptr) {
        vk.fn.vkDestroyDescriptorPool(vk.device, descriptorPool, nullptr);
    }
    descriptorPool = VK_NULL_HANDLE;
    descriptorSet  = VK_NULL_HANDLE;

    if (pipeline != VK_NULL_HANDLE && vk.fn.vkDestroyPipeline != nullptr) {
        vk.fn.vkDestroyPipeline(vk.device, pipeline, nullptr);
    }
    pipeline = VK_NULL_HANDLE;

    if (pipelineLayout != VK_NULL_HANDLE && vk.fn.vkDestroyPipelineLayout != nullptr) {
        vk.fn.vkDestroyPipelineLayout(vk.device, pipelineLayout, nullptr);
    }
    pipelineLayout = VK_NULL_HANDLE;

    if (descriptorSetLayout != VK_NULL_HANDLE && vk.fn.vkDestroyDescriptorSetLayout != nullptr) {
        vk.fn.vkDestroyDescriptorSetLayout(vk.device, descriptorSetLayout, nullptr);
    }
    descriptorSetLayout = VK_NULL_HANDLE;
}

} // namespace lsfg_android

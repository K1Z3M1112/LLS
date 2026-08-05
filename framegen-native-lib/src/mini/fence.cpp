#include "mini/fence.hpp"
#include "common/exception.hpp"
#include "layer.hpp"

#include <vulkan/vulkan_core.h>

#include <memory>

using namespace Mini;

Fence::Fence(VkDevice device) : device(device) {
    // create fence (unsignaled — signaled once the submission it's
    // attached to has finished executing on the GPU)
    const VkFenceCreateInfo desc{
        .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO
    };
    VkFence fenceHandle{};
    auto res = Layer::ovkCreateFence(device, &desc, nullptr, &fenceHandle);
    if (res != VK_SUCCESS || fenceHandle == VK_NULL_HANDLE)
        throw LSFG::vulkan_error(res, "Unable to create fence");

    // store fence in shared ptr
    this->fence = std::shared_ptr<VkFence>(
        new VkFence(fenceHandle),
        [dev = device](VkFence* fenceHandle) {
            Layer::ovkDestroyFence(dev, *fenceHandle, nullptr);
        }
    );
}

void Fence::wait(uint64_t timeout) const {
    VkFence fenceHandle = *this->fence;
    auto res = Layer::ovkWaitForFences(this->device, 1, &fenceHandle, VK_TRUE, timeout);
    if (res != VK_SUCCESS)
        throw LSFG::vulkan_error(res, "Unable to wait for fence");
}

#include "mini/buffer.hpp"
#include "common/exception.hpp"
#include "layer.hpp"

#include <vulkan/vulkan_core.h>

#include <memory>
#include <optional>
#include <cstdint>

using namespace Mini;

Buffer::Buffer(VkDevice device, VkPhysicalDevice physicalDevice,
        VkDeviceSize size, VkBufferUsageFlags usage)
        : bufSize(size) {
    // create buffer
    const VkBufferCreateInfo desc{
        .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
        .size = size,
        .usage = usage,
        .sharingMode = VK_SHARING_MODE_EXCLUSIVE
    };
    VkBuffer bufferHandle{};
    auto res = Layer::ovkCreateBuffer(device, &desc, nullptr, &bufferHandle);
    if (res != VK_SUCCESS || bufferHandle == VK_NULL_HANDLE)
        throw LSFG::vulkan_error(res, "Failed to create Vulkan buffer");

    // find a HOST_VISIBLE | HOST_COHERENT memory type, so we can map it
    // without manual flush/invalidate calls
    VkPhysicalDeviceMemoryProperties memProps;
    Layer::ovkGetPhysicalDeviceMemoryProperties(physicalDevice, &memProps);

    VkMemoryRequirements memReqs;
    Layer::ovkGetBufferMemoryRequirements(device, bufferHandle, &memReqs);

    constexpr VkMemoryPropertyFlags wantFlags =
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wunsafe-buffer-usage"
    std::optional<uint32_t> memType{};
    for (uint32_t i = 0; i < memProps.memoryTypeCount; ++i) {
        if ((memReqs.memoryTypeBits & (1 << i)) && // NOLINTBEGIN
            (memProps.memoryTypes[i].propertyFlags & wantFlags) == wantFlags) {
            memType.emplace(i);
            break;
        } // NOLINTEND
    }
    if (!memType.has_value())
        throw LSFG::vulkan_error(VK_ERROR_UNKNOWN, "Unable to find host-visible memory type for buffer");
#pragma clang diagnostic pop

    // allocate and bind memory
    const VkMemoryAllocateInfo allocInfo{
        .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
        .allocationSize = memReqs.size,
        .memoryTypeIndex = memType.value()
    };
    VkDeviceMemory memoryHandle{};
    res = Layer::ovkAllocateMemory(device, &allocInfo, nullptr, &memoryHandle);
    if (res != VK_SUCCESS || memoryHandle == VK_NULL_HANDLE)
        throw LSFG::vulkan_error(res, "Failed to allocate memory for Vulkan buffer");

    res = Layer::ovkBindBufferMemory(device, bufferHandle, memoryHandle, 0);
    if (res != VK_SUCCESS)
        throw LSFG::vulkan_error(res, "Failed to bind memory to Vulkan buffer");

    // map persistently -- HOST_COHERENT means no explicit flush/invalidate needed
    void* mappedPtr{};
    res = Layer::ovkMapMemory(device, memoryHandle, 0, VK_WHOLE_SIZE, 0, &mappedPtr);
    if (res != VK_SUCCESS || mappedPtr == nullptr)
        throw LSFG::vulkan_error(res, "Failed to map memory for Vulkan buffer");
    this->mapped = mappedPtr;

    // store objects in shared ptr; unmap happens implicitly when the device/
    // memory is destroyed, so we don't bother with an explicit unmap deleter
    this->buffer = std::shared_ptr<VkBuffer>(
        new VkBuffer(bufferHandle),
        [dev = device](VkBuffer* buf) {
            Layer::ovkDestroyBuffer(dev, *buf, nullptr);
        }
    );
    this->memory = std::shared_ptr<VkDeviceMemory>(
        new VkDeviceMemory(memoryHandle),
        [dev = device](VkDeviceMemory* mem) {
            Layer::ovkUnmapMemory(dev, *mem);
            Layer::ovkFreeMemory(dev, *mem, nullptr);
        }
    );
}

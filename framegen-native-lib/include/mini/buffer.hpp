#pragma once

#include <vulkan/vulkan_core.h>

#include <cstddef>
#include <cstdint>
#include <memory>

namespace Mini {

    ///
    /// C++ wrapper class for a host-visible, host-coherent Vulkan buffer.
    ///
    /// This exists purely to shuttle pixel data between the GPU-resident
    /// frame_0/frame_1/out_n images and host memory, so that the NCNN
    /// inference backend (which operates on plain host buffers) can read
    /// input frames and write generated frames back. It is NOT used by the
    /// normal Lossless.dll/lsfg pipeline, which stays fully GPU-resident.
    ///
    class Buffer {
    public:
        Buffer() noexcept = default;

        ///
        /// Create a host-visible buffer large enough to hold `size` bytes.
        ///
        /// @param device Vulkan device
        /// @param physicalDevice Vulkan physical device
        /// @param size Size of the buffer in bytes.
        /// @param usage Usage flags for the buffer (e.g. TRANSFER_SRC/DST).
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        Buffer(VkDevice device, VkPhysicalDevice physicalDevice,
            VkDeviceSize size, VkBufferUsageFlags usage);

        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return *this->buffer; }
        /// Get the size of the buffer in bytes.
        [[nodiscard]] VkDeviceSize size() const { return this->bufSize; }

        ///
        /// Map the buffer and get a pointer to its host-visible memory.
        /// The mapping persists for the lifetime of the buffer (mapped once
        /// at creation time, since HOST_COHERENT means no explicit flush is
        /// needed).
        ///
        [[nodiscard]] void* data() const { return this->mapped; }

        /// Trivially copyable, moveable and destructible
        Buffer(const Buffer&) noexcept = default;
        Buffer& operator=(const Buffer&) noexcept = default;
        Buffer(Buffer&&) noexcept = default;
        Buffer& operator=(Buffer&&) noexcept = default;
        ~Buffer() = default;
    private:
        std::shared_ptr<VkBuffer> buffer;
        std::shared_ptr<VkDeviceMemory> memory;
        void* mapped{};
        VkDeviceSize bufSize{};
    };

}

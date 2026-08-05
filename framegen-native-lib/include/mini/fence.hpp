#pragma once

#include <vulkan/vulkan_core.h>

#include <memory>

namespace Mini {

    ///
    /// C++ wrapper class for a Vulkan fence.
    ///
    /// Used to wait for a *specific* piece of GPU work to finish, as a
    /// cheaper, more targeted alternative to a full vkDeviceWaitIdle().
    /// A device-wide idle wait blocks on everything the device is doing
    /// (including unrelated work the app may have queued), which adds
    /// avoidable latency to every generated frame. Waiting on a fence
    /// only blocks until the one submission that signals it is done.
    ///
    class Fence {
    public:
        Fence() noexcept = default;

        ///
        /// Create the fence (unsignaled).
        ///
        /// @param device Vulkan device
        ///
        /// @throws LSFG::vulkan_error if object creation fails.
        ///
        Fence(VkDevice device);

        ///
        /// Block the calling thread until the fence is signaled.
        ///
        /// @param timeout Timeout in nanoseconds (default: no timeout).
        ///
        /// @throws LSFG::vulkan_error if waiting fails.
        ///
        void wait(uint64_t timeout = UINT64_MAX) const;

        /// Get the Vulkan handle.
        [[nodiscard]] auto handle() const { return *this->fence; }

        // Trivially copyable, moveable and destructible
        Fence(const Fence&) noexcept = default;
        Fence& operator=(const Fence&) noexcept = default;
        Fence(Fence&&) noexcept = default;
        Fence& operator=(Fence&&) noexcept = default;
        ~Fence() = default;
    private:
        VkDevice device{};
        std::shared_ptr<VkFence> fence;
    };

}

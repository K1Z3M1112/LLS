#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace Inference {

    ///
    /// A single RGBA8 frame stored in host memory, row-major, tightly packed
    /// (stride == width * 4). Boundary type between Vulkan staging buffers
    /// and the NCNN backend.
    ///
    struct HostFrame {
        uint32_t width{};
        uint32_t height{};
        std::vector<uint8_t> rgba; // width * height * 4 bytes, RGBA8
    };

    ///
    /// NCNN-based frame interpolation backend.
    /// Loads a custom IFNetLite/RIFE-lite student model (.param/.bin pair)
    /// and generates intermediate frames via recursive midpoint subdivision.
    ///
    /// Both FP32 and FP16 exports are supported transparently — ncnn reads
    /// the weight storage type from the .param/.bin pair itself.
    ///
    class NcnnBackend {
    public:
        NcnnBackend();

        ///
        /// Load a model. `paramPath` points at the `*.ncnn.param` file;
        /// the matching `*.ncnn.bin` is expected in the same directory.
        ///
        /// Tries Vulkan compute first, falls back to CPU.
        /// For fp16 exports, automatically retries with the sibling fp32
        /// export if loading fails.
        ///
        /// @throws std::runtime_error if no usable model could be loaded.
        ///
        void load(const std::string& paramPath);

        /// Whether a model has been successfully loaded.
        [[nodiscard]] bool isLoaded() const { return this->loaded; }
        /// Whether ncnn is using Vulkan compute (false = CPU fallback).
        [[nodiscard]] bool isUsingVulkan() const { return this->usingVulkan; }

        ///
        /// Generate `count` intermediate frames evenly spaced between `a`
        /// (t=0) and `b` (t=1), in order.
        ///
        [[nodiscard]] std::vector<HostFrame> interpolate(
            const HostFrame& a, const HostFrame& b, uint32_t count);

        // non-copyable (owns GPU/model resources)
        NcnnBackend(const NcnnBackend&) = delete;
        NcnnBackend& operator=(const NcnnBackend&) = delete;
        NcnnBackend(NcnnBackend&&) noexcept = default;
        NcnnBackend& operator=(NcnnBackend&&) noexcept = default;
        ~NcnnBackend();

    private:
        struct Impl;
        std::unique_ptr<Impl> impl;
        bool loaded{false};
        bool usingVulkan{false};
    };

} // namespace Inference

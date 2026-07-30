#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace Inference {

    ///
    /// A single RGBA8 frame stored in host memory, row-major, tightly packed
    /// (stride == width * 4). This is the boundary type used to move image
    /// data between the Vulkan-side staging buffers and the NCNN backend --
    /// it deliberately knows nothing about Vulkan.
    ///
    struct HostFrame {
        uint32_t width{};
        uint32_t height{};
        std::vector<uint8_t> rgba; // width * height * 4 bytes, RGBA8
    };

    ///
    /// Loads and runs a custom-trained frame-interpolation model (see the
    /// "Mobile Frame Interpolation Trainer" notebook -- an IFNetLite/RIFE-lite
    /// student network trained from scratch) via ncnn, as a self-contained
    /// alternative to the extracted Lossless.dll/Vulkan pipeline.
    ///
    /// The model takes two RGB frames and predicts the frame exactly halfway
    /// between them (t=0.5). Generating more than one intermediate frame is
    /// done by recursively predicting the midpoint of the largest remaining
    /// gap, which is the standard way to get N evenly-spaced frames out of a
    /// midpoint-only interpolator.
    ///
    /// Both FP32 and FP16 exports (see the notebook's NCNN export cell) are
    /// supported transparently -- ncnn reads the weight storage type from the
    /// .param/.bin pair itself, so this class does not need to know which one
    /// it was given.
    ///
    class NcnnBackend {
    public:
        NcnnBackend();

        ///
        /// Load a model. `paramPath` should point at the `*.ncnn.param` file;
        /// the matching `*.ncnn.bin` is expected next to it with the same
        /// stem (this is exactly what the notebook's export cell produces).
        ///
        /// Tries Vulkan compute first (using ncnn's own, independent Vulkan
        /// device -- this does not share device state with the game's or
        /// lsfg's Vulkan devices, which keeps this backend simple and
        /// self-contained at the cost of a host round-trip per frame). Falls
        /// back to the CPU backend if Vulkan is unavailable or pipeline
        /// creation fails.
        ///
        /// If `paramPath` looks like an FP16 export (filename contains
        /// "fp16") and the model fails to load -- e.g. because the GPU
        /// doesn't support fp16 storage -- this automatically retries with
        /// the sibling FP32 export (same path with "fp16" replaced by
        /// "fp32"), matching the fallback behaviour described in the
        /// training notebook. If that sibling doesn't exist, the original
        /// error is thrown.
        ///
        /// @throws std::runtime_error if no usable model could be loaded.
        ///
        void load(const std::string& paramPath);

        /// Whether a model has successfully been loaded.
        [[nodiscard]] bool isLoaded() const { return this->loaded; }
        /// Whether inference is currently running on the GPU via ncnn's Vulkan backend
        /// (false means it fell back to CPU).
        [[nodiscard]] bool isUsingVulkan() const { return this->usingVulkan; }

        ///
        /// Generate `count` intermediate frames evenly spaced between `a`
        /// (t=0) and `b` (t=1), in order from a to b.
        ///
        /// @param a First frame.
        /// @param b Second frame. Must be the same size as `a`.
        /// @param count Number of intermediate frames to generate. 0 returns an empty vector.
        /// @return Generated frames, length == count.
        ///
        /// @throws std::runtime_error if inference fails or no model is loaded.
        ///
        [[nodiscard]] std::vector<HostFrame> interpolate(
            const HostFrame& a, const HostFrame& b, uint32_t count);

        // non-copyable (owns GPU/model resources), moveable
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

}

#include "inference/ncnn_backend.hpp"

#include <ncnn/net.h>
#include <ncnn/mat.h>
#include <ncnn/gpu.h>

#include <algorithm>
#include <filesystem>
#include <iostream>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

using namespace Inference;

namespace {
    // ncnn's Vulkan instance is a single global resource; keep a process-wide
    // refcount so multiple NcnnBackend instances (e.g. hot-reloading config)
    // don't stomp on each other.
    std::mutex g_gpuMutex;
    int g_gpuRefCount = 0;

    void acquireGpuInstance() {
        const std::lock_guard<std::mutex> lock(g_gpuMutex);
        if (g_gpuRefCount == 0)
            ncnn::create_gpu_instance();
        ++g_gpuRefCount;
    }

    void releaseGpuInstance() {
        const std::lock_guard<std::mutex> lock(g_gpuMutex);
        if (--g_gpuRefCount == 0)
            ncnn::destroy_gpu_instance();
    }

    // Replace the LAST occurrence of "fp16" in a filename stem with "fp32".
    // Used for the fp16->fp32 fallback path.
    std::string fp16PathToFp32(const std::string& path) {
        auto pos = path.rfind("fp16");
        if (pos == std::string::npos)
            return {};
        std::string out = path;
        out.replace(pos, 4, "fp32");
        return out;
    }
}

struct NcnnBackend::Impl {
    ncnn::Net net;
    int inputBlob0{-1};
    int inputBlob1{-1};
    int outputBlob{-1};
    bool gpuAcquired{false};

    ~Impl() {
        net.clear();
        if (gpuAcquired)
            releaseGpuInstance();
    }

    // Try to load a .ncnn.param/.ncnn.bin pair with a given vulkan preference.
    // Returns true on success.
    bool tryLoad(const std::string& paramPath, bool useVulkan) {
        if (!std::filesystem::exists(paramPath)) {
            std::cerr << "lsfg-vk (ncnn): model file not found: " << paramPath << '\n';
            return false;
        }
        // paramPath is expected to end in ".param" -> strip that suffix to find the .bin
        std::string stem = paramPath;
        constexpr std::string_view paramSuffix = ".param";
        if (stem.size() > paramSuffix.size() &&
                stem.compare(stem.size() - paramSuffix.size(), paramSuffix.size(), paramSuffix) == 0)
            stem.erase(stem.size() - paramSuffix.size());
        const std::string binFile = stem + ".bin";
        if (!std::filesystem::exists(binFile)) {
            std::cerr << "lsfg-vk (ncnn): matching .bin not found next to " << paramPath
                       << " (expected " << binFile << ")\n";
            return false;
        }

        net.clear();

        net.opt = ncnn::Option();
        net.opt.num_threads = static_cast<int>(std::max(1U, std::thread::hardware_concurrency()));
        net.opt.use_vulkan_compute = useVulkan;

        if (useVulkan) {
            if (!this->gpuAcquired) {
                acquireGpuInstance();
                this->gpuAcquired = true;
            }
            auto* gpuDevice = ncnn::get_gpu_device();
            if (!gpuDevice) {
                std::cerr << "lsfg-vk (ncnn): no usable Vulkan device for ncnn, falling back to CPU\n";
                net.opt.use_vulkan_compute = false;
            } else {
                // Never hardcode based on GPU model -- ask ncnn what the device
                // actually supports and let it decide. It will silently disable
                // fp16 arithmetic itself if unsupported; we just mirror that
                // for storage too so we don't force a format the device can't
                // consume efficiently.
                const auto& info = gpuDevice->info;
                net.opt.use_fp16_packed = info.support_fp16_packed();
                net.opt.use_fp16_storage = info.support_fp16_storage();
                net.opt.use_fp16_arithmetic = info.support_fp16_arithmetic();
                net.opt.use_int8_storage = false;
                net.opt.use_int8_arithmetic = false;
                net.set_vulkan_device(gpuDevice);
            }
        }

        if (net.load_param(paramPath.c_str()) != 0) {
            std::cerr << "lsfg-vk (ncnn): failed to parse " << paramPath << '\n';
            return false;
        }
        if (net.load_model(binFile.c_str()) != 0) {
            std::cerr << "lsfg-vk (ncnn): failed to load weights " << binFile << '\n';
            return false;
        }

        // Resolve input/output blobs positionally -- robust regardless of
        // whatever names pnnx assigned them.
        const auto& inputIndexes = net.input_indexes();
        const auto& outputIndexes = net.output_indexes();
        if (inputIndexes.size() < 2 || outputIndexes.empty()) {
            std::cerr << "lsfg-vk (ncnn): model has unexpected I/O shape ("
                       << inputIndexes.size() << " inputs, " << outputIndexes.size() << " outputs), "
                       << "expected >=2 inputs (frame_a, frame_c) and >=1 output (predicted mid frame)\n";
            return false;
        }
        this->inputBlob0 = inputIndexes[0];
        this->inputBlob1 = inputIndexes[1];
        this->outputBlob = outputIndexes[0];
        return true;
    }

    // Predict the frame exactly halfway between a and b.
    [[nodiscard]] ncnn::Mat predictMid(const ncnn::Mat& a, const ncnn::Mat& b) const {
        ncnn::Extractor ex = net.create_extractor();
        ex.input(this->inputBlob0, a);
        ex.input(this->inputBlob1, b);
        ncnn::Mat out;
        if (ex.extract(this->outputBlob, out) != 0)
            throw std::runtime_error("lsfg-vk (ncnn): inference failed");
        return out;
    }
};

NcnnBackend::NcnnBackend() : impl(std::make_unique<Impl>()) {}
NcnnBackend::~NcnnBackend() = default;

void NcnnBackend::load(const std::string& paramPath) {
    this->loaded = false;
    this->usingVulkan = false;

    // 1. try Vulkan first
    if (this->impl->tryLoad(paramPath, /*useVulkan=*/true)) {
        this->loaded = true;
        this->usingVulkan = this->impl->net.opt.use_vulkan_compute;
        return;
    }

    // 2. try CPU with the same model
    if (this->impl->tryLoad(paramPath, /*useVulkan=*/false)) {
        this->loaded = true;
        return;
    }

    // 3. if this looked like an fp16 export, fall back to the sibling fp32 export
    //    (matches the guidance in the training notebook)
    const std::string fp32Path = fp16PathToFp32(paramPath);
    if (!fp32Path.empty() && fp32Path != paramPath) {
        std::cerr << "lsfg-vk (ncnn): fp16 model failed to load, trying fp32 fallback: "
                   << fp32Path << '\n';
        if (this->impl->tryLoad(fp32Path, /*useVulkan=*/true)) {
            this->loaded = true;
            this->usingVulkan = this->impl->net.opt.use_vulkan_compute;
            return;
        }
        if (this->impl->tryLoad(fp32Path, /*useVulkan=*/false)) {
            this->loaded = true;
            return;
        }
    }

    throw std::runtime_error("lsfg-vk (ncnn): unable to load NCNN model from " + paramPath);
}

namespace {
    ncnn::Mat toNcnnMat(const HostFrame& frame) {
        // RGBA8 host buffer -> 3-channel float Mat in [0,1], matching how the
        // model was trained (cv2 BGR2RGB, then /255.0).
        ncnn::Mat mat = ncnn::Mat::from_pixels(frame.rgba.data(), ncnn::Mat::PIXEL_RGBA2RGB,
            static_cast<int>(frame.width), static_cast<int>(frame.height));
        constexpr float norm[3] = { 1.0F / 255.0F, 1.0F / 255.0F, 1.0F / 255.0F };
        mat.substract_mean_normalize(nullptr, norm);
        return mat;
    }

    HostFrame fromNcnnMat(const ncnn::Mat& mat, uint32_t width, uint32_t height) {
        // model output is already clamped to [0,1] (torch.clamp in forward()) --
        // scale back up to 0-255 and pack into RGBA8 (alpha forced to opaque).
        ncnn::Mat scaled = mat.clone();
        constexpr float scale[3] = { 255.0F, 255.0F, 255.0F };
        scaled.substract_mean_normalize(nullptr, scale); // multiply-only normalize (mean=0)

        HostFrame out;
        out.width = width;
        out.height = height;
        out.rgba.resize(static_cast<size_t>(width) * height * 4);
        scaled.to_pixels(out.rgba.data(), ncnn::Mat::PIXEL_RGB2RGBA);
        // to_pixels for RGB2RGBA leaves alpha undefined/0 in some ncnn builds -- force opaque.
        for (size_t i = 3; i < out.rgba.size(); i += 4)
            out.rgba[i] = 255;
        return out;
    }
}

std::vector<HostFrame> NcnnBackend::interpolate(
        const HostFrame& a, const HostFrame& b, uint32_t count) {
    if (!this->loaded)
        throw std::runtime_error("lsfg-vk (ncnn): interpolate() called before a model was loaded");
    if (count == 0)
        return {};
    if (a.width != b.width || a.height != b.height)
        throw std::runtime_error("lsfg-vk (ncnn): frame_a and frame_c size mismatch");

    // Recursive midpoint subdivision: repeatedly split the largest remaining
    // gap by predicting its midpoint, using previously generated frames as
    // new anchors. This is the standard way to get N evenly-spaced frames
    // out of a midpoint-only (t=0.5) interpolator, and produces exact,
    // evenly-spaced output whenever count == 2^k - 1 (i.e. multiplier is a
    // power of two: 2x -> 1 frame, 4x -> 3 frames, 8x -> 7 frames, ...).
    // For other multipliers (e.g. 3x) the spacing is only approximately even
    // -- there's no way around that with a fixed-t=0.5 model; a true 1/3 and
    // 2/3 timestep would require a timestep-conditioned network.
    struct Node {
        double t;
        ncnn::Mat img;
    };
    std::vector<Node> nodes;
    nodes.push_back({0.0, toNcnnMat(a)});
    nodes.push_back({1.0, toNcnnMat(b)});

    for (uint32_t i = 0; i < count; ++i) {
        // find the largest gap between adjacent nodes
        size_t bestIdx = 0;
        double bestGap = -1.0;
        for (size_t j = 0; j + 1 < nodes.size(); ++j) {
            const double gap = nodes[j + 1].t - nodes[j].t;
            if (gap > bestGap) {
                bestGap = gap;
                bestIdx = j;
            }
        }

        const double midT = (nodes[bestIdx].t + nodes[bestIdx + 1].t) / 2.0;
        ncnn::Mat midImg = this->impl->predictMid(nodes[bestIdx].img, nodes[bestIdx + 1].img);
        nodes.insert(nodes.begin() + static_cast<std::ptrdiff_t>(bestIdx) + 1, {midT, std::move(midImg)});
    }

    std::vector<HostFrame> result;
    result.reserve(count);
    for (size_t i = 1; i + 1 < nodes.size(); ++i)
        result.push_back(fromNcnnMat(nodes[i].img, a.width, a.height));
    return result;
}

#ifdef LSFGVK_ENABLE_NCNN

#include "ncnn_backend.hpp"

#include <net.h>
#include <mat.h>
#include <gpu.h>

#include <algorithm>
#include <filesystem>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

using namespace Inference;

namespace {
    // ncnn's Vulkan instance is a single global resource; keep a refcount so
    // multiple NcnnBackend instances don't stomp on each other.
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

    // Replace the last "fp16" in a filename stem with "fp32" for auto-fallback.
    std::string fp16PathToFp32(const std::string& path) {
        auto pos = path.rfind("fp16");
        if (pos == std::string::npos) return {};
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

    bool tryLoad(const std::string& paramPath, bool useVulkan) {
        if (!std::filesystem::exists(paramPath)) return false;

        // Derive .bin from .param
        std::string stem = paramPath;
        constexpr std::string_view paramSuffix = ".param";
        if (stem.size() > paramSuffix.size() &&
                stem.compare(stem.size() - paramSuffix.size(),
                             paramSuffix.size(), paramSuffix) == 0)
            stem.erase(stem.size() - paramSuffix.size());
        const std::string binFile = stem + ".bin";
        if (!std::filesystem::exists(binFile)) return false;

        net.clear();
        net.opt = ncnn::Option();
        net.opt.num_threads = static_cast<int>(
            std::max(1U, std::thread::hardware_concurrency()));
        net.opt.use_vulkan_compute = useVulkan;

        if (useVulkan) {
            if (!this->gpuAcquired) {
                acquireGpuInstance();
                this->gpuAcquired = true;
            }
            auto* gpuDevice = ncnn::get_gpu_device();
            if (!gpuDevice) {
                net.opt.use_vulkan_compute = false;
            } else {
                const auto& info = gpuDevice->info;
                net.opt.use_fp16_packed   = info.support_fp16_packed();
                net.opt.use_fp16_storage  = info.support_fp16_storage();
                net.opt.use_fp16_arithmetic = info.support_fp16_arithmetic();
                net.opt.use_int8_storage  = false;
                net.opt.use_int8_arithmetic = false;
                net.set_vulkan_device(gpuDevice);
            }
        }

        if (net.load_param(paramPath.c_str()) != 0) return false;
        if (net.load_model(binFile.c_str()) != 0) return false;

        const auto& inputIndexes  = net.input_indexes();
        const auto& outputIndexes = net.output_indexes();
        if (inputIndexes.size() < 2 || outputIndexes.empty()) return false;

        this->inputBlob0 = inputIndexes[0];
        this->inputBlob1 = inputIndexes[1];
        this->outputBlob = outputIndexes[0];
        return true;
    }

    [[nodiscard]] ncnn::Mat predictMid(const ncnn::Mat& a, const ncnn::Mat& b) const {
        ncnn::Extractor ex = net.create_extractor();
        ex.input(this->inputBlob0, a);
        ex.input(this->inputBlob1, b);
        ncnn::Mat out;
        if (ex.extract(this->outputBlob, out) != 0)
            throw std::runtime_error("lsfg-android (ncnn): inference failed");
        return out;
    }
};

NcnnBackend::NcnnBackend() : impl(std::make_unique<Impl>()) {}
NcnnBackend::~NcnnBackend() = default;

void NcnnBackend::load(const std::string& paramPath) {
    this->loaded = false;
    this->usingVulkan = false;

    if (this->impl->tryLoad(paramPath, true)) {
        this->loaded = true;
        this->usingVulkan = this->impl->net.opt.use_vulkan_compute;
        return;
    }
    if (this->impl->tryLoad(paramPath, false)) {
        this->loaded = true;
        return;
    }

    // fp16 → fp32 auto-fallback
    const std::string fp32Path = fp16PathToFp32(paramPath);
    if (!fp32Path.empty() && fp32Path != paramPath) {
        if (this->impl->tryLoad(fp32Path, true)) {
            this->loaded = true;
            this->usingVulkan = this->impl->net.opt.use_vulkan_compute;
            return;
        }
        if (this->impl->tryLoad(fp32Path, false)) {
            this->loaded = true;
            return;
        }
    }

    throw std::runtime_error(
        "lsfg-android (ncnn): unable to load model from " + paramPath);
}

namespace {
    ncnn::Mat toNcnnMat(const HostFrame& frame) {
        ncnn::Mat mat = ncnn::Mat::from_pixels(
            frame.rgba.data(), ncnn::Mat::PIXEL_RGBA2RGB,
            static_cast<int>(frame.width), static_cast<int>(frame.height));
        constexpr float norm[3] = {1.0F/255.0F, 1.0F/255.0F, 1.0F/255.0F};
        mat.substract_mean_normalize(nullptr, norm);
        return mat;
    }

    HostFrame fromNcnnMat(const ncnn::Mat& mat, uint32_t width, uint32_t height) {
        ncnn::Mat scaled = mat.clone();
        constexpr float scale[3] = {255.0F, 255.0F, 255.0F};
        scaled.substract_mean_normalize(nullptr, scale);

        HostFrame out;
        out.width = width;
        out.height = height;
        out.rgba.resize(static_cast<size_t>(width) * height * 4);
        scaled.to_pixels(out.rgba.data(), ncnn::Mat::PIXEL_RGB2RGBA);
        for (size_t i = 3; i < out.rgba.size(); i += 4)
            out.rgba[i] = 255;
        return out;
    }
}

std::vector<HostFrame> NcnnBackend::interpolate(
        const HostFrame& a, const HostFrame& b, uint32_t count) {
    if (!this->loaded)
        throw std::runtime_error(
            "lsfg-android (ncnn): interpolate() called before model loaded");
    if (count == 0) return {};
    if (a.width != b.width || a.height != b.height)
        throw std::runtime_error("lsfg-android (ncnn): frame size mismatch");

    // Recursive midpoint subdivision for evenly-spaced intermediate frames.
    struct Node { double t; ncnn::Mat img; };
    std::vector<Node> nodes;
    nodes.push_back({0.0, toNcnnMat(a)});
    nodes.push_back({1.0, toNcnnMat(b)});

    for (uint32_t i = 0; i < count; ++i) {
        size_t bestIdx = 0;
        double bestGap = -1.0;
        for (size_t j = 0; j + 1 < nodes.size(); ++j) {
            const double gap = nodes[j+1].t - nodes[j].t;
            if (gap > bestGap) { bestGap = gap; bestIdx = j; }
        }
        const double midT = (nodes[bestIdx].t + nodes[bestIdx+1].t) / 2.0;
        ncnn::Mat midImg = this->impl->predictMid(
            nodes[bestIdx].img, nodes[bestIdx+1].img);
        nodes.insert(nodes.begin() + static_cast<std::ptrdiff_t>(bestIdx) + 1,
                     {midT, std::move(midImg)});
    }

    std::vector<HostFrame> result;
    result.reserve(count);
    for (size_t i = 1; i + 1 < nodes.size(); ++i)
        result.push_back(fromNcnnMat(nodes[i].img, a.width, a.height));
    return result;
}

#endif // LSFGVK_ENABLE_NCNN

#include "rife_ncnn.hpp"
#include "rife_warp_layer.hpp"

#include <algorithm>
#include <vector>

namespace lsfg_rife {

namespace {
// IFNet's downsample/upsample chain needs both dimensions divisible by 32;
// odd capture resolutions get replicate-padded up to the next multiple and
// cropped back out of the output afterwards.
constexpr int kAlignment = 32;

// See the class doc above — flip to true only after on-device verification
// of the Vulkan Warp shader. Left as a single named constant (rather than
// scattering `false` through the file) specifically so that verification
// step is a one-line change. NOTE: flipping this also requires calling
// ncnn::create_gpu_instance() once at process startup (and
// ncnn::destroy_gpu_instance() at shutdown) before any RifeEngine is
// constructed — not done anywhere in this file yet, since it's a no-op
// while this stays false.
constexpr bool kUseVulkanCompute = false;

inline int alignUp(int value, int alignment) {
    return ((value + alignment - 1) / alignment) * alignment;
}
} // namespace

RifeEngine::RifeEngine() : m_net(std::make_unique<ncnn::Net>()) {}

RifeEngine::~RifeEngine() = default;

int RifeEngine::load(AAssetManager* assetManager,
                      const char* paramAssetPath,
                      const char* binAssetPath) {
    if (assetManager == nullptr) {
        return -1;
    }

    m_net->opt.use_vulkan_compute = kUseVulkanCompute;
    m_net->opt.num_threads = 4;

    m_net->register_custom_layer("rife.Warp", WarpLayer_layer_creator);

    if (m_net->load_param(assetManager, paramAssetPath) != 0) {
        return -2;
    }
    if (m_net->load_model(assetManager, binAssetPath) != 0) {
        return -3;
    }

    m_loaded = true;
    return 0;
}

int RifeEngine::interpolate(const uint8_t* rgba0, const uint8_t* rgba1,
                             int width, int height, float timestep,
                             uint8_t* outRgba) const {
    if (!m_loaded || rgba0 == nullptr || rgba1 == nullptr || outRgba == nullptr) {
        return -1;
    }
    if (width <= 0 || height <= 0) {
        return -2;
    }

    const int padW = alignUp(width, kAlignment);
    const int padH = alignUp(height, kAlignment);

    ncnn::Mat img0 = ncnn::Mat::from_pixels(rgba0, ncnn::Mat::PIXEL_RGBA2RGB, width, height);
    ncnn::Mat img1 = ncnn::Mat::from_pixels(rgba1, ncnn::Mat::PIXEL_RGBA2RGB, width, height);
    if (img0.empty() || img1.empty()) {
        return -3;
    }

    const float norm_vals[3] = {1.0f / 255.0f, 1.0f / 255.0f, 1.0f / 255.0f};
    img0.substract_mean_normalize(nullptr, norm_vals);
    img1.substract_mean_normalize(nullptr, norm_vals);

    if (padW != width || padH != height) {
        ncnn::Mat padded0, padded1;
        ncnn::copy_make_border(img0, padded0, 0, padH - height, 0, padW - width,
                                ncnn::BORDER_REPLICATE, 0.f);
        ncnn::copy_make_border(img1, padded1, 0, padH - height, 0, padW - width,
                                ncnn::BORDER_REPLICATE, 0.f);
        img0 = padded0;
        img1 = padded1;
    }

    // in2: single-channel timestep map broadcast across the padded frame,
    // matching the "Split ... in2" shape in flownet.param.
    ncnn::Mat timeMat(padW, padH, 1);
    timeMat.fill(timestep);

    ncnn::Extractor ex = m_net->create_extractor();
    ex.input("in0", img0);
    ex.input("in1", img1);
    ex.input("in2", timeMat);

    ncnn::Mat out;
    if (ex.extract("out0", out) != 0) {
        return -4;
    }
    if (out.w != padW || out.h != padH || out.c != 3) {
        return -5;
    }

    // out channels are ~[0,1]; scale back to 0-255 and pack into RGBA,
    // cropping the alignment padding back off.
    const float* rCh = out.channel(0);
    const float* gCh = out.channel(1);
    const float* bCh = out.channel(2);

    for (int y = 0; y < height; y++) {
        const int rowOff = y * padW;
        uint8_t* dstRow = outRgba + static_cast<size_t>(y) * width * 4;
        for (int x = 0; x < width; x++) {
            const int idx = rowOff + x;
            auto toByte = [](float v) -> uint8_t {
                v = std::min(std::max(v, 0.0f), 1.0f) * 255.0f;
                return static_cast<uint8_t>(v + 0.5f);
            };
            dstRow[x * 4 + 0] = toByte(rCh[idx]);
            dstRow[x * 4 + 1] = toByte(gCh[idx]);
            dstRow[x * 4 + 2] = toByte(bCh[idx]);
            dstRow[x * 4 + 3] = 255;
        }
    }

    return 0;
}

} // namespace lsfg_rife

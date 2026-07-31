#include "rife_warp_layer.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>

#if NCNN_VULKAN
#include "gpu.h"
#endif

namespace lsfg_rife {

WarpLayer::WarpLayer() {
    one_blob_only = false;
    support_inplace = false;
#if NCNN_VULKAN
    support_vulkan = true;
#endif
}

namespace {

inline float sample_channel(const ncnn::Mat& src, int channel, float fx, float fy) {
    const int w = src.w;
    const int h = src.h;

    // Clamp to border (RIFE trains with padding_mode="border").
    fx = std::min(std::max(fx, 0.0f), static_cast<float>(w - 1));
    fy = std::min(std::max(fy, 0.0f), static_cast<float>(h - 1));

    const int x0 = static_cast<int>(fx);
    const int y0 = static_cast<int>(fy);
    const int x1 = std::min(x0 + 1, w - 1);
    const int y1 = std::min(y0 + 1, h - 1);

    const float tx = fx - static_cast<float>(x0);
    const float ty = fy - static_cast<float>(y0);

    const float* row0 = src.channel(channel).row(y0);
    const float* row1 = src.channel(channel).row(y1);

    const float v00 = row0[x0];
    const float v01 = row0[x1];
    const float v10 = row1[x0];
    const float v11 = row1[x1];

    const float top = v00 + (v01 - v00) * tx;
    const float bot = v10 + (v11 - v10) * tx;
    return top + (bot - top) * ty;
}

} // namespace

int WarpLayer::forward(const std::vector<ncnn::Mat>& bottom_blobs,
                        std::vector<ncnn::Mat>& top_blobs,
                        const ncnn::Option& opt) const {
    const ncnn::Mat& src = bottom_blobs[0];
    const ncnn::Mat& flow = bottom_blobs[1];

    const int w = src.w;
    const int h = src.h;
    const int c = src.c;

    if (flow.w != w || flow.h != h || flow.c < 2) {
        // Shape mismatch between the feature map and its flow field —
        // treat as a hard failure rather than reading out of bounds.
        return -1;
    }

    ncnn::Mat& dst = top_blobs[0];
    dst.create(w, h, c, src.elemsize, opt.blob_allocator);
    if (dst.empty()) {
        return -100; // ncnn's conventional "allocation failed" code.
    }

    const ncnn::Mat flow_x = flow.channel(0);
    const ncnn::Mat flow_y = flow.channel(1);

    #pragma omp parallel for num_threads(opt.num_threads)
    for (int y = 0; y < h; y++) {
        const float* fx_row = flow_x.row(y);
        const float* fy_row = flow_y.row(y);

        for (int x = 0; x < w; x++) {
            const float sample_x = static_cast<float>(x) + fx_row[x];
            const float sample_y = static_cast<float>(y) + fy_row[x];

            for (int ch = 0; ch < c; ch++) {
                dst.channel(ch).row(y)[x] = sample_channel(src, ch, sample_x, sample_y);
            }
        }
    }

    return 0;
}

::ncnn::Layer* WarpLayer_layer_creator(void* /*userdata*/) {
    return new WarpLayer();
}

#if NCNN_VULKAN

namespace {

// Deliberately the simplest possible ncnn Vulkan layer shape: one
// invocation per output pixel, looping over channels inside the shader.
// No pack4/pack8 (SIMD-packed) storage variant — WarpLayer doesn't set
// support_packing / support_fp16_storage, so ncnn keeps its blobs at
// elempack=1, fp32 before calling this layer, which is exactly what the
// indexing below assumes. That leaves real perf on the table (a pack4
// variant would move 4x the data per shader invocation) but is much less
// likely to be wrong on the first try — a reasonable trade until this has
// actually been profiled on a device.
const char* kWarpCompSource = R"glsl(
#version 450
layout (local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout (binding = 0) readonly buffer bottom0_blob { float bottom0_data[]; };
layout (binding = 1) readonly buffer bottom1_blob { float bottom1_data[]; };
layout (binding = 2) writeonly buffer top_blob { float top_data[]; };

layout (push_constant) uniform parameter {
    int w;
    int h;
    int c;
    int cstep;      // channel stride of bottom0 / top (w*h at elempack1)
    int flow_cstep; // channel stride of the flow blob (bottom1)
} p;

void main() {
    int x = int(gl_GlobalInvocationID.x);
    int y = int(gl_GlobalInvocationID.y);
    if (x >= p.w || y >= p.h) return;

    int flow_idx = y * p.w + x;
    float fx = float(x) + bottom1_data[flow_idx];
    float fy = float(y) + bottom1_data[p.flow_cstep + flow_idx];

    fx = clamp(fx, 0.0, float(p.w - 1));
    fy = clamp(fy, 0.0, float(p.h - 1));

    int x0 = int(fx);
    int y0 = int(fy);
    int x1 = min(x0 + 1, p.w - 1);
    int y1 = min(y0 + 1, p.h - 1);
    float tx = fx - float(x0);
    float ty = fy - float(y0);

    for (int ch = 0; ch < p.c; ch++) {
        int base = ch * p.cstep;
        float v00 = bottom0_data[base + y0 * p.w + x0];
        float v01 = bottom0_data[base + y0 * p.w + x1];
        float v10 = bottom0_data[base + y1 * p.w + x0];
        float v11 = bottom0_data[base + y1 * p.w + x1];
        float top = v00 + (v01 - v00) * tx;
        float bot = v10 + (v11 - v10) * tx;
        top_data[base + y * p.w + x] = top + (bot - top) * ty;
    }
}
)glsl";

} // namespace

int WarpLayer::create_pipeline(const ncnn::Option& opt) {
    // Force fp32 / non-packed storage for this pipeline regardless of the
    // net's global options — the shader above indexes raw float[] buffers
    // and doesn't know about ncnn's fp16 or pack4/pack8 layouts.
    ncnn::Option shader_opt = opt;
    shader_opt.use_fp16_storage = false;
    shader_opt.use_fp16_packed = false;
    shader_opt.use_fp16_arithmetic = false;
    shader_opt.use_shader_pack8 = false;
    shader_opt.use_image_storage = false;

    std::vector<uint32_t> spirv;
    int ret = ncnn::compile_spirv_module(
        kWarpCompSource, static_cast<int>(strlen(kWarpCompSource)), shader_opt, spirv);
    if (ret != 0 || spirv.empty()) {
        return -1;
    }

    pipeline_warp = new ncnn::Pipeline(vkdev);
    pipeline_warp->set_optimal_local_size_xyz(8, 8, 1);

    std::vector<ncnn::vk_specialization_type> specializations;
    ret = pipeline_warp->create(spirv.data(), spirv.size() * sizeof(uint32_t), specializations);
    if (ret != 0) {
        delete pipeline_warp;
        pipeline_warp = nullptr;
        return -1;
    }

    return 0;
}

int WarpLayer::destroy_pipeline(const ncnn::Option& /*opt*/) {
    delete pipeline_warp;
    pipeline_warp = nullptr;
    return 0;
}

int WarpLayer::forward(const std::vector<ncnn::VkMat>& bottom_blobs,
                        std::vector<ncnn::VkMat>& top_blobs,
                        ncnn::VkCompute& cmd,
                        const ncnn::Option& opt) const {
    if (pipeline_warp == nullptr) {
        return -1;
    }

    const ncnn::VkMat& src = bottom_blobs[0];
    const ncnn::VkMat& flow = bottom_blobs[1];

    ncnn::VkMat& dst = top_blobs[0];
    dst.create(src.w, src.h, src.c, src.elemsize, src.elempack, opt.blob_vkallocator);
    if (dst.empty()) {
        return -100;
    }

    std::vector<ncnn::VkMat> bindings(3);
    bindings[0] = src;
    bindings[1] = flow;
    bindings[2] = dst;

    std::vector<ncnn::vk_constant_type> constants(5);
    constants[0].i = src.w;
    constants[1].i = src.h;
    constants[2].i = src.c;
    constants[3].i = static_cast<int>(src.cstep);
    constants[4].i = static_cast<int>(flow.cstep);

    cmd.record_pipeline(pipeline_warp, bindings, constants, dst);

    return 0;
}

#endif // NCNN_VULKAN

} // namespace lsfg_rife

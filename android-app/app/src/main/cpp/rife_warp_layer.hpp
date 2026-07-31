#pragma once

// Custom ncnn layer required by the RIFE v4.x IFNet graph in
// assets/models/rife/flownet.param (8x "rife.Warp" layer entries). ncnn
// does not ship this op — RIFE registers it itself upstream — so we
// reimplement it here and register it under the same "rife.Warp" type
// string before calling ncnn::Net::load_param(), otherwise loading fails
// with "layer rife.Warp not exists".
//
// Semantics (bilinear backward/inverse warp, PyTorch grid_sample
// equivalent with align_corners=true, padding_mode="border"):
//   bottom_blobs[0]: feature/image map, shape (w, h, c)
//   bottom_blobs[1]: optical flow, shape (w, h, 2) — channel 0 is the
//                    x-displacement, channel 1 the y-displacement, in
//                    pixel units (NOT normalized to [-1,1]).
//   top_blobs[0]:    output(x, y, ch) = bilinear_sample(
//                        bottom0, x + flow_x(x,y), y + flow_y(x,y), ch)
//                    sampling coordinates are clamped to the image
//                    border rather than zero-padded, matching RIFE's
//                    training-time warp.
//
// Only a CPU forward() was implemented initially. This header now also
// declares a Vulkan forward() (see rife_warp_layer.cpp) so the whole IFNet
// graph — not just the convolutions — can run on-GPU. The GLSL compute
// shader is compiled at pipeline-creation time via ncnn's
// compile_spirv_module() (runtime glslang compile, no build-time shader
// step needed), matching ncnn's documented "custom Vulkan layer" pattern.
//
// rife_ncnn.cpp still leaves net.opt.use_vulkan_compute = false by
// default — this shader has not been run on real hardware, only written
// against ncnn's public Pipeline/VkCompute/VkMat API from documentation
// and general Vulkan-compute knowledge. Treat the GPU path as
// "implemented, unverified" until someone flips that flag on a device and
// confirms both (a) it doesn't crash / reject the SPIR-V and (b) the
// output matches the CPU path for a known frame pair. See the toggle and
// comment in rife_ncnn.cpp.

#include "layer.h"
#if NCNN_VULKAN
#include "pipeline.h"
#endif

namespace lsfg_rife {

class WarpLayer : public ncnn::Layer {
public:
    WarpLayer();

    virtual int forward(const std::vector<ncnn::Mat>& bottom_blobs,
                         std::vector<ncnn::Mat>& top_blobs,
                         const ncnn::Option& opt) const override;

#if NCNN_VULKAN
    virtual int create_pipeline(const ncnn::Option& opt) override;
    virtual int destroy_pipeline(const ncnn::Option& opt) override;

    virtual int forward(const std::vector<ncnn::VkMat>& bottom_blobs,
                         std::vector<ncnn::VkMat>& top_blobs,
                         ncnn::VkCompute& cmd,
                         const ncnn::Option& opt) const override;

private:
    ncnn::Pipeline* pipeline_warp = nullptr;
#endif
};

// Creator function ncnn's layer registry expects: `ncnn::Layer* (*)(void*)`.
::ncnn::Layer* WarpLayer_layer_creator(void* userdata);

} // namespace lsfg_rife

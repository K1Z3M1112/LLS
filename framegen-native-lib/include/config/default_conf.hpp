#pragma once

#include <string>

const std::string DEFAULT_CONFIG = R"(version = 1
[global]
# override the location of Lossless Scaling
# dll = "/games/Lossless Scaling/Lossless.dll"

# use a custom-trained NCNN model (Vulkan mobile GPU) instead of the
# Lossless.dll pipeline. ncnn_model should point at the *.ncnn.param file,
# with the matching *.ncnn.bin sitting next to it (see the training
# notebook's NCNN export step -- both FP32 and FP16 exports are supported
# transparently, just point at whichever .param file you want to use).
# use_ncnn = true
# ncnn_model = "/path/to/frame_interpolator_fp16.ncnn.param"

# [[game]] # example entry
# exe = "Game.exe"
#
# multiplier = 3
# flow_scale = 0.7
# performance_mode = true
# hdr_mode = false
#
# experimental_present_mode = "fifo"

[[game]] # default vkcube entry
exe = "vkcube"

multiplier = 4
performance_mode = true

[[game]] # default benchmark entry
exe = "benchmark"

multiplier = 4
performance_mode = false

[[game]] # override Genshin Impact
exe = "Genshin"

multiplier = 3
)";

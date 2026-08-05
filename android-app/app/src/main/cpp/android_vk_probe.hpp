#pragma once

#include <string>

namespace lsfg_android {

constexpr int kProbeNoVulkan = -10;
constexpr int kProbeMissingSpirv = -11;
constexpr int kProbeDriverRejected = -12;

// Creates a headless Vulkan device and runs vkCreateShaderModule over every
// cached SPIR-V blob. Returns kOk iff all shaders are accepted.
int probe_shaders_on_device(const std::string &cacheDir);

// Reports whether the first physical Vulkan device on this system advertises
// VK_KHR_shader_float16_int8 with shaderFloat16=VK_TRUE. Used by the UI to
// grey out the "FP16 frame-gen shaders" toggle on hardware that can't run
// the OpCapability Float16 SPIR-V variants. Headless: creates a temporary
// VkInstance/VkDevice, checks the feature, and tears down. Returns false on
// any error (no Vulkan loader, no device, etc.) — the UI must treat false
// as "FP16 not available" rather than a failure to probe.
bool device_supports_float16();

// Reports whether the device advertises vulkanMemoryModel. Neither the FP16
// nor FP32 native SPIR-V shaders shipped in Lossless.dll 3.2.2+ require VMM
// (verified in _analysis/*.dis and _analysis/fp32/), so this is informational
// / used for the defensive FP16 rescue path. Used by the render
// loop to auto-force FP16 mode on Mali Bifrost/Valhall (G57/G68/G77) which
// reports VMM=false. Returns false on any probe error (treat as "VMM not
// available", same defensive default as device_supports_float16).
bool device_supports_vulkan_memory_model();

} // namespace lsfg_android

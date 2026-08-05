#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace lsfg_android {

// Error codes returned to Kotlin via JNI. Keep kOk == 0.
constexpr int kOk = 0;
constexpr int kErrDllUnreadable = -1;
constexpr int kErrMissingResource = -2;
constexpr int kErrTranslationFailed = -3;
constexpr int kErrWriteFailed = -4;

// Parses Lossless.dll at [dllPath] (3.2.2+), extracts every RCDATA resource
// that LSFG uses, and writes one file per resource into two caches:
//   - <cacheDir>/fp16/<resId>.spv     FP16 SPIR-V (304..351) verbatim from DLL
//   - <cacheDir>/fp32/<resId>.spv     FP32 SPIR-V (353..400) verbatim from DLL
// Both are native SPIR-V shipped directly in the DLL resources — there is no
// DXBC bytecode in 3.2.2+ and no translation step. Each cache is best-effort:
// if a blob is missing or has the wrong magic it's skipped. FP32 is the
// default because it has no VulkanMemoryModel dependency (see
// fp32_spirv_shaders_available below), so it works on devices that lack
// vulkanMemoryModel (Mali Bifrost/Valhall).
int extract_dll_to_spirv(const std::string &dllPath, const std::string &cacheDir);

// Source identifier for load_cached_spirv.
enum class ShaderCache {
    Fp16Spirv,  // <cacheDir>/fp16/<id>.spv    (precompiled FP16 SPIR-V)
    Fp32Spirv,  // <cacheDir>/fp32/<id>.spv    (precompiled FP32 SPIR-V)
};

// Reads a cached SPIR-V file back from disk. Returns an empty vector on
// missing/unreadable files — the caller decides whether that's a fatal error.
std::vector<uint8_t> load_cached_spirv(const std::string &cacheDir, uint32_t resId,
                                       ShaderCache source = ShaderCache::Fp32Spirv);

// Maps a framegen shader name (e.g. "p_mipmaps", "p_alpha[2]", "generate")
// to its base resource ID (255..302). This base ID is not itself cached
// (there's no DXBC path anymore) — it's only used as the anchor that the
// FP16/FP32 offsets below are computed from. Returns 0 if the name is
// unknown.
//
// Mirror of Extract::nameIdxTable in lsfg-vk-android/src/extract/extract.cpp.
uint32_t shader_name_to_resource_id(const std::string &name);

// Same lookup, but returning the SPIR-V FP16 resource ID (304..351). The FP16
// set is a parallel SPIR-V variant precompiled into Lossless.dll with the
// `OpCapability Float16` enabled and mixed FP16/FP32 ops. The mapping is a
// constant +49 offset over shader_name_to_resource_id(). Returns 0 if the
// name is unknown OR if the corresponding FP16 ID is not in the supported
// range (currently 304..351).
uint32_t shader_name_to_resource_id_fp16(const std::string &name);

// Returns true when the FP16 cache directory contains every shader in the
// 304..351 range. Used by the render loop to fall back to the FP32 SPIR-V
// path transparently when the user toggles FP16 on but the DLL extraction
// skipped the FP16 set.
bool fp16_shaders_available(const std::string &cacheDir);

// Same lookup as shader_name_to_resource_id but returning the FP32 SPIR-V
// resource ID (353..400). Constant +98 offset over the base id. Returns 0
// if the name is unknown OR the resulting id falls outside 353..400.
uint32_t shader_name_to_resource_id_fp32_spirv(const std::string &name);

// Returns true when the FP32 SPIR-V cache directory contains every shader in
// the 353..400 range. This is the default path: no VulkanMemoryModel
// dependency (the DLL's FP32 SPIR-V uses OpMemoryModel Logical GLSL450, no
// VMM capability), so it works on devices that lack vulkanMemoryModel
// (Mali Bifrost/Valhall) — verified across the entire range in
// _analysis/*.dis and _analysis/fp32/.
bool fp32_spirv_shaders_available(const std::string &cacheDir);

} // namespace lsfg_android

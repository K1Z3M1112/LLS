#include "extract/extract.hpp"
#include "config/config.hpp"

#include <pe-parse/parse.h>

#include <cstdlib>
#include <filesystem>
#include <algorithm>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

using namespace Extract;

namespace {
    // Base shader ids, as they were laid out (and still are, relatively) in the
    // legacy DXBC resource set of Lossless.dll. Lossless Scaling 3.2.2.0 embeds
    // the *same* shaders a second time, compiled to native SPIR-V, at a shifted
    // resource id. lsfg-vk upstream (2.0.0-dev, which added 3.2.2.0 support)
    // derives that shifted id from this base id with a small formula instead of
    // a second hardcoded table - see resolveResourceId() below.
    const std::unordered_map<std::string, uint32_t> baseIdTable = {{
        { "mipmaps", 255 },
        { "alpha[0]", 267 },
        { "alpha[1]", 268 },
        { "alpha[2]", 269 },
        { "alpha[3]", 270 },
        { "beta[0]", 275 },
        { "beta[1]", 276 },
        { "beta[2]", 277 },
        { "beta[3]", 278 },
        { "beta[4]", 279 },
        { "gamma[0]", 257 },
        { "gamma[1]", 259 },
        { "gamma[2]", 260 },
        { "gamma[3]", 261 },
        { "gamma[4]", 262 },
        { "delta[0]", 257 },
        { "delta[1]", 263 },
        { "delta[2]", 264 },
        { "delta[3]", 265 },
        { "delta[4]", 266 },
        { "delta[5]", 258 },
        { "delta[6]", 271 },
        { "delta[7]", 272 },
        { "delta[8]", 273 },
        { "delta[9]", 274 },
        { "generate", 256 },
    }};

    // offsets used by lsfg-vk 2.0.0-dev's shader_registry.cpp to compute the
    // SPIR-V resource id of a shader from its base id:
    //   id' = BASE_OFFSET + id + (perf ? OFFSET_PERF : 0) + (fp16 ? 0 : OFFSET_FP32)
    constexpr uint32_t BASE_OFFSET = 49;
    constexpr uint32_t OFFSET_PERF = 23;
    constexpr uint32_t OFFSET_FP32 = 49;

    // Resolve a shader name (e.g. "alpha[0]" or the performance-mode
    // "p_alpha[0]") to the SPIR-V resource id inside Lossless.dll 3.2.2.0+.
    //
    // We always request the fp32 (full precision) variant: picking fp16 would
    // require negotiating VK_KHR_shader_float16_int8 / Vulkan 1.2 shaderFloat16
    // with the hooked device first (this layer doesn't do that yet), so fp16 is
    // left as false here - same safe default upstream falls back to when the
    // device (or user) doesn't opt into low precision.
    uint32_t resolveResourceId(const std::string& name) {
        const bool perfRequested = name.rfind("p_", 0) == 0;
        const std::string baseName = perfRequested ? name.substr(2) : name;

        // mipmaps/generate are shared between the quality and performance
        // shader sets, so they never get the performance offset applied.
        const bool perf = perfRequested && baseName != "mipmaps" && baseName != "generate";

        auto it = baseIdTable.find(baseName);
        if (it == baseIdTable.end())
            throw std::runtime_error("Unknown shader name: " + name);

        return BASE_OFFSET + it->second + (perf ? OFFSET_PERF : 0) + OFFSET_FP32;
    }

    // full set of shader names this layer needs, used to validate that the
    // parsed DLL actually contains everything we expect.
    const std::vector<std::string> allShaderNames = {{
        "mipmaps", "generate",
        "alpha[0]", "alpha[1]", "alpha[2]", "alpha[3]",
        "beta[0]", "beta[1]", "beta[2]", "beta[3]", "beta[4]",
        "gamma[0]", "gamma[1]", "gamma[2]", "gamma[3]", "gamma[4]",
        "delta[0]", "delta[1]", "delta[2]", "delta[3]", "delta[4]",
        "delta[5]", "delta[6]", "delta[7]", "delta[8]", "delta[9]",
        "p_alpha[0]", "p_alpha[1]", "p_alpha[2]", "p_alpha[3]",
        "p_beta[0]", "p_beta[1]", "p_beta[2]", "p_beta[3]", "p_beta[4]",
        "p_gamma[0]", "p_gamma[1]", "p_gamma[2]", "p_gamma[3]", "p_gamma[4]",
        "p_delta[0]", "p_delta[1]", "p_delta[2]", "p_delta[3]", "p_delta[4]",
        "p_delta[5]", "p_delta[6]", "p_delta[7]", "p_delta[8]", "p_delta[9]",
    }};
}

namespace {
    auto& shaders() {
        static std::unordered_map<uint32_t, std::vector<uint8_t>> shaderData;
        return shaderData;
    }

    int on_resource(void*, const peparse::resource& res) {
        if (res.type != peparse::RT_RCDATA || res.buf == nullptr || res.buf->bufLen <= 0)
            return 0;
        std::vector<uint8_t> resource_data(res.buf->bufLen);
        std::copy_n(res.buf->buf, res.buf->bufLen, resource_data.data());
        shaders()[res.name] = resource_data;
        return 0;
    }

    const std::vector<std::filesystem::path> PATHS{{
        ".local/share/Steam/steamapps/common",
        ".steam/steam/steamapps/common",
        ".steam/debian-installation/steamapps/common",
        ".var/app/com.valvesoftware.Steam/.local/share/Steam/steamapps/common",
        "snap/steam/common/.local/share/Steam/steamapps/common"
    }};

    std::string getDllPath() {
        // overriden path
        std::string dllPath = Config::activeConf.dll;
        if (!dllPath.empty())
            return dllPath;
        // direct Unix path from the host (GameNative / Wine-on-Android resolves the
        // Wine-prefix path on the Java side and passes it in here).
        const char* directPath = getenv("LSFG_DLL_PATH_UNIX");
        if (directPath && *directPath != '\0' && std::filesystem::exists(directPath))
            return std::string(directPath);
        // Wine prefix: try Steam's default install locations inside drive_c.
        const char* winePrefix = getenv("WINEPREFIX");
        if (winePrefix && *winePrefix != '\0') {
            const std::vector<std::filesystem::path> WINE_PATHS{{
                "drive_c/Program Files (x86)/Steam/steamapps/common/Lossless Scaling/Lossless.dll",
                "drive_c/Program Files/Steam/steamapps/common/Lossless Scaling/Lossless.dll"
            }};
            for (const auto& rel : WINE_PATHS) {
                const std::filesystem::path path = std::filesystem::path(winePrefix) / rel;
                if (std::filesystem::exists(path))
                    return path.string();
            }
        }
        // home based paths
        const char* home = getenv("HOME");
        const std::string homeStr = home ? home : "";
        for (const auto& base : PATHS) {
            const std::filesystem::path path =
                std::filesystem::path(homeStr) / base / "Lossless Scaling" / "Lossless.dll";
            if (std::filesystem::exists(path))
                return path.string();
        }
        // xdg home
        const char* dataDir = getenv("XDG_DATA_HOME");
        if (dataDir && *dataDir != '\0')
            return std::string(dataDir) + "/Steam/steamapps/common/Lossless Scaling/Lossless.dll";
        // final fallback
        return "Lossless.dll";
    }
}

void Extract::extractShaders() {
    if (!shaders().empty())
        return;

    // parse the dll
    peparse::parsed_pe* dll = peparse::ParsePEFromFile(getDllPath().c_str());
    if (!dll)
        throw std::runtime_error("Unable to read Lossless.dll, is it installed?");
    peparse::IterRsrc(dll, on_resource, nullptr);
    peparse::DestructParsedPE(dll);

    // ensure all shaders are present
    for (const auto& name : allShaderNames) {
        const uint32_t idx = resolveResourceId(name);
        if (shaders().find(idx) == shaders().end())
            throw std::runtime_error("Shader not found: " + name + ".\n- Is Lossless Scaling up to date? (LLS requires Lossless Scaling 3.2.2.0 or newer)");
    }
}

std::vector<uint8_t> Extract::getShader(const std::string& name) {
    if (shaders().empty())
        throw std::runtime_error("Shaders are not loaded.");

    const uint32_t idx = resolveResourceId(name);

    auto sit = shaders().find(idx);
    if (sit == shaders().end())
        throw std::runtime_error("Shader not found: " + name);

    // resources at this offset are native SPIR-V (Lossless Scaling 3.2.2.0+),
    // not DXBC - no translation needed, unlike the legacy resource set.
    return sit->second;
}

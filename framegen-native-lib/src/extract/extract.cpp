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

// Resource ids point at the native FP32 SPIR-V variant (base DXBC id + 98),
// since Lossless.dll 3.2.2+ ships those directly and the DXBC->SPIR-V
// translator path has been removed. See Extract::translateShader.
namespace {
    constexpr uint32_t kFp32SpirvIdOffset = 98;
}

const std::unordered_map<std::string, uint32_t> nameIdxTable = {{
    { "mipmaps", 255 + kFp32SpirvIdOffset },
    { "alpha[0]", 267 + kFp32SpirvIdOffset },
    { "alpha[1]", 268 + kFp32SpirvIdOffset },
    { "alpha[2]", 269 + kFp32SpirvIdOffset },
    { "alpha[3]", 270 + kFp32SpirvIdOffset },
    { "beta[0]", 275 + kFp32SpirvIdOffset },
    { "beta[1]", 276 + kFp32SpirvIdOffset },
    { "beta[2]", 277 + kFp32SpirvIdOffset },
    { "beta[3]", 278 + kFp32SpirvIdOffset },
    { "beta[4]", 279 + kFp32SpirvIdOffset },
    { "gamma[0]", 257 + kFp32SpirvIdOffset },
    { "gamma[1]", 259 + kFp32SpirvIdOffset },
    { "gamma[2]", 260 + kFp32SpirvIdOffset },
    { "gamma[3]", 261 + kFp32SpirvIdOffset },
    { "gamma[4]", 262 + kFp32SpirvIdOffset },
    { "delta[0]", 257 + kFp32SpirvIdOffset },
    { "delta[1]", 263 + kFp32SpirvIdOffset },
    { "delta[2]", 264 + kFp32SpirvIdOffset },
    { "delta[3]", 265 + kFp32SpirvIdOffset },
    { "delta[4]", 266 + kFp32SpirvIdOffset },
    { "delta[5]", 258 + kFp32SpirvIdOffset },
    { "delta[6]", 271 + kFp32SpirvIdOffset },
    { "delta[7]", 272 + kFp32SpirvIdOffset },
    { "delta[8]", 273 + kFp32SpirvIdOffset },
    { "delta[9]", 274 + kFp32SpirvIdOffset },
    { "generate", 256 + kFp32SpirvIdOffset },
    { "p_mipmaps", 255 + kFp32SpirvIdOffset },
    { "p_alpha[0]", 290 + kFp32SpirvIdOffset },
    { "p_alpha[1]", 291 + kFp32SpirvIdOffset },
    { "p_alpha[2]", 292 + kFp32SpirvIdOffset },
    { "p_alpha[3]", 293 + kFp32SpirvIdOffset },
    { "p_beta[0]", 298 + kFp32SpirvIdOffset },
    { "p_beta[1]", 299 + kFp32SpirvIdOffset },
    { "p_beta[2]", 300 + kFp32SpirvIdOffset },
    { "p_beta[3]", 301 + kFp32SpirvIdOffset },
    { "p_beta[4]", 302 + kFp32SpirvIdOffset },
    { "p_gamma[0]", 280 + kFp32SpirvIdOffset },
    { "p_gamma[1]", 282 + kFp32SpirvIdOffset },
    { "p_gamma[2]", 283 + kFp32SpirvIdOffset },
    { "p_gamma[3]", 284 + kFp32SpirvIdOffset },
    { "p_gamma[4]", 285 + kFp32SpirvIdOffset },
    { "p_delta[0]", 280 + kFp32SpirvIdOffset },
    { "p_delta[1]", 286 + kFp32SpirvIdOffset },
    { "p_delta[2]", 287 + kFp32SpirvIdOffset },
    { "p_delta[3]", 288 + kFp32SpirvIdOffset },
    { "p_delta[4]", 289 + kFp32SpirvIdOffset },
    { "p_delta[5]", 281 + kFp32SpirvIdOffset },
    { "p_delta[6]", 294 + kFp32SpirvIdOffset },
    { "p_delta[7]", 295 + kFp32SpirvIdOffset },
    { "p_delta[8]", 296 + kFp32SpirvIdOffset },
    { "p_delta[9]", 297 + kFp32SpirvIdOffset },
    { "p_generate", 256 + kFp32SpirvIdOffset },
}};

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
    for (const auto& [name, idx] : nameIdxTable)
        if (shaders().find(idx) == shaders().end())
            throw std::runtime_error("Shader not found: " + name + ".\n- Is Lossless Scaling up to date?");
}

std::vector<uint8_t> Extract::getShader(const std::string& name) {
    if (shaders().empty())
        throw std::runtime_error("Shaders are not loaded.");

    auto hit = nameIdxTable.find(name);
    if (hit == nameIdxTable.end())
        throw std::runtime_error("Shader hash not found: " + name);

    auto sit = shaders().find(hit->second);
    if (sit == shaders().end())
        throw std::runtime_error("Shader not found: " + name);

    return sit->second;
}

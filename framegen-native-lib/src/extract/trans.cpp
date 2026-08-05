#include "extract/trans.hpp"

#include <cstdint>
#include <cstddef>
#include <algorithm>
#include <stdexcept>
#include <vector>

using namespace Extract;

namespace {
    /// SPIR-V binaries always start with this 4-byte magic number
    /// (0x07230203, little-endian in the byte stream).
    constexpr uint8_t SPIRV_MAGIC[4] = { 0x03, 0x02, 0x23, 0x07 };

    bool isSpirvAlready(const std::vector<uint8_t>& bytecode) {
        return bytecode.size() >= 4 &&
            std::equal(std::begin(SPIRV_MAGIC), std::end(SPIRV_MAGIC), bytecode.begin());
    }
}

std::vector<uint8_t> Extract::translateShader(std::vector<uint8_t> bytecode) {
    // Lossless.dll 3.2.2+ ships every framegen shader as native SPIR-V --
    // there is no DXBC bytecode to translate anymore, so this is a pure
    // passthrough with a sanity check on the magic number.
    if (!isSpirvAlready(bytecode))
        throw std::runtime_error(
            "Extract::translateShader: resource is not SPIR-V. "
            "This build only supports Lossless.dll 3.2.2+ native SPIR-V shaders "
            "(DXBC->SPIR-V translation was removed).");

    return bytecode;
}

#pragma once

#include <cstdint>
#include <vector>

namespace Extract {

    ///
    /// Validate that a shader resource is already native SPIR-V (Lossless.dll
    /// 3.2.2+) and return it unchanged. Throws if the resource is not SPIR-V.
    ///
    /// @param bytecode The SPIR-V bytecode extracted from Lossless.dll.
    /// @return The same bytecode, unmodified.
    ///
    std::vector<uint8_t> translateShader(std::vector<uint8_t> bytecode);

}

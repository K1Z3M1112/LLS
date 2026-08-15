#!/usr/bin/env python3
"""Regenerate gpu_postprocess_spv.hpp from gpu_postprocess.comp.

The app ships a precompiled SPIR-V blob (gpu_postprocess_spv.hpp) instead of
compiling the .comp shader at build time, so any edit to gpu_postprocess.comp
must be followed by re-running this script, or the app keeps running the old
shader binary.

Usage:
    1. Compile the shader to SPIR-V with glslc (from the Android NDK's
       shader-tools, or the Vulkan SDK):

           glslc -fshader-stage=compute --target-env=vulkan1.1 -O \
               gpu_postprocess.comp -o gpu_postprocess.spv

    2. Regenerate the header from that .spv:

           python3 regen_gpu_postprocess_spv.py gpu_postprocess.spv

    This overwrites gpu_postprocess_spv.hpp in place, in the same format
    the build already expects (kGpuPostprocessCompSpv / …SpvSize, namespace
    lsfg_android).
"""
import struct
import sys
from pathlib import Path

HEADER_TOP = """#pragma once

#include <cstdint>

namespace lsfg_android {

alignas(4) static constexpr uint32_t kGpuPostprocessCompSpv[] = {
"""

HEADER_BOTTOM = """};

static constexpr uint32_t kGpuPostprocessCompSpvSize = sizeof(kGpuPostprocessCompSpv);

} // namespace lsfg_android
"""


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 1

    spv_path = Path(sys.argv[1])
    data = spv_path.read_bytes()
    if len(data) % 4 != 0:
        print(f"error: {spv_path} is not a multiple of 4 bytes; not a valid SPIR-V module")
        return 1

    words = struct.unpack(f"<{len(data) // 4}I", data)
    if not words or words[0] != 0x07230203:
        print("error: missing SPIR-V magic number (0x07230203) - is this a real .spv file?")
        return 1

    lines = []
    for i in range(0, len(words), 8):
        chunk = words[i:i + 8]
        lines.append("    " + " ".join(f"0x{w:08x}u," for w in chunk))

    out_path = Path(__file__).with_name("gpu_postprocess_spv.hpp")
    out_path.write_text(HEADER_TOP + "\n".join(lines) + "\n" + HEADER_BOTTOM)
    print(f"wrote {out_path} ({len(words)} words / {len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

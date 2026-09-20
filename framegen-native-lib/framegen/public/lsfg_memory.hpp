#pragma once

#include "core/memorypool.hpp"

namespace LSFG {

    ///
    /// Choose how the frame-gen chain allocates its internal images. Call
    /// before LSFG_3_1::initialize() / LSFG_3_1P::initialize(); takes effect
    /// for contexts created afterwards. Both default to false (old behaviour).
    ///
    /// @param pooled Sub-allocate long-lived images from one shared block.
    /// @param aliasScratch Let per-stage scratch images share memory.
    ///
    __attribute__((visibility("default")))
    void setMemoryOptions(bool pooled, bool aliasScratch);

    /// Options currently set by setMemoryOptions().
    __attribute__((visibility("default")))
    Core::MemoryOptions getMemoryOptions();

    /// Sizes from the most recently created context (for logging / diagnostics).
    __attribute__((visibility("default")))
    Core::MemoryStats getMemoryStats();

}

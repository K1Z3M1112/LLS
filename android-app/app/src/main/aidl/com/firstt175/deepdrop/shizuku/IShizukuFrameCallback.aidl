package com.firstt175.deepdrop.shizuku;

import android.hardware.HardwareBuffer;

oneway interface IShizukuFrameCallback {
    // HardwareBuffer is passed as a native graphic-buffer handle. Binder transports
    // the handle/fence metadata, not the pixel payload. The receiver imports the
    // same AHardwareBuffer directly into Vulkan.
    void onFrame(in HardwareBuffer buffer, long timestampNs, long frameId);
    void onFrameMetrics(long timestampNs, long frameTimeNs, long pacingJitterNs);
    void onError(String message);
}

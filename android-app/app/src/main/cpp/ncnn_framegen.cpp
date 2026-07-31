// net.h/mat.h must come first: ncnn's bundled simplevk.h defines the same
// Vulkan handle types (VkResult, VkFormat, ...) as the real NDK
// <vulkan/vulkan_core.h>. Including ncnn's headers first lets simplevk.h
// define VK_DEFINE_NON_DISPATCHABLE_HANDLE, so ncnn_framegen.hpp's guard
// around its own vulkan_core.h include skips it instead of redefining
// everything.
#include <net.h>
#include <mat.h>

#include "ncnn_framegen.hpp"

#include <android/hardware_buffer.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <unordered_map>

#define LOG_TAG "lsfg-ncnn-fg"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Model I/O contract (verified against the shipped .param files, see
// android-app/app/src/main/assets/models/frame_interpolator_v2_fp{16,32}_ncnn.param):
//
//   input  "in0"  : 1x3x256x256, RGB, float, [0,1] range (no mean/std layer
//                   visible ahead of the first Convolution — the model's own
//                   `mul 2 / div 255 / sub 1` chain operates on an internal
//                   optical-flow tensor to build a GridSample warp grid, NOT
//                   on the input pixels, so callers must pre-scale 0..255
//                   uint8 -> 0..1 float themselves).
//   input  "in1"  : same shape/convention as in0, the second frame.
//   output "out0" : 1x3x256x256, RGB, float, clamped to [0,1] by the graph's
//                   final Clip layer.
//
// Crucially, the graph bakes a 256x256 GridSample coordinate grid in as a
// constant (`MemoryData` op reading a fixed 2x256x256 blob from the .bin) —
// it was exported/traced at a fixed spatial size and will silently produce
// garbage if fed a different resolution. There is no dynamic-shape path.
// So frames of arbitrary size are processed as a grid of 256x256 tiles with
// a feathered-overlap blend to hide seams (kTileOverlap below), NOT resized
// to 256x256 and back (which would throw away all detail).
// ---------------------------------------------------------------------------

namespace NcnnFG {

namespace {

constexpr int kTileSize = 256;
constexpr int kTileOverlap = 32;      // must be even; blended on both sides of a seam
constexpr int kTileStride = kTileSize - kTileOverlap;
constexpr const char *kInputBlob0 = "in0";
constexpr const char *kInputBlob1 = "in1";
constexpr const char *kOutputBlob = "out0";

struct Context {
    AHardwareBuffer *in0 = nullptr;
    AHardwareBuffer *in1 = nullptr;
    std::vector<AHardwareBuffer *> outs;
    uint32_t width = 0;
    uint32_t height = 0;
};

std::mutex g_mu;
ncnn::Net g_net;
bool g_loaded = false;
bool g_warnedNonPow2 = false;
std::atomic<int32_t> g_nextId{1};
std::unordered_map<int32_t, Context> g_contexts;

// AHB lock helper. Only AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM is supported
// (the format the capture path in this app always allocates — see
// ahb_image_bridge.cpp). Any other format is a programming error upstream,
// not a runtime condition to silently paper over, so we throw.
struct LockedAhb {
    AHardwareBuffer *ahb;
    void *data = nullptr;
    uint32_t stride = 0; // in pixels, per AHardwareBuffer_describe
    uint32_t width = 0;
    uint32_t height = 0;

    explicit LockedAhb(AHardwareBuffer *buf, bool forWrite) : ahb(buf) {
        AHardwareBuffer_Desc desc{};
        AHardwareBuffer_describe(ahb, &desc);
        if (desc.format != AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM) {
            throw std::runtime_error("NcnnFG: unsupported AHB format (expected RGBA_8888)");
        }
        width = desc.width;
        height = desc.height;
        const uint32_t usage = forWrite
            ? AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN
            : AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN;
        int rc = AHardwareBuffer_lock(ahb, usage, -1, nullptr, &data);
        if (rc != 0 || data == nullptr) {
            throw std::runtime_error("NcnnFG: AHardwareBuffer_lock failed");
        }
        stride = desc.stride; // pixels per row, may exceed width
    }
    ~LockedAhb() {
        AHardwareBuffer_unlock(ahb, nullptr);
    }
    LockedAhb(const LockedAhb &) = delete;
    LockedAhb &operator=(const LockedAhb &) = delete;
};

// Triangular feather weight for position `p` within [0, kTileSize) along one
// axis, given the tile does (or doesn't) have a neighbour on the low/high
// side to blend with. Ramps 0->1 across the first kTileOverlap pixels when a
// low-side neighbour exists, and 1->0 across the last kTileOverlap pixels
// when a high-side neighbour exists; 1.0 elsewhere (including the whole axis
// when the tile is at that edge of the image, so edge tiles aren't dimmed).
inline float featherWeight1D(int p, bool hasLowNeighbour, bool hasHighNeighbour) {
    float w = 1.0f;
    if (hasLowNeighbour && p < kTileOverlap) {
        w = std::min(w, (p + 0.5f) / static_cast<float>(kTileOverlap));
    }
    if (hasHighNeighbour && p >= kTileSize - kTileOverlap) {
        const int fromEnd = kTileSize - 1 - p;
        w = std::min(w, (fromEnd + 0.5f) / static_cast<float>(kTileOverlap));
    }
    return w;
}

// Accumulation canvas for feathered tile blending.
struct AccumCanvas {
    uint32_t width, height;
    std::vector<float> rgb;    // HWC, size w*h*3
    std::vector<float> weight; // HW

    AccumCanvas(uint32_t w, uint32_t h) : width(w), height(h),
        rgb(static_cast<size_t>(w) * h * 3, 0.0f),
        weight(static_cast<size_t>(w) * h, 0.0f) {}

    void addTile(const ncnn::Mat &tile, int tileX, int tileY,
                 bool lowX, bool highX, bool lowY, bool highY) {
        const float *rPlane = tile.channel(0);
        const float *gPlane = tile.channel(1);
        const float *bPlane = tile.channel(2);
        for (int y = 0; y < kTileSize; ++y) {
            const int dstY = tileY + y;
            if (dstY < 0 || dstY >= static_cast<int>(height)) continue;
            const float wy = featherWeight1D(y, lowY, highY);
            for (int x = 0; x < kTileSize; ++x) {
                const int dstX = tileX + x;
                if (dstX < 0 || dstX >= static_cast<int>(width)) continue;
                const float w = wy * featherWeight1D(x, lowX, highX);
                if (w <= 0.0f) continue;
                const size_t srcIdx = static_cast<size_t>(y) * kTileSize + x;
                const size_t dstIdx = (static_cast<size_t>(dstY) * width + dstX);
                rgb[dstIdx * 3 + 0] += rPlane[srcIdx] * w;
                rgb[dstIdx * 3 + 1] += gPlane[srcIdx] * w;
                rgb[dstIdx * 3 + 2] += bPlane[srcIdx] * w;
                weight[dstIdx] += w;
            }
        }
    }

    // Writes the blended result into an AHB (RGBA8888, alpha forced to 255).
    void writeTo(LockedAhb &out) const {
        for (uint32_t y = 0; y < height; ++y) {
            auto *row = static_cast<uint8_t *>(out.data) + static_cast<size_t>(y) * out.stride * 4;
            for (uint32_t x = 0; x < width; ++x) {
                const size_t idx = static_cast<size_t>(y) * width + x;
                const float w = weight[idx] > 1e-6f ? weight[idx] : 1.0f;
                uint8_t *px = row + static_cast<size_t>(x) * 4;
                px[0] = static_cast<uint8_t>(std::clamp(rgb[idx * 3 + 0] / w, 0.0f, 1.0f) * 255.0f + 0.5f);
                px[1] = static_cast<uint8_t>(std::clamp(rgb[idx * 3 + 1] / w, 0.0f, 1.0f) * 255.0f + 0.5f);
                px[2] = static_cast<uint8_t>(std::clamp(rgb[idx * 3 + 2] / w, 0.0f, 1.0f) * 255.0f + 0.5f);
                px[3] = 255;
            }
        }
    }
};

// Runs the model on one already-extracted tile pair, returns the 256x256x3
// output tile (still float, [0,1]).
ncnn::Mat runTile(const ncnn::Mat &tile0, const ncnn::Mat &tile1) {
    ncnn::Extractor ex = g_net.create_extractor();
    ex.input(kInputBlob0, tile0);
    ex.input(kInputBlob1, tile1);
    ncnn::Mat out;
    if (ex.extract(kOutputBlob, out) != 0) {
        throw std::runtime_error("NcnnFG: ncnn extract() failed");
    }
    return out;
}

// Materializes a midpoint frame into a temporary RGBA8 buffer so it can be
// fed back in as an "img0"/"img1" for the next level of recursion (we only
// have a model for the exact midpoint, so N>1 outputs come from recursive
// bisection — see interpFramesRecursive).
struct TempFrame {
    uint32_t width, height;
    std::vector<uint8_t> rgba; // width*height*4, tightly packed (stride==width)

    explicit TempFrame(const AccumCanvas &canvas)
        : width(canvas.width), height(canvas.height),
          rgba(static_cast<size_t>(canvas.width) * canvas.height * 4) {
        for (uint32_t y = 0; y < height; ++y) {
            for (uint32_t x = 0; x < width; ++x) {
                const size_t idx = static_cast<size_t>(y) * width + x;
                const float w = canvas.weight[idx] > 1e-6f ? canvas.weight[idx] : 1.0f;
                uint8_t *px = &rgba[idx * 4];
                px[0] = static_cast<uint8_t>(std::clamp(canvas.rgb[idx * 3 + 0] / w, 0.0f, 1.0f) * 255.0f + 0.5f);
                px[1] = static_cast<uint8_t>(std::clamp(canvas.rgb[idx * 3 + 1] / w, 0.0f, 1.0f) * 255.0f + 0.5f);
                px[2] = static_cast<uint8_t>(std::clamp(canvas.rgb[idx * 3 + 2] / w, 0.0f, 1.0f) * 255.0f + 0.5f);
                px[3] = 255;
            }
        }
    }
};

// A read-only view over either a real LockedAhb or a TempFrame, so the tile
// extraction code above (which takes a LockedAhb) can also run against a
// synthesized in-memory frame during recursion. AHardwareBuffer_lock can't
// wrap arbitrary memory, so instead of trying to unify types we duplicate
// the tiny bit of pixel-read logic needed here.
struct RawFrameView {
    const uint8_t *data;
    uint32_t stride; // pixels
    uint32_t width, height;

    static RawFrameView fromLocked(const LockedAhb &l) {
        return RawFrameView{static_cast<const uint8_t *>(l.data), l.stride, l.width, l.height};
    }
    static RawFrameView fromTemp(const TempFrame &t) {
        return RawFrameView{t.rgba.data(), t.width, t.width, t.height};
    }
};

inline void readPixelClampedView(const RawFrameView &img, int x, int y,
                                  float &r, float &g, float &b) {
    x = std::clamp(x, 0, static_cast<int>(img.width) - 1);
    y = std::clamp(y, 0, static_cast<int>(img.height) - 1);
    const uint8_t *row = img.data + static_cast<size_t>(y) * img.stride * 4;
    const uint8_t *px = row + static_cast<size_t>(x) * 4;
    r = px[0] / 255.0f;
    g = px[1] / 255.0f;
    b = px[2] / 255.0f;
}

ncnn::Mat extractTileView(const RawFrameView &img, int tileX, int tileY) {
    ncnn::Mat tile(kTileSize, kTileSize, 3);
    float *rPlane = tile.channel(0);
    float *gPlane = tile.channel(1);
    float *bPlane = tile.channel(2);
    for (int y = 0; y < kTileSize; ++y) {
        for (int x = 0; x < kTileSize; ++x) {
            float r, g, b;
            readPixelClampedView(img, tileX + x, tileY + y, r, g, b);
            rPlane[y * kTileSize + x] = r;
            gPlane[y * kTileSize + x] = g;
            bPlane[y * kTileSize + x] = b;
        }
    }
    return tile;
}

std::unique_ptr<AccumCanvas> interpolateMidpointView(const RawFrameView &a, const RawFrameView &b) {
    auto canvas = std::make_unique<AccumCanvas>(a.width, a.height);
    const int w = static_cast<int>(a.width);
    const int h = static_cast<int>(a.height);
    auto processTile = [&](int tileX, int tileY, bool lowX, bool highX, bool lowY, bool highY) {
        ncnn::Mat t0 = extractTileView(a, tileX, tileY);
        ncnn::Mat t1 = extractTileView(b, tileX, tileY);
        ncnn::Mat out = runTile(t0, t1);
        canvas->addTile(out, tileX, tileY, lowX, highX, lowY, highY);
    };
    for (int tileY = 0; tileY < h; tileY += kTileStride) {
        const bool lowY = tileY > 0, highY = tileY + kTileSize < h;
        for (int tileX = 0; tileX < w; tileX += kTileStride) {
            processTile(tileX, tileY, tileX > 0, tileX + kTileSize < w, lowY, highY);
        }
        if (w > kTileSize && (w - kTileSize) % kTileStride != 0) {
            processTile(w - kTileSize, tileY, true, false, lowY, highY);
        }
    }
    if (h > kTileSize && (h - kTileSize) % kTileStride != 0) {
        const int tileY = h - kTileSize;
        for (int tileX = 0; tileX < w; tileX += kTileStride) {
            processTile(tileX, tileY, tileX > 0, tileX + kTileSize < w, true, false);
        }
        if (w > kTileSize && (w - kTileSize) % kTileStride != 0) {
            processTile(w - kTileSize, tileY, true, false, true, false);
        }
    }
    return canvas;
}

// Recursively fills `outFrames` with `count` evenly-spaced interpolated
// frames between `a` and `b` (exclusive of both endpoints), using only the
// exact-midpoint model. Standard bisection trick for midpoint-only
// interpolation models when more than one in-between frame is needed:
// interpolate the center, then recurse on each half using that center as
// the new endpoint. `outFrames` is filled in left-to-right (time) order.
// Produces exactly evenly-spaced frames when count+1 is a power of two; for
// other counts the spacing is a close (not perfectly uniform) approximation
// — a one-time warning for this is logged from initialize().
//
// `pool` owns every synthesized midpoint frame (as TempFrame, heap
// allocated so pointers into it stay valid regardless of vector growth);
// `outFrames` collects raw pointers into `pool` in output order.
void interpFramesRecursive(const RawFrameView &a, const RawFrameView &b,
                            int count,
                            std::vector<std::unique_ptr<TempFrame>> &pool,
                            std::vector<TempFrame *> &outFrames) {
    if (count <= 0) return;
    auto canvas = interpolateMidpointView(a, b);
    pool.push_back(std::make_unique<TempFrame>(*canvas));
    TempFrame *mid = pool.back().get();
    const RawFrameView midView = RawFrameView::fromTemp(*mid);

    const int left = count / 2;
    const int right = count - left - 1;
    if (left > 0) interpFramesRecursive(a, midView, left, pool, outFrames);
    outFrames.push_back(mid);
    if (right > 0) interpFramesRecursive(midView, b, right, pool, outFrames);
}

} // namespace

void initialize(bool isHdr, float flowScale, uint64_t generationCount,
                 const std::string &paramPath, const std::string &binPath,
                 bool useVulkanCompute) {
    (void)flowScale; // no runtime knob in this exported graph, see header
    std::lock_guard<std::mutex> lock(g_mu);
    if (isHdr) {
        LOGW("NcnnFG::initialize: HDR requested but not implemented for the ncnn engine; "
             "proceeding in SDR (this only affects tone, not crashes).");
    }
    if (generationCount > 0 && (generationCount & (generationCount + 1)) != 0 && !g_warnedNonPow2) {
        // generationCount+1 not a power of two
        LOGW("NcnnFG::initialize: generationCount=%llu (multiplier=%llu) is not of the form "
             "2^k-1; frame spacing from recursive-midpoint bisection will be a close "
             "approximation rather than perfectly uniform.",
             static_cast<unsigned long long>(generationCount),
             static_cast<unsigned long long>(generationCount + 1));
        g_warnedNonPow2 = true;
    }

    g_net.opt.use_vulkan_compute = useVulkanCompute;
    g_net.opt.num_threads = 4;

    if (g_net.load_param(paramPath.c_str()) != 0) {
        throw std::runtime_error("NcnnFG: failed to load param file: " + paramPath);
    }
    if (g_net.load_model(binPath.c_str()) != 0) {
        throw std::runtime_error("NcnnFG: failed to load bin file: " + binPath);
    }
    g_loaded = true;
    LOGI("NcnnFG initialized (param=%s, vulkan=%d)", paramPath.c_str(), (int)useVulkanCompute);
}

int32_t createContextFromAHB(
        AHardwareBuffer *in0, AHardwareBuffer *in1,
        const std::vector<AHardwareBuffer *> &outN,
        VkExtent2D extent, VkFormat /*format*/) {
    std::lock_guard<std::mutex> lock(g_mu);
    if (!g_loaded) {
        throw std::runtime_error("NcnnFG::createContextFromAHB called before initialize()");
    }
    const int32_t id = g_nextId.fetch_add(1);
    Context ctx;
    ctx.in0 = in0;
    ctx.in1 = in1;
    ctx.outs = outN;
    ctx.width = extent.width;
    ctx.height = extent.height;
    g_contexts.emplace(id, std::move(ctx));
    return id;
}

void presentContext(int32_t id, int /*inSem*/, const std::vector<int> &/*outSem*/) {
    Context ctx;
    {
        std::lock_guard<std::mutex> lock(g_mu);
        auto it = g_contexts.find(id);
        if (it == g_contexts.end()) {
            throw std::runtime_error("NcnnFG::presentContext: unknown context id");
        }
        ctx = it->second; // copy: AHB pointers only, cheap
    }

    LockedAhb img0(ctx.in0, /*forWrite*/false);
    LockedAhb img1(ctx.in1, /*forWrite*/false);
    RawFrameView a = RawFrameView::fromLocked(img0);
    RawFrameView b = RawFrameView::fromLocked(img1);

    const size_t n = ctx.outs.size();
    if (n == 0) return;

    if (n == 1) {
        // Single output: exact midpoint, no recursion needed.
        auto canvas = interpolateMidpointView(a, b);
        LockedAhb out(ctx.outs[0], /*forWrite*/true);
        canvas->writeTo(out);
        return;
    }

    // n > 1: recursive bisection produces n evenly-spaced (or close to it,
    // see the non-power-of-two note in initialize()) intermediate frames.
    std::vector<std::unique_ptr<TempFrame>> pool;
    std::vector<TempFrame *> ordered;
    interpFramesRecursive(a, b, static_cast<int>(n), pool, ordered);
    const size_t count = std::min(n, ordered.size());
    for (size_t i = 0; i < count; ++i) {
        LockedAhb out(ctx.outs[i], /*forWrite*/true);
        const RawFrameView view = RawFrameView::fromTemp(*ordered[i]);
        AccumCanvas canvas(view.width, view.height);
        for (uint32_t y = 0; y < canvas.height; ++y) {
            for (uint32_t x = 0; x < canvas.width; ++x) {
                float r, g, bch;
                readPixelClampedView(view, x, y, r, g, bch);
                const size_t idx = static_cast<size_t>(y) * canvas.width + x;
                canvas.rgb[idx * 3 + 0] = r;
                canvas.rgb[idx * 3 + 1] = g;
                canvas.rgb[idx * 3 + 2] = bch;
                canvas.weight[idx] = 1.0f;
            }
        }
        canvas.writeTo(out);
    }
}

void waitIdle() {
    // presentContext() is synchronous; nothing to wait on.
}

void deleteContext(int32_t id) {
    std::lock_guard<std::mutex> lock(g_mu);
    g_contexts.erase(id);
}

void finalize() {
    std::lock_guard<std::mutex> lock(g_mu);
    g_contexts.clear();
    if (g_loaded) {
        g_net.clear();
        g_loaded = false;
    }
}

} // namespace NcnnFG

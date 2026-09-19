#pragma once
#include <string>
#include <vector>

namespace lsfg_android::vkbasalt {

struct Config {
    bool enabled = false;
    std::vector<std::string> effects;
    std::string reshadeTexturePath;
    std::string reshadeIncludePath;
    bool depthCapture = false;
    bool enableOnLaunch = true;
    float casSharpness = 0.4f;
    float dlsSharpness = 0.5f;
    float dlsDenoise = 0.17f;
    float fxaaQualitySubpix = 0.75f;
    float fxaaQualityEdgeThreshold = 0.125f;
    float fxaaQualityEdgeThresholdMin = 0.0312f;
    float smaaThreshold = 0.05f;
    int smaaMaxSearchSteps = 32;
    int smaaMaxSearchStepsDiag = 16;
    int smaaCornerRounding = 25;
    std::string lutFile;
};

bool writeConfig(const std::string& path, const Config& config);
bool validateFxPath(const std::string& path);
std::vector<std::string> scanFxDirectory(const std::string& directory);

} // namespace lsfg_android::vkbasalt

// PulseFX dedicated distortion AudioEffect.
//
// This library implements the Android audio_effect.h ABI. It must be built and
// registered by the device/ROM audio-effects configuration before the Kotlin
// controller can select it by UUID. See native/README.md.

#include <hardware/audio_effect.h>
#include <system/audio.h>

#include <algorithm>
#include <cmath>
#include <cerrno>
#include <cstdint>
#include <cstring>

namespace {

constexpr effect_uuid_t kEffectUuid = {
    0x7e4e3b7a, 0x1e20, 0x4f74, 0x8f4e,
    {0x9e, 0x9d, 0x5f, 0x3e, 0x9c, 0x01}
};

constexpr uint32_t kParamDriveDb = 1;
constexpr uint32_t kParamMixPercent = 2;
constexpr uint32_t kParamToneHz = 3;
constexpr uint32_t kParamOutputDb = 4;
constexpr uint32_t kParamBiasPercent = 5;
constexpr uint32_t kParamMode = 6;
constexpr uint32_t kParamDynamicsPercent = 7;
constexpr uint32_t kParamSpreadPercent = 8;

constexpr float kDefaultSampleRate = 48000.0f;
constexpr float kPi = 3.14159265358979323846f;
constexpr size_t kDryDelaySize = 7;
constexpr size_t kHalfBandTaps = 15;

static float waveshape(float input, int mode);

struct HalfBand2x {
    static constexpr float kDecimation[kHalfBandTaps] = {
        -3.0f / 632.0f, 0.0f, 15.0f / 632.0f, 0.0f, -48.0f / 632.0f, 0.0f,
        194.0f / 632.0f, 316.0f / 632.0f, 194.0f / 632.0f, 0.0f, -48.0f / 632.0f,
        0.0f, 15.0f / 632.0f, 0.0f, -3.0f / 632.0f
    };

    float inputHistory[kHalfBandTaps] = {};
    float outputHistory[kHalfBandTaps] = {};
    size_t inputPosition = 0;
    size_t outputPosition = 0;

    static float push(
        float* history,
        float sample,
        size_t& position,
        bool interpolation
    ) {
        history[position] = sample;
        float sum = 0.0f;
        size_t index = position;
        for (size_t tap = 0; tap < kHalfBandTaps; ++tap) {
            const float coefficient = interpolation ? kDecimation[tap] * 2.0f : kDecimation[tap];
            sum += coefficient * history[index];
            index = index == 0 ? kHalfBandTaps - 1 : index - 1;
        }
        position = (position + 1) % kHalfBandTaps;
        return sum;
    }

    float process(float input, int mode) {
        const float highRate0 = push(inputHistory, input, inputPosition, true);
        const float highRate1 = push(inputHistory, 0.0f, inputPosition, true);
        const float shaped0 = waveshape(highRate0, mode);
        const float shaped1 = waveshape(highRate1, mode);
        const float lowRate0 = push(outputHistory, shaped0, outputPosition, false);
        push(outputHistory, shaped1, outputPosition, false);
        return lowRate0;
    }

    void reset() {
        std::fill(inputHistory, inputHistory + kHalfBandTaps, 0.0f);
        std::fill(outputHistory, outputHistory + kHalfBandTaps, 0.0f);
        inputPosition = 0;
        outputPosition = 0;
    }
};

struct PulseFxEffect {
    const effect_interface_s* itfe;

    bool enabled = false;
    int driveDb = 0;
    int mixPercent = 100;
    int toneHz = 12000;
    int outputDb = 0;
    int biasPercent = 0;
    int mode = 0;
    int dynamicsPercent = 0;
    int spreadPercent = 0;

    float sampleRate = kDefaultSampleRate;
    float envelopeL = 0.0f;
    float envelopeR = 0.0f;
    float dcX1L = 0.0f;
    float dcX1R = 0.0f;
    float dcY1L = 0.0f;
    float dcY1R = 0.0f;
    float toneStateL = 0.0f;
    float toneStateR = 0.0f;
    float dryL[kDryDelaySize] = {};
    float dryR[kDryDelaySize] = {};
    size_t dryPosition = 0;
    HalfBand2x oversamplerL;
    HalfBand2x oversamplerR;
};

static PulseFxEffect* context(effect_handle_t handle) {
    return reinterpret_cast<PulseFxEffect*>(handle);
}

static float clamp(float value, float low, float high) {
    return std::max(low, std::min(high, value));
}

static float envelope(float input, float previous, float sampleRate) {
    const float magnitude = std::fabs(input);
    const float coefficient = magnitude > previous
        ? std::exp(-1.0f / (0.005f * sampleRate))
        : std::exp(-1.0f / (0.050f * sampleRate));
    return coefficient * previous + (1.0f - coefficient) * magnitude;
}

static float waveshape(float input, int mode) {
    const int selectedMode = std::max(0, std::min(5, mode));
    switch (selectedMode) {
        case 0: { // Soft clip
            const float absolute = std::fabs(input);
            if (absolute > 1.5f) return input > 0.0f ? 1.0f : -1.0f;
            if (absolute > 1.0f) {
                const float t = (3.0f - (2.0f - absolute) * (2.0f - absolute)) / 3.0f;
                return input > 0.0f ? t : -t;
            }
            return input - (input * input * input) / 3.0f;
        }
        case 1: // Hard clip
            return clamp(input, -1.0f, 1.0f);
        case 2: // Tanh saturation
            return std::tanh(input);
        case 3: // Foldback
            return std::sin(input * kPi);
        case 4: // Rectify
            return clamp(std::fabs(input), 0.0f, 1.0f);
        case 5: // Asymmetric
        default:
            return input >= 0.0f ? 1.0f - std::exp(-input) : -std::tanh(-input * 1.5f);
    }
}

static float dcBlock(float sample, float previousX, float previousY) {
    return sample - previousX + 0.995f * previousY;
}

static float tone(float sample, float cutoffHz, float previous, float sampleRate) {
    const float cutoff = clamp(cutoffHz, 200.0f, std::max(200.0f, sampleRate * 0.45f));
    const float alpha = clamp(
        1.0f - std::exp(-2.0f * kPi * cutoff / sampleRate),
        0.001f,
        1.0f
    );
    return previous + alpha * (sample - previous);
}

static void reset(PulseFxEffect* effect) {
    effect->envelopeL = 0.0f;
    effect->envelopeR = 0.0f;
    effect->dcX1L = 0.0f;
    effect->dcX1R = 0.0f;
    effect->dcY1L = 0.0f;
    effect->dcY1R = 0.0f;
    effect->toneStateL = 0.0f;
    effect->toneStateR = 0.0f;
    std::fill(effect->dryL, effect->dryL + kDryDelaySize, 0.0f);
    std::fill(effect->dryR, effect->dryR + kDryDelaySize, 0.0f);
    effect->dryPosition = 0;
    effect->oversamplerL.reset();
    effect->oversamplerR.reset();
}

static int32_t process(effect_handle_t handle, audio_buffer_t* input, audio_buffer_t* output) {
    if (input == nullptr || output == nullptr || input->raw == nullptr || output->raw == nullptr) {
        return -EINVAL;
    }

    auto* effect = context(handle);
    const size_t frames = std::min(input->frameCount, output->frameCount);
    const auto* inputSamples = static_cast<const float*>(input->raw);
    auto* outputSamples = static_cast<float*>(output->raw);

    // AudioFlinger supplies the configured effect chain as interleaved PCM
    // float for this insert effect. The ROM integration must select the float
    // chain; rejecting unknown formats is preferable to corrupting PCM16 data.
    if (!effect->enabled || effect->mixPercent <= 0 || effect->driveDb <= 0) {
        if (inputSamples != outputSamples) {
            std::memcpy(outputSamples, inputSamples, frames * 2 * sizeof(float));
        }
        return 0;
    }

    const float wetAmount = clamp(static_cast<float>(effect->mixPercent), 0.0f, 100.0f) / 100.0f;
    const float baseDrive = std::pow(10.0f, clamp(static_cast<float>(effect->driveDb), 0.0f, 48.0f) / 20.0f);
    const float dynamics = clamp(static_cast<float>(effect->dynamicsPercent), 0.0f, 100.0f) / 100.0f;
    const float spread = clamp(static_cast<float>(effect->spreadPercent), 0.0f, 100.0f) / 100.0f * 0.5f;
    const float bias = clamp(static_cast<float>(effect->biasPercent), -100.0f, 100.0f) / 100.0f * 0.12f;
    const float outputGain = std::pow(10.0f, clamp(static_cast<float>(effect->outputDb), -12.0f, 6.0f) / 20.0f);

    for (size_t frame = 0; frame < frames; ++frame) {
        const float left = inputSamples[frame * 2];
        const float right = inputSamples[frame * 2 + 1];
        const float dryLeft = effect->dryL[effect->dryPosition];
        const float dryRight = effect->dryR[effect->dryPosition];
        effect->dryL[effect->dryPosition] = left;
        effect->dryR[effect->dryPosition] = right;
        effect->dryPosition = (effect->dryPosition + 1) % kDryDelaySize;

        effect->envelopeL = envelope(left, effect->envelopeL, effect->sampleRate);
        effect->envelopeR = envelope(right, effect->envelopeR, effect->sampleRate);

        const float driveLeft = baseDrive * (1.0f + dynamics * effect->envelopeL * 4.0f) * (1.0f + spread);
        const float driveRight = baseDrive * (1.0f + dynamics * effect->envelopeR * 4.0f) * (1.0f - spread);

        const float wetLeft = effect->oversamplerL.process(left * driveLeft + bias, effect->mode);
        const float wetRight = effect->oversamplerR.process(right * driveRight + bias, effect->mode);
        const float blockedLeft = dcBlock(wetLeft, effect->dcX1L, effect->dcY1L);
        const float blockedRight = dcBlock(wetRight, effect->dcX1R, effect->dcY1R);
        effect->dcX1L = wetLeft;
        effect->dcX1R = wetRight;
        effect->dcY1L = blockedLeft;
        effect->dcY1R = blockedRight;
        effect->toneStateL = tone(blockedLeft, static_cast<float>(effect->toneHz), effect->toneStateL, effect->sampleRate);
        effect->toneStateR = tone(blockedRight, static_cast<float>(effect->toneHz), effect->toneStateR, effect->sampleRate);

        outputSamples[frame * 2] = clamp(
            (dryLeft * (1.0f - wetAmount) + effect->toneStateL * wetAmount) * outputGain,
            -1.5f,
            1.5f
        );
        outputSamples[frame * 2 + 1] = clamp(
            (dryRight * (1.0f - wetAmount) + effect->toneStateR * wetAmount) * outputGain,
            -1.5f,
            1.5f
        );
    }

    return 0;
}

static int32_t setParameter(PulseFxEffect* effect, void* data, uint32_t dataSize) {
    auto* parameter = static_cast<effect_param_t*>(data);
    if (parameter == nullptr || parameter->psize < sizeof(uint32_t) || parameter->vsize < sizeof(int32_t)) {
        return -EINVAL;
    }

    const auto* parameterBytes = parameter->data;
    const auto valueOffset = (parameter->psize + 3u) & ~3u;
    if (sizeof(effect_param_t) + valueOffset + parameter->vsize > dataSize) {
        return -EINVAL;
    }

    uint32_t id = 0;
    int32_t value = 0;
    std::memcpy(&id, parameterBytes, sizeof(id));
    std::memcpy(&value, parameterBytes + valueOffset, sizeof(value));

    switch (id) {
        case kParamDriveDb: effect->driveDb = value; break;
        case kParamMixPercent: effect->mixPercent = value; break;
        case kParamToneHz: effect->toneHz = value; break;
        case kParamOutputDb: effect->outputDb = value; break;
        case kParamBiasPercent: effect->biasPercent = value; break;
        case kParamMode: effect->mode = value; break;
        case kParamDynamicsPercent: effect->dynamicsPercent = value; break;
        case kParamSpreadPercent: effect->spreadPercent = value; break;
        default: return -EINVAL;
    }
    return 0;
}

static int32_t command(
    effect_handle_t handle,
    uint32_t commandCode,
    uint32_t commandSize,
    void* commandData,
    uint32_t* replySize,
    void* replyData
) {
    auto* effect = context(handle);
    switch (commandCode) {
        case EFFECT_CMD_INIT:
            reset(effect);
            return 0;
        case EFFECT_CMD_RESET:
            reset(effect);
            return 0;
        case EFFECT_CMD_ENABLE:
            effect->enabled = true;
            return 0;
        case EFFECT_CMD_DISABLE:
            effect->enabled = false;
            return 0;
        case EFFECT_CMD_SET_CONFIG: {
            // Preserve the chain's sample rate for the envelope and tone
            // coefficients. The device integration must place this effect in
            // the float insert chain (see native/README.md).
            if (commandData != nullptr && commandSize >= sizeof(effect_config_t)) {
                const auto* config = static_cast<const effect_config_t*>(commandData);
                if (config->inputCfg.samplingRate != 0) {
                    effect->sampleRate = static_cast<float>(config->inputCfg.samplingRate);
                }
            }
            return 0;
        }
        case EFFECT_CMD_SET_PARAM: {
            if (commandData == nullptr || commandSize < sizeof(effect_param_t)) return -EINVAL;
            const int32_t status = setParameter(effect, commandData, commandSize);
            if (replySize != nullptr && replyData != nullptr && *replySize >= sizeof(int32_t)) {
                *static_cast<int32_t*>(replyData) = status;
                *replySize = sizeof(int32_t);
            }
            return 0;
        }
        default:
            return -EINVAL;
    }
}

static int32_t getDescriptor(effect_handle_t, effect_descriptor_t* descriptor);

static const effect_interface_s kInterface = {
    process,
    command,
    getDescriptor,
    nullptr
};

static int32_t getDescriptor(effect_handle_t, effect_descriptor_t* descriptor) {
    if (descriptor == nullptr) return -EINVAL;
    std::memset(descriptor, 0, sizeof(*descriptor));
    descriptor->uuid = kEffectUuid;
    descriptor->flags = EFFECT_FLAG_TYPE_INSERT | EFFECT_FLAG_INSERT_ANY;
    descriptor->apiVersion = EFFECT_CONTROL_API_VERSION;
    descriptor->cpuLoad = 100;
    descriptor->memoryUsage = sizeof(PulseFxEffect) / 1024 + 1;
    std::strncpy(descriptor->name, "PulseFX Dedicated Distortion", EFFECT_STRING_LEN_MAX - 1);
    std::strncpy(descriptor->implementor, "PulseFX", EFFECT_STRING_LEN_MAX - 1);
    return 0;
}

static int32_t createEffect(
    const effect_uuid_t* uuid,
    int32_t,
    int32_t,
    effect_handle_t* handle
) {
    if (uuid == nullptr || handle == nullptr || std::memcmp(uuid, &kEffectUuid, sizeof(kEffectUuid)) != 0) {
        return -EINVAL;
    }
    auto* effect = new PulseFxEffect();
    effect->itfe = &kInterface;
    *handle = reinterpret_cast<effect_handle_t>(effect);
    return 0;
}

static int32_t releaseEffect(effect_handle_t handle) {
    delete context(handle);
    return 0;
}

static int32_t getLibraryDescriptor(const effect_uuid_t* uuid, effect_descriptor_t* descriptor) {
    if (uuid == nullptr || std::memcmp(uuid, &kEffectUuid, sizeof(kEffectUuid)) != 0) {
        return -EINVAL;
    }
    return getDescriptor(nullptr, descriptor);
}

} // namespace

extern "C" {
audio_effect_library_t AUDIO_EFFECT_LIBRARY_INFO_SYM = {
    AUDIO_EFFECT_LIBRARY_TAG,
    EFFECT_LIBRARY_API_VERSION,
    "PulseFX Dedicated Distortion",
    "PulseFX",
    createEffect,
    releaseEffect,
    getLibraryDescriptor
};
}

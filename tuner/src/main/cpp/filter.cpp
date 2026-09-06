#include "filter.h"
#include <cmath>

namespace {
    constexpr float kPi = 3.14159265358979323846f;
}

EllipticFilter::EllipticFilter() = default;

float EllipticFilter::Biquad::process(float sample) {
    const float y = b0 * sample + z1;
    z1 = b1 * sample - a1 * y + z2;
    z2 = b2 * sample - a2 * y;
    return y;
}

void EllipticFilter::Biquad::reset() {
    z1 = 0.0f;
    z2 = 0.0f;
}

void EllipticFilter::designBandpass(float centerFreq, float q, float sampleRate) {
    const float omega = 2.0f * kPi * centerFreq / sampleRate;
    const float sinOmega = std::sin(omega);
    const float cosOmega = std::cos(omega);
    const float alpha = sinOmega / (2.0f * q);

    float b0 = alpha;
    float b1 = 0.0f;
    float b2 = -alpha;
    float a0 = 1.0f + alpha;
    float a1 = -2.0f * cosOmega;
    float a2 = 1.0f - alpha;

    b0 /= a0;
    b1 /= a0;
    b2 /= a0;
    a1 /= a0;
    a2 /= a0;

    for (auto &section: sections) {
        section.b0 = b0;
        section.b1 = b1;
        section.b2 = b2;
        section.a1 = a1;
        section.a2 = a2;
        section.reset();
    }
}

float EllipticFilter::process(float sample) {
    float value = sample;
    for (auto &section: sections) {
        value = section.process(value);
    }
    return value;
}

void EllipticFilter::reset() {
    for (auto &section: sections) {
        section.reset();
    }
}

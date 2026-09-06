#ifndef CUBESUITE_TUNER_FILTER_H
#define CUBESUITE_TUNER_FILTER_H

class EllipticFilter {
public:
    EllipticFilter();

    void designBandpass(float centerFreq, float q, float sampleRate);

    float process(float sample);

    void reset();

private:
    struct Biquad {
        float b0 = 1.0f;
        float b1 = 0.0f;
        float b2 = 0.0f;
        float a1 = 0.0f;
        float a2 = 0.0f;
        float z1 = 0.0f;
        float z2 = 0.0f;

        float process(float sample);

        void reset();
    };

    Biquad sections[3];
};

#endif

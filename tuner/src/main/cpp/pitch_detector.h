#ifndef CUBESUITE_TUNER_PITCH_DETECTOR_H
#define CUBESUITE_TUNER_PITCH_DETECTOR_H

float detect_pitch_yin(const float *samples, int length, float sampleRate,
                       float *diff, float *diffNorm);

float detect_pitch_hps(const float *samples, int length, float sampleRate);

#endif

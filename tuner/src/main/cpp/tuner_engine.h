#ifndef CUBESUITE_TUNER_ENGINE_H
#define CUBESUITE_TUNER_ENGINE_H

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

bool tuner_init(int sampleRate, int bufferSize);
void tuner_process(const int16_t *samples, int count, float *out);

#ifdef __cplusplus
}
#endif

#endif

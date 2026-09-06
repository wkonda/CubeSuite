#include "tuner_engine.h"
#include <algorithm>
#include <cmath>
#include <vector>
#include <complex>

namespace {

// ------------------------------------------------------------
// Paramètres généraux
// ------------------------------------------------------------
    constexpr int kNumStrings = 6;
    constexpr int kMaxBuffer = 4096;
    constexpr int kHistoryLength = 5;          // Trames pour la stabilité
    constexpr int kMaxHarmonics = 8;           // Plus d'harmoniques pour mieux capter les cordes graves

// Fréquences cibles (accord standard)
    constexpr float kTargetFreqs[kNumStrings] = {
            82.41f, 110.00f, 146.83f, 196.00f, 246.94f, 329.63f
    };

// Pondération des harmoniques (favorise la fondamentale, mais renforce la 2e harmonique pour les graves)
    constexpr float kHarmonicWeights[kMaxHarmonics] = {
            1.00f, 0.95f, 0.85f, 0.70f, 0.55f, 0.40f, 0.30f, 0.20f
    };

// Coefficient d'inharmonicité (typique guitare ~1e-4, varie selon les cordes)
// On utilise une valeur moyenne acceptable pour toutes les cordes.
    constexpr float kInharmonicityCoeff = 0.0001f;

// Plage de recherche (désaccord max supposé)
    constexpr float kSearchMinCents = -50.0f;
    constexpr float kSearchMaxCents = 50.0f;

// Pas de recherche grossière (FFT) et fine (Goertzel)
    constexpr float kCoarseStepCents = 5.0f;   // Pour FFT
    constexpr float kFineStepCents = 1.0f;     // Pour Goertzel

// Seuils et constantes
    constexpr float kNoiseFloor = 0.001f;      // Niveau de bruit minimal
    constexpr float kBaseActivityThreshold = 0.005f;
    constexpr float kLowEThresholdFactor = 0.4f; // Seuil réduit pour la corde de Mi grave
    constexpr float kMinConfidence = 0.6f;     // Confiance minimale pour considérer une corde active

// Taille de la FFT (zero-padding pour résolution spectrale)
    constexpr int kFftSize = 8192;

// ------------------------------------------------------------
// État global
// ------------------------------------------------------------
    float gCentHistory[kNumStrings][kHistoryLength];
    float gScoreHistory[kNumStrings][kHistoryLength];
    float gConfidenceHistory[kNumStrings][kHistoryLength];
    int gHistoryIndex[kNumStrings];
    bool gWasActive[kNumStrings];
    float gNoiseFloorEstimate[kNumStrings];
    float gLastFreq[kNumStrings];      // pour le lissage
    float gLastVar[kNumStrings];       // variance (pour Kalman simplifié)
    int gSampleRate = 44100;
    int gBufferSize = 4096;

// Buffers FFT réutilisables
    std::vector<float> gFftReal(kFftSize);
    std::vector<float> gFftImag(kFftSize);
    std::vector<float> gFftMagnitude(kFftSize / 2);

// ------------------------------------------------------------
// Fonctions utilitaires
// ------------------------------------------------------------
    float median_of(const float *values, int count) {
        float temp[kHistoryLength];
        int valid = 0;
        for (int i = 0; i < count; ++i) {
            if (!std::isnan(values[i])) temp[valid++] = values[i];
        }
        if (valid == 0) return NAN;
        std::sort(temp, temp + valid);
        return temp[valid / 2];
    }

    float mean_of(const float *values, int count) {
        float sum = 0.0f;
        int valid = 0;
        for (int i = 0; i < count; ++i) {
            if (!std::isnan(values[i])) {
                sum += values[i];
                valid++;
            }
        }
        if (valid == 0) return NAN;
        return sum / valid;
    }

    float cents_between(float freq, float target) {
        return 1200.0f * std::log2(freq / target);
    }

// ------------------------------------------------------------
// FFT (radix-2 itérative)
// ------------------------------------------------------------
    void fft(float *real, float *imag, int n) {
        int j = 0;
        for (int i = 0; i < n; ++i) {
            if (i < j) {
                std::swap(real[i], real[j]);
                std::swap(imag[i], imag[j]);
            }
            int m = n >> 1;
            while (m >= 1 && j >= m) {
                j -= m;
                m >>= 1;
            }
            j += m;
        }

        for (int len = 2; len <= n; len <<= 1) {
            float ang = 2.0f * 3.14159265358979323846f / len;
            float wlen_r = std::cos(ang);
            float wlen_i = -std::sin(ang);
            for (int i = 0; i < n; i += len) {
                float w_r = 1.0f;
                float w_i = 0.0f;
                for (int k = 0; k < len / 2; ++k) {
                    float u_r = real[i + k];
                    float u_i = imag[i + k];
                    float v_r = real[i + k + len / 2] * w_r - imag[i + k + len / 2] * w_i;
                    float v_i = real[i + k + len / 2] * w_i + imag[i + k + len / 2] * w_r;
                    real[i + k] = u_r + v_r;
                    imag[i + k] = u_i + v_i;
                    real[i + k + len / 2] = u_r - v_r;
                    imag[i + k + len / 2] = u_i - v_i;
                    float next_w_r = w_r * wlen_r - w_i * wlen_i;
                    w_i = w_r * wlen_i + w_i * wlen_r;
                    w_r = next_w_r;
                }
            }
        }
    }

// ------------------------------------------------------------
// Interpolation de magnitude FFT (parabolique)
// ------------------------------------------------------------
    float interpolate_magnitude(float freq) {
        float bin = freq * kFftSize / static_cast<float>(gSampleRate);
        int bin0 = static_cast<int>(std::floor(bin));
        if (bin0 < 1 || bin0 >= kFftSize / 2 - 1) return 0.0f;
        float y0 = gFftMagnitude[bin0 - 1];
        float y1 = gFftMagnitude[bin0];
        float y2 = gFftMagnitude[bin0 + 1];
        float denom = y0 - 2.0f * y1 + y2;
        float shift = 0.0f;
        if (std::fabs(denom) > 1e-9f) shift = 0.5f * (y0 - y2) / denom;
        float interp_bin = bin0 + shift;
        float mag = y1 + 0.5f * (y2 - y0) * (interp_bin - bin0);
        return mag;
    }

// ------------------------------------------------------------
// Calcul de la fréquence d'une harmonique avec inharmonicité
// ------------------------------------------------------------
    float harmonic_freq(float baseFreq, int harmonicIndex) {
        // f_n = n * f0 * sqrt(1 + B * n^2)
        float n = static_cast<float>(harmonicIndex);
        return n * baseFreq * std::sqrt(1.0f + kInharmonicityCoeff * n * n);
    }

// ------------------------------------------------------------
// Saillance harmonique basée sur la FFT (pour recherche grossière)
// ------------------------------------------------------------
    float harmonic_salience_fft(float baseFreq) {
        const float nyquist = static_cast<float>(gSampleRate) * 0.5f;
        float score = 0.0f;
        int harmonicsFound = 0;

        for (int h = 1; h <= kMaxHarmonics; ++h) {
            float freq = harmonic_freq(baseFreq, h);
            if (freq >= nyquist * 0.95f) break;
            float mag = interpolate_magnitude(freq);
            if (mag > 0.001f) {
                score += mag * kHarmonicWeights[h - 1];
                harmonicsFound++;
            }
        }

        // Exigence minimale de présence harmonique
        bool strongEnough;
        if (baseFreq < 90.0f) {
            // Pour le Mi grave, on accepte une seule harmonique forte (souvent la 2e)
            strongEnough = (harmonicsFound >= 1) || (score > 0.002f);
        } else {
            strongEnough = (harmonicsFound >= 2) || (score > 0.008f);
        }

        if (!strongEnough || score < 0.001f) return 0.0f;

        // Bonus pour harmoniques multiples
        return score * (0.5f + 0.5f * static_cast<float>(harmonicsFound) / kMaxHarmonics);
    }

// ------------------------------------------------------------
// Goertzel pour calcul précis de magnitude
// ------------------------------------------------------------
    float goertzel_power(const float *samples, int count, float freq) {
        const float omega = 2.0f * 3.14159265358979323846f * freq / static_cast<float>(gSampleRate);
        const float coeff = 2.0f * std::cos(omega);
        float q0 = 0.0f, q1 = 0.0f, q2 = 0.0f;

        for (int i = 0; i < count; ++i) {
            q0 = coeff * q1 - q2 + samples[i];
            q2 = q1;
            q1 = q0;
        }
        return q1 * q1 + q2 * q2 - coeff * q1 * q2;
    }

    float get_mag_goertzel(const float *samples, int count, float freq) {
        return 2.0f * std::sqrt(goertzel_power(samples, count, freq)) / static_cast<float>(count);
    }

// ------------------------------------------------------------
// Saillance harmonique basée sur Goertzel (affinage précis)
// ------------------------------------------------------------
    float harmonic_salience_goertzel(const float *samples, int count, float baseFreq) {
        float mags[kMaxHarmonics] = {0};
        const float nyquist = static_cast<float>(gSampleRate) * 0.5f;
        int harmonicsFound = 0;
        float score = 0.0f;

        for (int h = 1; h <= kMaxHarmonics; ++h) {
            float freq = harmonic_freq(baseFreq, h);
            if (freq >= nyquist * 0.95f) break;
            mags[h - 1] = get_mag_goertzel(samples, count, freq);
            if (mags[h - 1] > 0.001f) {
                score += mags[h - 1] * kHarmonicWeights[h - 1];
                harmonicsFound++;
            }
        }

        bool strongEnough;
        if (baseFreq < 90.0f) {
            strongEnough = (harmonicsFound >= 1) || (mags[0] > 0.002f) || (mags[1] > 0.002f);
        } else {
            strongEnough = (harmonicsFound >= 2) || (mags[0] > 0.008f) || (mags[1] > 0.008f);
        }

        if (!strongEnough || score < 0.001f) return 0.0f;
        return score * 2.0f * (0.5f + 0.5f * static_cast<float>(harmonicsFound) / kMaxHarmonics);
    }

// ------------------------------------------------------------
// Estimation grossière par FFT sur plage réduite
// ------------------------------------------------------------
    void coarse_estimate(const float *samples, int count, float *coarseFreqs, float *coarseScores) {
        std::fill(gFftReal.begin(), gFftReal.end(), 0.0f);
        std::fill(gFftImag.begin(), gFftImag.end(), 0.0f);

        for (int i = 0; i < count; ++i) {
            float window = 0.5f - 0.5f * std::cos(2.0f * 3.14159265358979323846f * i / (count - 1));
            gFftReal[i] = samples[i] * window;
        }

        fft(gFftReal.data(), gFftImag.data(), kFftSize);

        for (int i = 0; i < kFftSize / 2; ++i) {
            gFftMagnitude[i] = std::sqrt(gFftReal[i] * gFftReal[i] + gFftImag[i] * gFftImag[i]);
        }

        for (int s = 0; s < kNumStrings; ++s) {
            float bestScore = -1.0f;
            float bestFreq = kTargetFreqs[s];

            for (float cents = kSearchMinCents;
                 cents <= kSearchMaxCents; cents += kCoarseStepCents) {
                float ratio = std::pow(2.0f, cents / 1200.0f);
                float freq = kTargetFreqs[s] * ratio;
                float score = harmonic_salience_fft(freq);
                if (score > bestScore) {
                    bestScore = score;
                    bestFreq = freq;
                }
            }

            coarseFreqs[s] = bestFreq;
            coarseScores[s] = bestScore;
        }
    }

// ------------------------------------------------------------
// Affinage fin par Goertzel autour de l'estimation grossière
// ------------------------------------------------------------
    void fine_estimate(const float *samples, int count, const float *coarseFreqs, float *fineFreqs,
                       float *fineScores) {
        for (int s = 0; s < kNumStrings; ++s) {
            float centerFreq = coarseFreqs[s];
            float bestScore = -1.0f;
            float bestFreq = centerFreq;

            // Recherche fine sur ±10 cents autour de l'estimation (suffisant car le grossier est à ±5 cents)
            for (float cents = -10.0f; cents <= 10.0f; cents += kFineStepCents) {
                float ratio = std::pow(2.0f, cents / 1200.0f);
                float freq = centerFreq * ratio;
                float score = harmonic_salience_goertzel(samples, count, freq);
                if (score > bestScore) {
                    bestScore = score;
                    bestFreq = freq;
                }
            }

            fineFreqs[s] = bestFreq;
            fineScores[s] = bestScore;
        }
    }

// ------------------------------------------------------------
// Inhibition polyphonique itérative (soustraction harmonique)
// ------------------------------------------------------------
    void polyphonic_inhibition(const float *fineFreqs, float *scores, float *adjustedFreqs) {
        float finalScores[kNumStrings];
        float finalFreqs[kNumStrings];
        for (int s = 0; s < kNumStrings; ++s) {
            finalScores[s] = scores[s];
            finalFreqs[s] = fineFreqs[s];
        }

        // 2 passes de soustraction
        for (int pass = 0; pass < 2; ++pass) {
            for (int s = 0; s < kNumStrings; ++s) {
                float inhibition = 0.0f;
                // Pour chaque autre corde
                for (int other = 0; other < kNumStrings; ++other) {
                    if (other == s) continue;
                    // Pour chaque harmonique de l'autre corde
                    for (int h = 1; h <= kMaxHarmonics; ++h) {
                        float harmonicFreq = harmonic_freq(finalFreqs[other], h);
                        // Vérifier si cette harmonique est proche de la fondamentale ou d'une harmonique de la corde s
                        for (int hs = 1; hs <= kMaxHarmonics; ++hs) {
                            float targetFreq = harmonic_freq(finalFreqs[s], hs);
                            float centsDiff = std::fabs(cents_between(targetFreq, harmonicFreq));
                            if (centsDiff < 15.0f) { // Proximité de 15 cents
                                float closeness = 1.0f - (centsDiff / 15.0f);
                                inhibition += finalScores[other] * closeness * 0.6f;
                                break; // une seule harmonique suffit
                            }
                        }
                    }
                }
                finalScores[s] = std::max(0.0f, scores[s] - inhibition);
            }

            // Mise à jour des scores pour la passe suivante
            for (int s = 0; s < kNumStrings; ++s) {
                scores[s] = finalScores[s];
            }
        }

        for (int s = 0; s < kNumStrings; ++s) {
            adjustedFreqs[s] = fineFreqs[s];
        }
    }

// ------------------------------------------------------------
// Filtre de lissage simple (moyenne pondérée exponentielle)
// ------------------------------------------------------------
    void
    smooth_and_update(float freq, float score, int stringIndex, float *outCents, bool *outActive) {
        // Mise à jour de l'historique
        int idx = gHistoryIndex[stringIndex];
        gScoreHistory[stringIndex][idx] = score;
        gCentHistory[stringIndex][idx] = cents_between(freq, kTargetFreqs[stringIndex]);
        gConfidenceHistory[stringIndex][idx] =
                score / (score + gNoiseFloorEstimate[stringIndex] + 1e-6f);
        gHistoryIndex[stringIndex] = (idx + 1) % kHistoryLength;

        // Calcul de la médiane des scores et des cents (robuste aux valeurs aberrantes)
        float medianScore = median_of(gScoreHistory[stringIndex], kHistoryLength);
        float medianCents = median_of(gCentHistory[stringIndex], kHistoryLength);
        float meanConfidence = mean_of(gConfidenceHistory[stringIndex], kHistoryLength);

        // Seuil dynamique (plus bas pour Mi grave)
        float threshold = kBaseActivityThreshold;
        if (stringIndex == 0) threshold *= kLowEThresholdFactor;

        // La corde est active si le score dépasse le seuil ET la confiance est suffisante
        bool active = (medianScore > threshold) && (meanConfidence > kMinConfidence);

        if (active) {
            outCents[stringIndex] = medianCents;
            outActive[stringIndex] = true;
        } else {
            outCents[stringIndex] = NAN;
            outActive[stringIndex] = false;
        }

        // Mise à jour du plancher de bruit (estimation adaptative)
        if (!active) {
            // Si inactif, on met à jour le bruit avec le score actuel (faible)
            gNoiseFloorEstimate[stringIndex] =
                    0.9f * gNoiseFloorEstimate[stringIndex] + 0.1f * score;
        }
    }

} // namespace

// ------------------------------------------------------------
// Initialisation
// ------------------------------------------------------------
bool tuner_init(int sampleRate, int bufferSize) {
    gSampleRate = sampleRate;
    gBufferSize = std::min(std::max(bufferSize, 4096), kMaxBuffer);

    // Allocation des buffers FFT
    gFftReal.resize(kFftSize);
    gFftImag.resize(kFftSize);
    gFftMagnitude.resize(kFftSize / 2);

    for (int s = 0; s < kNumStrings; ++s) {
        gWasActive[s] = false;
        gHistoryIndex[s] = 0;
        gNoiseFloorEstimate[s] = kNoiseFloor;
        gLastFreq[s] = kTargetFreqs[s];
        gLastVar[s] = 0.0f;
        for (int i = 0; i < kHistoryLength; ++i) {
            gScoreHistory[s][i] = 0.0f;
            gCentHistory[s][i] = NAN;
            gConfidenceHistory[s][i] = 0.0f;
        }
    }

    return true;
}

// ------------------------------------------------------------
// Traitement d'un buffer audio
// ------------------------------------------------------------
void tuner_process(const int16_t *samples, int count, float *out) {
    const int n = std::min(count, gBufferSize);
    float floatSamples[kMaxBuffer] = {0};
    const float scale = 1.0f / 32768.0f;
    float mean = 0.0f;

    for (int i = 0; i < n; ++i) mean += static_cast<float>(samples[i]);
    mean /= static_cast<float>(n);

    // Fenêtrage de Hann
    for (int i = 0; i < n; ++i) {
        float window = 0.5f - 0.5f * std::cos(2.0f * 3.14159265358979323846f * i / (n - 1));
        floatSamples[i] = (static_cast<float>(samples[i]) - mean) * scale * window;
    }

    // 1. Estimation grossière par FFT (plage ±50 cents, pas 5 cents)
    float coarseFreqs[kNumStrings];
    float coarseScores[kNumStrings];
    coarse_estimate(floatSamples, n, coarseFreqs, coarseScores);

    // 2. Affinage fin par Goertzel (plage ±10 cents autour de l'estimation)
    float fineFreqs[kNumStrings];
    float fineScores[kNumStrings];
    fine_estimate(floatSamples, n, coarseFreqs, fineFreqs, fineScores);

    // 3. Inhibition polyphonique
    polyphonic_inhibition(fineFreqs, fineScores, fineFreqs);

    // 4. Lissage temporel et décision d'activité
    for (int s = 0; s < kNumStrings; ++s) {
        bool active;
        smooth_and_update(fineFreqs[s], fineScores[s], s, out, &active);
        out[s + 6] = active ? 1.0f : 0.0f;
    }
}

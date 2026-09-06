#include <iostream>
#include <fstream>
#include <vector>
#include <cstdint>
#include <cmath>
#include <iomanip>
#include "../src/main/cpp/tuner_engine.h"
#include "../src/main/cpp/pitch_detector.h"

// Mock tuner_engine implementation if needed, but we can just include the cpp files
// during compilation.

void process_file(const std::string &filename) {
    std::ifstream fs(filename, std::ios::binary);
    if (!fs) {
        std::cerr << "Could not open " << filename << std::endl;
        return;
    }

    // Skip WAV header (44 bytes for a simple PCM WAV)
    fs.seekg(44);

    std::vector<int16_t> samples;
    int16_t sample;
    while (fs.read(reinterpret_cast<char *>(&sample), sizeof(sample))) {
        samples.push_back(sample);
    }

    std::cout << "Processing " << filename << " (" << samples.size() << " samples)" << std::endl;

    tuner_init(44100, 4096);

    const int step = 2048;
    for (size_t i = 0; i + 4096 <= samples.size(); i += step) {
        float out[12];
        tuner_process(&samples[i], 4096, out);

        bool detected = false;
        std::cout << std::fixed << std::setprecision(2) << "At " << (float) i / 44100.0f << "s: ";
        int count = 0;
        for (int s = 0; s < 6; ++s) {
            if (!std::isnan(out[s])) {
                std::cout << "[S" << s << ": " << out[s] << "c] ";
                detected = true;
                count++;
            }
        }
        if (count > 1) std::cout << "(POLY: " << count << ") ";

        // Also try YIN
        std::vector<float> floatSamples(2048);
        for (int j = 0; j < 2048; ++j) floatSamples[j] = samples[i + j] / 32768.0f;
        float diff[2048], diffNorm[2048];
        float pitch = detect_pitch_yin(floatSamples.data(), 2048, 44100.0f, diff, diffNorm);
        if (!std::isnan(pitch)) {
            std::cout << " | YIN: " << pitch << " Hz";
        }

        float hps_pitch = detect_pitch_hps(floatSamples.data(), 2048, 44100.0f);
        if (!std::isnan(hps_pitch)) {
            std::cout << " | HPS: " << hps_pitch << " Hz";
        }

        if (!detected && std::isnan(pitch)) {
            std::cout << "No pitch detected";
        }
        std::cout << std::endl;
    }
}

int main(int argc, char **argv) {
    if (argc < 2) {
        std::cerr << "Usage: " << argv[0] << " <wav_file>" << std::endl;
        return 1;
    }
    for (int i = 1; i < argc; ++i) {
        process_file(argv[i]);
    }
    return 0;
}

# CubeSuite

CubeSuite is an Android utility application for managing and configuring **M-VAVE Cube Baby**
multi-effects pedals.

## Disclaimer

**This project is provided "as-is". If it doesn't work for you, don't bother me.** No support or
guarantees are provided.

## Features

- **USB OTG Connectivity:** Direct connection to your pedal for stable communication.
- **Visual Editing:** Tweak all effects parameters (Amp, Cab, Mod, Delay, Reverb) using an intuitive
  mobile interface.
- **Preset Control:** Seamlessly switch between and edit Presets A, B, and C.
- **Flash Memory Sync:** Save your settings directly to the pedal hardware.
- **Dark Mode UI:** Optimized for low-light environments (stages, studios).

## Getting Started

### Prerequisites

- An Android device running Android 8.0 (Oreo) or higher.
- A USB OTG (On-The-Go) adapter/cable compatible with your phone.
- M-VAVE Cube Baby pedal.

### Installation

1. Clone this repository.
2. Open the project in Android Studio.
3. Build and install the APK on your Android device.

### Usage

1. Connect your M-VAVE Cube Baby to your phone using a USB OTG cable.
2. Open **CubeSuite**.
3. Grant USB permissions when prompted.
4. Once the device is detected, you can start editing your presets.
5. Tap **SAVE** to persist changes to the pedal's hardware memory.

## Development

CubeSuite is built with:

- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) for UI.
- Custom USB/MIDI implementation for communication with M-VAVE hardware.

## Acknowledgements

Special thanks to the [cuvave-midi](https://github.com/pferreir/cuvave-midi) project for its
research and implementation of the Cube Baby communication protocol.

## License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE)
file for details.

# 🎛️ PulseFX Studio (v3.0.0)

> **Next-Generation Real-Time DSP Audio Mastering Suite for Android 15 & Modern Devices**  
> *Engineered for Unrooted & Rooted Android Devices with Zero-Latency Master Stream Capture.*

[![Release](https://img.shields.io/badge/Release-v3.0.0-00E5FF.svg)](https://github.com/Densuper/ViPER4Android-FX-Studio-Suite/releases/tag/v3.0.0)
[![Android](https://img.shields.io/badge/Android-8.0%20to%2015%2B-69F0AE.svg)](https://developer.android.com)
[![Platform](https://img.shields.io/badge/Target-Google%20Pixel%20%26%20Universal-FFB74D.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🚀 Overview

**PulseFX Studio** is a complete, ground-up reimagining of Android audio signal processing. While inspired by the legendary acoustic concepts of **ViPER's Audio**, PulseFX Studio replaces the legacy, deprecated C++ kernel drivers with a modern, high-precision **32-bit floating-point DSP engine** that works across **all media (YouTube, Spotify, Games, Web Audio)** without requiring root access.

Featuring an interactive **Catmull-Rom Paragraphic Spline Curve Equalizer**, touch-drawn resonance curves, and a clean Material 3 dark studio interface, PulseFX Studio gives you studio-grade acoustic control on your phone.

---

## 🌟 Key Features

### 🎛️ 1. Interactive Paragraphic Spline Curve Equalizer
- **20 Hz to 20 kHz Logarithmic Scale**: Freeform touch drawing and individual node dragging with smooth Catmull-Rom cubic spline interpolation.
- **Real-Time Biquad IIR Math**: Resamples drawn curves directly into 10 Direct Form II peaking filters without audio pops or drops.
- **One-Touch Reset**: Instantly flatten the response curve to 0 dB.
- **21 Classic Audiophile Presets**: Acoustic, Bass Booster, Classical, Dance, Deep, Electronic, Hip-Hop, Jazz, Rock, Vocal Booster, etc.

### 🔊 2. Real-Time Acoustic Mastering Modules (18-Module Suite)
1. **Master Limiter**: Lookahead soft-knee true-peak ceiling (0 dBFS protection) with independent output gain and pan control.
2. **Playback AGC**: Fast-attack ballistic automatic gain leveling with configurable multipliers.
3. **FET Compressor**: Dynamic transfer curve visualizer with adjustable threshold (-40 to 0 dB), ratio (1:1 to 20:1), and makeup gain.
4. **ViPER-DDC (Digital Device Correction)**: Harman and acoustic correction curves for Apple AirPods Pro, Sony WH-1000XM4, Sennheiser HD650, Audio-Technica M50x, Beyerdynamic DT990, Bose QC45, Galaxy Buds2 Pro, etc.
5. **ViPER Bass**: Dynamic sub-harmonic frequency synthesizers with **Natural Bass**, **Pure Bass+ (Quadratic Rectification)**, and **Subwoofer** modes.
6. **ViPER Clarity**: High-shelf harmonic overtones with **Natural**, **Ozone+ (Asymmetric Exciter)**, and **XHiFi Pro** harmonic expansion.
7. **Analog Tube Simulator (6N1P / 12AX7)**: Non-linear hyperbolic tangent ($\tanh$) triode saturation for analog warmth.
8. **Convolver / Analog Tape & Console Modeling**: Studer A800 tape machine, Telefunken 12AX7 tube, Sony Walkman MegaBass, Lexicon 480L hall reverb, Neve 1073 console transformer, and SSL 4000G bus compressor.
9. **Field Surround & Haas Differential Delay**: Mid-Side vocal spatializer and binaural inter-aural time delay ($1\text{ ms} \to 20\text{ ms}$).
10. **Schroeder-Moorer Reverberation**: 4 parallel feedback comb filters with high-frequency damping + all-pass diffusion matrix.
11. **Auditory Protection (Cure+ Crossfeed)**: Eliminates ear fatigue during long headphone sessions.
12. **Speaker Optimization**: Acoustic EQ correction profile tailored for external phone speakers.

---

## 🛡️ Architecture: Non-Root vs. Root

```
┌─────────────────────────────────────────────────────────────┐
│                 ALL AUDIO SOURCES                           │
│     YouTube • Spotify • Apple Music • Games • Chrome        │
└──────────────────────────────┬──────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
┌──────────────────────────────┐    ┌──────────────────────────────┐
│       NON-ROOT MODE          │    │          ROOT MODE           │
│   AudioPlaybackCapture       │    │   Shevery AudioFlinger Hook  │
│   Master 48kHz Stereo PCM    │    │   Auto-Session Relay Daemon  │
└──────────────┬───────────────┘    └──────────────┬───────────────┘
               │                                   │
               └──────────────────┬────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│           PULSEFX 32-BIT FLOAT DSP ENGINE (Pure Math)       │
│    Biquads • Spline Splines • Tube Saturation • Limiter     │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 PHYSICAL HARDWARE OUTPUT                    │
│     DAC / USB-C / CMF Buds (Bluetooth) / Phone Speakers     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📥 Installation

### Non-Root Users (Standard Android 8.0 - 15+):
1. Download **`PulseFX-Studio-v3.0.0.apk`** from the [Releases](https://github.com/Densuper/ViPER4Android-FX-Studio-Suite/releases) page.
2. Install the APK on your device.
3. Open the app, grant the Audio Capture permission prompt, and enjoy system-wide sound enhancement.

### Root / Magisk / KernelSU / APatch Users:
1. Flash **`PulseFX-Studio-v3.0.0-Shevery.zip`** in Magisk / KernelSU / APatch.
2. Reboot your device. The background daemon will automatically bind AudioFlinger sessions directly into PulseFX Studio.

---

## 🌟 Tribute & Acknowledgments

- **Lead Architect & Developer**: **Denver Colaco**
- **Lead AI & Systems Engineer**: **J.A.R.V.I.S. (Antigravity Core)**
- **Acoustic Tribute**: Inspired by the classic DSP concepts and algorithms of **ViPER's Audio (Euphony & ZhuHang)**.
- **UI Heritage**: Inspired by the Jetpack Compose explorations of **Team DeWitt & Iscle**.

---

## 📄 License
This project is open-source under the [Apache License 2.0](LICENSE).

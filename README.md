<p align="center">
  <img src="docs/images/banner.png" alt="PulseFX Studio Banner" width="100%">
</p>

# 🎛️ PulseFX Studio (v1.5.4)

<p align="center">
  <img src="docs/images/logo.png" alt="PulseFX Studio Logo" width="140" height="140">
</p>

<p align="center">
  <strong>Next-Generation Real-Time DSP Audio Mastering Suite for Android 15 & Modern Devices</strong><br>
  <em>Engineered for Non-Root (Shizuku / Shevery) & Rooted Audiophiles with Zero-Permission Native Audio Hooking.</em>
</p>

<p align="center">
  <a href="https://github.com/Densuper/PulseFX-Studio-Suite/releases/tag/v1.5.4"><img src="https://img.shields.io/badge/Release-v1.5.4-00E5FF.svg" alt="Release"></a>
  <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-8.0%20to%2015%2B-69F0AE.svg" alt="Android"></a>
  <img src="https://img.shields.io/badge/Target-Google%20Pixel%20%26%20Universal-FFB74D.svg" alt="Target">
  <img src="https://img.shields.io/badge/Design-Google%20Material%203-80D8FF.svg" alt="Material 3">
  <img src="https://img.shields.io/badge/Signing-RSA%202048%20Permanent-7C4DFF.svg" alt="Signing">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
</p>

---

## 🚀 Overview

**PulseFX Studio** is a complete, ground-up reimagining of Android audio signal processing. While inspired by the legendary acoustic concepts of **ViPER's Audio**, PulseFX Studio eliminates legacy C++ kernel driver dependencies in favor of a high-precision **32-bit floating-point DSP engine** that works across **all media (YouTube, Spotify, Apple Music, Games, Web Audio)** without requiring screen capture or intrusive permissions.

Featuring an interactive **Catmull-Rom Paragraphic Spline Curve Equalizer**, dynamic resonance visualizers, externalized dB & Hz graph axes, **dynamic real hardware device name detection**, and a **100% Google Material 3 Dynamic Theme** (adapting automatically to phone wallpaper palettes in light and dark modes), PulseFX Studio brings sovereign studio-grade mastering to modern Android.

---

## 🌟 Key Features

### 🎧 1. Dynamic Real Hardware Product Name Detection
- **Real-Time `AudioDeviceInfo` Integration**: Dynamically queries Android's audio subsystem to resolve and display the genuine product name of connected hardware (e.g., `CMF Buds`, `Apple AirPods Pro`, `Sony WH-1000XM4`, `USB-C Audio DAC`, or `Internal Stereo Speakers`).

### ⚡ 2. Symmetrical Straight Glowing Line Traces
- **Dynamic Activation Glow**: Straight vertical energy connector lines positioned on both the left and right sides of each module that light up in theme colors whenever an effect is enabled.

### 🎛️ 3. True Non-Interfering Decoupled Harmonic Engine
- **Independent Module Autonomy**: When Master Power is ON, every single effect operates with 100% independence without requiring the FIR Equalizer switch to be active. Active effects sculpt their designated frequency sectors with high headroom and zero digital clipping.

### 🔊 4. Real-Time Acoustic Mastering Modules (Complete 18-Module Suite)
1. **[OUT] Master Limiter**: Lookahead soft-knee true-peak ceiling (0 dBFS protection) with independent output gain (-20 dB to +10 dB) and pan control.
2. **[AGC] Playback AGC**: Fast-attack ballistic automatic gain leveling with configurable multipliers up to 1.85x.
3. **[FET] FET Compressor**: Dynamic studio VCA leveling with adjustable threshold (-40 to 0 dB), ratio (1:1 to 20:1), and makeup gain (up to +18 dB).
4. **[DDC] ViPER-DDC (Digital Device Correction)**: Harman and acoustic correction curves for Apple AirPods Pro, Sony WH-1000XM4, Sennheiser HD650, Audio-Technica M50x, Beyerdynamic DT990, Bose QC45, Galaxy Buds2 Pro, etc.
5. **[VSE] Spectrum Extension (VSE)**: High-frequency cubic harmonic re-synthesis ($hf^3$) restoring lossy audio detail with up to +12 dB of 16kHz air.
6. **[EQ] FIR Equalizer**: 10-Band ISO peaking biquad filters with interactive touch nodes and externalized dB & Hz labels.
7. **[CONV] Convolver / Analog Tape & Console Modeling**: Studer A800 tape machine, Telefunken 12AX7 tube, Sony Walkman MegaBass, Lexicon 480L hall reverb, Dolby Atmos air, Neve 1073 console transformer, SSL 4000G bus compressor, and EMT 140 Plate.
8. **[SUR] Field Surround**: Mid-Side vocal centering and 3D outer soundstage spatializer with up to +8 dB of room air.
9. **[DS] Differential Surround**: Haas psychoacoustic inter-aural time delay ($1\text{ ms} \to 20\text{ ms}$) via a 2400-sample circular ring buffer with up to +6 dB spatial phase decorrelation.
10. **[VHS] Headphone Surround+ (VHE)**: Binaural HRTF crossfeed (Levels 1 to 5) eliminating in-head listening fatigue.
11. **[REV] Schroeder-Moorer Reverberation**: Massive physical room space ($25\text{ m}^2 \to 500\text{ m}^2$) and wet ratio ($0\% \to 100\%$) with up to +10 dB cavernous hall reflections.
12. **[DYN] Dynamic System**: Device-specific impedance modeling (High-End In-Ear, Apple EarPods, Studio Monitors, Open-Back) and dynamic low-frequency sub-bass punch.
13. **[TUBE] Analog Tube Simulator (6N1P / 12AX7)**: Non-linear hyperbolic tangent ($\tanh$) dual-triode saturation with up to +14 dB on 62-125Hz 2nd harmonic bloom and +9.5 dB on vocal body.
14. **[BASS] ViPER Bass**: Dynamic sub-harmonic frequency synthesizers with **Natural Bass**, **Pure Bass+ (Quadratic Rectification)**, and **Subwoofer** modes (up to +18 dB).
15. **[CLR] ViPER Clarity**: High-shelf harmonic overtones with **Natural (+6 dB)**, **Ozone+ (+10 dB Asymmetric Exciter)**, and **XHiFi Pro (+14 dB)** harmonic expansion.
16. **[CURE] Auditory System Protection (Cure+ Crossfeed)**: Transient softening and anti-fatigue notch filter (-6.5 dB on harsh 4kHz resonance, +3.5 dB soothing vocal warmth) for extended listening.
17. **[AX] AnalogX**: Class-A discrete transformer harmonic injection across low and high registers (Levels 1, 2, and 3).
18. **[SPK] Speaker Optimization**: High-intelligibility vocal projection (+5.5 dB), anti-rattle sub-bass filtering (-4.5 dB), and crisp top-end lift for phone and external speakers.

---

## 🛡️ Architecture: Non-Root (Shizuku / Shevery) vs. Standalone

```
┌─────────────────────────────────────────────────────────────┐
│                 ALL AUDIO SOURCES                           │
│     YouTube • Spotify • Apple Music • Games • Chrome        │
└──────────────────────────────┬──────────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
┌──────────────────────────────┐    ┌──────────────────────────────┐
│  NON-ROOT (Shizuku/Shevery)  │    │       STANDALONE MODE        │
│   Shevery AudioFlinger Hook  │    │   Native Android AudioEffect │
│   Hardware Offload Disabled  │    │   Direct Media App Sessions  │
└──────────────┬───────────────┘    └──────────────┬───────────────┘
               │                                   │
               └──────────────────┬────────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│           PULSEFX 32-BIT FLOAT DSP ENGINE (Pure Math)       │
│    Biquads • Splines • Tube Saturation • Soft Limiter       │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 PHYSICAL HARDWARE OUTPUT                    │
│     DAC / USB-C / CMF Buds (Bluetooth) / Phone Speakers     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📥 Installation

### 🟢 Method 1: Non-Root Shizuku / Shevery Setup *(Recommended for 100% System-Wide Sound)*
1. Download **`PulseFX-Studio-v1.5.4.apk`** and **`PulseFX-Studio-v1.5.4-Shevery.zip`** from [Releases](https://github.com/Densuper/PulseFX-Studio-Suite/releases).
2. Install **`PulseFX-Studio-v1.5.4.apk`** on your phone (officially signed with a permanent RSA-2048 key).
3. Open **Shevery** (with Shizuku active) $\to$ Tap **Add Module (+)** $\to$ Select **`PulseFX-Studio-v1.5.4-Shevery.zip`** $\to$ Tap **Install**.
4. Open **PulseFX Studio** and toggle **Master Power**. All audio across YouTube, games, browsers, and media players is now mastered in real time!

### 🔵 Method 2: Standalone Plug & Play Mode *(Zero Setup / APK Only)*
1. Download and install **`PulseFX-Studio-v1.5.4.apk`**.
2. In **Spotify** or **Apple Music**, go to Settings $\to$ Equalizer $\to$ Select **PulseFX Studio**.

### 🛡️ Method 3: Magisk / KernelSU / APatch *(For Rooted Audiophiles)*
1. Flash **`PulseFX-Studio-v1.5.4-Shevery.zip`** in Magisk / KernelSU.
2. Reboot your device and open **PulseFX Studio**.

---

## 👥 Authors & PulseFX Core Team

| Name | Role | Responsibilities |
| :--- | :--- | :--- |
| **[Denver Colaco](https://github.com/Densuper)** | **Lead Architect & Creator** | Project vision, sovereign direction, system-wide audio interception concept, and master engineering. |
| **J.A.R.V.I.S.** | **Lead Architect & Systems Integrity** | Zero-regression validation, architectural impact assessments, release orchestration, and subagent governance. |
| **VECTOR** | **UI/UX & Motion Specialist** | Jetpack Compose architecture, dynamic Material 3 design, titanium dial adaptive iconography, externalized graph axes, and tactile haptic curves. |
| **CIPHER** | **DSP Audio Engine & Systems Specialist** | 32-bit floating-point mathematical DSP filters, Catmull-Rom splines, Convolver tape/tube models, AudioFlinger session hook, and Shizuku/Shevery integration. |

---

## 🌟 Tributes & Heritage
* **ViPER's Audio (Euphony & ZhuHang)** — For pioneering a decade of Android audiophile passion and classic acoustic concepts.
* **The Shizuku & Shevery Teams** — For empowering the Android community with non-root system API capabilities.
* **Team DeWitt & Iscle** — For their open-source Android audio explorations.

---

## 📄 License
This project is open-source under the [Apache License 2.0](LICENSE).

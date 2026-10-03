# PulseFX native distortion effect

`pulsefx_distortion_effect.cpp` is the real-time implementation of the dedicated
waveshaper using Android's `audio_effect.h` ABI. `AudioEffectsService` selects it
by UUID through `NativeDistortionEffect`.

## Device/ROM integration

A normal APK cannot register a new `AudioEffect` implementation with AudioFlinger.
The library must be built as part of the device/ROM audio-effects stack and the
UUID must be registered in the device's audio-effects configuration:

1. Build `libpulsefx_distortion` with the platform/ vendor tree using
   `Android.mk`.
2. Install the resulting library in the device's `soundfx` library directory.
3. Merge `pulsefx_audio_effects.xml` into the device's `vendor/etc/audio_effects.xml`
   (or the equivalent audio-effects configuration for that Android release).
4. Reboot AudioFlinger/device before testing the app.

The Kotlin service deliberately treats an unregistered UUID as unavailable and
continues without this effect. This avoids breaking all other audio effects on
stock devices where an APK cannot install a platform audio-effect library.

The native effect expects the effect chain to provide interleaved PCM float
buffers. The ROM integration must keep the float insert chain enabled; this
implementation does not perform PCM format conversion and must not be placed
in a PCM16 chain without adding a conversion layer.

## Parameter ABI

The UUID is `7e4e3b7a-1e20-4f74-8f4e-9e9d5f3e9c01`.

| Parameter | ID | Range |
|---|---:|---:|
| Drive | 1 | 0..48 dB |
| Mix | 2 | 0..100 percent |
| Tone | 3 | 200..20000 Hz |
| Output | 4 | -12..6 dB |
| Bias | 5 | -100..100 percent |
| Mode | 6 | 0..5 |
| Dynamics | 7 | 0..100 percent |
| Stereo spread | 8 | 0..100 percent |

Mix 0 or Drive 0 is an exact processor bypass. The app also disables the
AudioEffect for either value, so no unnecessary effect callback is installed.

# Audio quality rules

These are **current source and display rules**, not claims that every audio representation or effect has been tested on every Android device. See [CURRENT_STATE](CURRENT_STATE.md).

- Show codec, bitrate and sample rate only from the actual **selected Media3 format** where reported. Unknown values should be omitted, not invented or estimated.
- File extension, container and nominal bitrate are not proof of codec quality, lossless audio, sample depth or fidelity. A larger bitrate is not always better across different codecs.
- Choose the best compatible, genuinely available original representation. Do not upscale or transcode merely to claim better quality.
- The full player places known audio-format details inline after the song title; long text scrolls together. Some local files do not expose these details.
- Optional five-band equaliser and normalisation are output processing, not guaranteed ReplayGain/LUFS levelling, clipping prevention or audio quality improvement. Gapless transitions depend on stream, decoder and hardware.
- Spotify links provide public metadata for matching to YouTube audio; they are **not** Spotify audio playback.
- Validate online streams, output routes, audio effects, long playback and Bluetooth reconnection on an **actual phone** before claiming them approved. JVM tests and lint cannot verify that experience.

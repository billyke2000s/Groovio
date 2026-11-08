# Audio quality rules

- Report codec, bitrate, and sample rate from the actual selected playback format when available.
- Unknown values must be omitted, never estimated or invented.
- A container extension is not proof of codec or bitrate.
- Do not upscale or transcode to claim lossless audio.
- Choose the highest-quality compatible, authorised representation where providers offer alternatives.
- Different codecs cannot be ranked solely by nominal bitrate.
- Codec labels are shown inline after the title in the full-screen player (`AudioFormatLabel`), and scroll together with long titles.
- Codec/bitrate labels may be absent for some local files if the decoder or source does not expose them.

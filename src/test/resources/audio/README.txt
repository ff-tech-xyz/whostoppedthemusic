Generated test signals, dedicated to CC0-1.0 with this project.
These are synthetic sine waves, not recordings or third-party songs.

stereo.mp3: 3 seconds, 440 Hz, 44100 Hz sample rate, two channels.
mono.ogg: 3 seconds, 660 Hz, 48000 Hz sample rate, one channel.

Generated using FFmpeg 9.0.1:
ffmpeg -f lavfi -i 'sine=frequency=440:duration=3:sample_rate=44100' -ac 2 -c:a libmp3lame -q:a 4 stereo.mp3
ffmpeg -f lavfi -i 'sine=frequency=660:duration=3:sample_rate=48000' -ac 1 -c:a libvorbis -q:a 4 mono.ogg

The unit decoder checks and existing packaged-client harness share these fixtures.

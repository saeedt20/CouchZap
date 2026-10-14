#!/usr/bin/env python3
"""Original deterministic CRT-inspired click. No recordings/samples; asset dedicated to CC0."""
import math, random, struct, wave
from pathlib import Path
rate, seconds = 22050, .160
rng = random.Random(42024)
noise = 0.0
samples = []
for i in range(round(rate * seconds)):
    t = i / rate
    noise += .32 * (rng.uniform(-1, 1) - noise)
    attack = min(1, t / .003)
    tail = min(1, (seconds - t) / .018)
    click = math.sin(2 * math.pi * 850 * t) * math.exp(-t / .009)
    pop = .35 * math.sin(2 * math.pi * 95 * t) * math.exp(-t / .035)
    brush = .15 * noise * math.exp(-t / .027)
    v = (click + pop + brush) * attack * tail
    samples.append(v)
peak = max(abs(x) for x in samples)
samples = [x * .18 / peak for x in samples]
path = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/crt_switch.wav'
with wave.open(str(path), 'wb') as out:
    out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate)
    out.writeframes(b''.join(struct.pack('<h', round(x * 32767)) for x in samples))
print(f'{path.name}: {path.stat().st_size} bytes; {seconds*1000:.0f} ms; peak -14.9 dBFS; RMS {20*math.log10(math.sqrt(sum(x*x for x in samples)/len(samples))):.1f} dBFS')

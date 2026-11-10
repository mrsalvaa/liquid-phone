#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Genera app/src/main/res/raw/call_end.wav: el "bloop" que suena al terminar una llamada.

Un seno que cae de 520 a 260 Hz en 0,35 s, con ataque de 5 ms y caída exponencial.
Uso: python3 tools/call-end-tone.py
"""
import math
import pathlib
import struct
import wave

RATE = 44100
DURATION = 0.35
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw/call_end.wav"

samples = []
phase = 0.0
n = int(RATE * DURATION)
for i in range(n):
    t = i / RATE
    freq = 520 - 260 * (t / DURATION)
    phase += 2 * math.pi * freq / RATE
    envelope = min(1.0, i / (RATE * 0.005)) * math.exp(-7 * t)
    samples.append(0.6 * envelope * math.sin(phase))

with wave.open(str(OUT), "w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(struct.pack("<%dh" % n, *(int(s * 32767 * 0.85) for s in samples)))

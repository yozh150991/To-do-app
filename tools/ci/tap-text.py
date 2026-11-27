#!/usr/bin/env python3
"""Prints "x y", the centre of the first on-screen element whose text or content description is one
of the given labels, from a `uiautomator dump` XML on stdin. Prints nothing when none matches.

Usage: adb exec-out cat /sdcard/ui.xml | tap-text.py Inbox Вхідні Skrzynka
"""
import re
import sys
import xml.etree.ElementTree as ET

labels = set(sys.argv[1:])
raw = sys.stdin.buffer.read().decode("utf-8", errors="replace")
start = raw.find("<?xml")
if start < 0:
    start = raw.find("<hierarchy")
if start < 0:
    sys.exit(0)
try:
    root = ET.fromstring(raw[start:raw.rfind(">") + 1])
except ET.ParseError:
    sys.exit(0)
for node in root.iter("node"):
    if node.get("text") in labels or node.get("content-desc") in labels:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break

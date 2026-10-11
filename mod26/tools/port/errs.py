"""Prints each unique javac error from err.txt with its source line and symbol."""
import sys

t = open('C:/Users/Massi/port26/err.txt', encoding='utf-8', errors='replace').read().split('\n')
seen = set()
for i, l in enumerate(t):
    if 'error:' in l and l.startswith('C:'):
        f = l.split('blurredclient')[-1][5:]
        sym = t[i + 3].strip() if i + 3 < len(t) and 'symbol' in t[i + 3] else ''
        k = f + ' | ' + t[i + 1].strip()[:120] + ' | ' + sym
        if k not in seen:
            seen.add(k)
            print(k[:320])

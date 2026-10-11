"""Flattens Stonecutter `//?` branches to one Minecraft version (default 1.21.11).

Keeps the code of the branches whose condition holds, drops the rest and the
markers themselves. Usage: python resolve_sc.py <dir> [version]
"""
import os
import re
import sys

ROOT = sys.argv[1]
VER = tuple(int(x) for x in (sys.argv[2] if len(sys.argv) > 2 else '1.21.11').split('.'))


def vt(s):
    return tuple(int(x) for x in s.split('.'))


def cond(expr):
    expr = expr.strip()
    ok = True
    for part in re.split(r'\s*&&\s*', expr):
        m = re.fullmatch(r'(>=|<=|>|<|=|==)?\s*([\d.]+)', part.strip())
        if not m:
            raise ValueError('condition: ' + expr)
        op, v = m.group(1) or '=', vt(m.group(2))
        ok &= {'>=': VER >= v, '<=': VER <= v, '>': VER > v, '<': VER < v, '=': VER == v, '==': VER == v}[op]
    return ok


MARK = re.compile(r'^(\s*)(?:\*/)?\s*//\?\s*(.*)$')


def strip_comment_wrap(line):
    s = line
    s = re.sub(r'^(\s*)/\*\s?', r'\1', s) if s.strip().startswith('/*') and not s.strip().startswith('/**') else s
    return s


def process(text):
    out = []
    # stack of [taken_any, active]
    stack = []
    lines = text.split('\n')
    for line in lines:
        m = MARK.match(line)
        if m:
            body = m.group(2).strip()
            # `} elif X {`, `} else {`, `if X {`, `}`, also single-line `if X` (applies to next line)
            if body.startswith('}'):
                rest = body[1:].strip()
                if not stack:
                    raise ValueError('unbalanced: ' + line)
                taken, _ = stack.pop()
                if rest.startswith('elif'):
                    c = cond(rest[4:].rstrip('{').strip())
                    act = (not taken) and c
                    stack.append([taken or act, act])
                elif rest.startswith('else'):
                    stack.append([True, not taken])
                elif rest == '':
                    pass
                else:
                    raise ValueError('marker: ' + line)
                continue
            if body.startswith('if'):
                expr = body[2:].strip()
                if expr.endswith('{'):
                    c = cond(expr[:-1])
                    stack.append([c, c])
                else:
                    # Single-line condition: governs the next line only.
                    stack.append(['single', cond(expr)])
                continue
            raise ValueError('marker: ' + line)
        if stack and stack[-1][0] == 'single':
            _, act = stack.pop()
            if all(s[1] for s in stack) and act:
                out.append(unwrap(line))
            continue
        if all(s[1] for s in stack):
            out.append(unwrap(line) if stack else line)
    if stack:
        raise ValueError('unclosed')
    return '\n'.join(out)


def unwrap(line):
    # Active code is never commented, but be safe with a stray `/*` / `*/` wrap.
    t = line.strip()
    if t.startswith('/*') and not t.startswith('/**'):
        return re.sub(r'/\*\s?', '', line, count=1)
    if t.endswith('*/') and not t.startswith('*') and '/*' not in t:
        return line[:line.rfind('*/')]
    return line


bad = []
for d, _, files in os.walk(ROOT):
    for f in files:
        if not f.endswith(('.java', '.json')):
            continue
        p = os.path.join(d, f)
        src = open(p, encoding='utf-8').read()
        if '//?' not in src:
            continue
        try:
            res = process(src)
        except ValueError as e:
            bad.append((p, str(e)))
            continue
        open(p, 'w', encoding='utf-8', newline='\n').write(res)
for p, e in bad:
    print('FAILED', p, e)
print('done')

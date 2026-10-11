"""Checks every mixin's targets against the 26.2 decompiled source.

For each mixin: the @Mixin target class(es), each injector's method names,
each @At target ("Lowner;name(desc)ret" invokes and field gets), and
@Accessor/@Invoker names. Reports whatever can't be found by name.
"""
import os
import re

MIXINS = 'C:/Users/Massi/port26/proj/src/main/java/dev/blurredclient/mod/mixin'
SRC = 'C:/Users/Massi/port26/mc262'
cache = {}


def source(cls):
    """Decompiled source of a class (inner classes map to their outer file)."""
    path = cls.replace('.', '/').split('$')[0]
    if path not in cache:
        p = os.path.join(SRC, path + '.java')
        cache[path] = open(p, encoding='utf-8').read() if os.path.exists(p) else None
    return cache[path]


def has_member(cls, name):
    s = source(cls)
    if s is None:
        return None
    if name in ('<init>', '<clinit>'):
        return True
    return re.search(r'\b' + re.escape(name) + r'\b\s*[(;=]', s) is not None or \
        re.search(r'\b' + re.escape(name) + r'\b', s) is not None


def imports(text):
    m = {}
    for imp in re.findall(r'^import ([\w.]+);', text, re.M):
        m[imp.rsplit('.', 1)[1]] = imp
    return m


problems = []
for f in sorted(os.listdir(MIXINS)):
    if not f.endswith('.java'):
        continue
    t = open(os.path.join(MIXINS, f), encoding='utf-8').read()
    imp = imports(t)
    pkg_cls = []
    tm = re.search(r'@Mixin\(\s*(?:value\s*=\s*)?\{?([^)}]*)\}?', t)
    targets = []
    if tm:
        for c in re.findall(r'([\w.$]+)\.class', tm.group(1)):
            full = imp.get(c.split('.')[0], c)
            if '.' in c and c.split('.')[0] in imp:
                full = imp[c.split('.')[0]] + '$' + c.split('.', 1)[1]
            targets.append(full)
        for c in re.findall(r'"([\w./$]+)"', tm.group(1)):
            targets.append(c.replace('/', '.'))
    for c in targets:
        if source(c) is None:
            problems.append(f'{f}: target class missing: {c}')
    # Injector method names.
    for m in re.finditer(r'@(Inject|Redirect|ModifyVariable|ModifyArg|ModifyArgs|ModifyConstant|ModifyReturnValue|WrapOperation|WrapWithCondition|ModifyExpressionValue)\((.*?)\)\s*\n\s*(?:@|private|public|protected|static)', t, re.S):
        body = m.group(2)
        mm = re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', body)
        if mm:
            for name in re.findall(r'"([^"]+)"', mm.group(1)):
                n = name.split('(')[0]
                for c in targets:
                    r = has_member(c, n)
                    if r is False:
                        problems.append(f'{f}: method "{n}" not in {c.rsplit(".", 1)[-1]}')
        for tgt in re.findall(r'target\s*=\s*"([^"]+)"', body):
            mt = re.match(r'L([\w/$]+);([\w<>$]+)', tgt)
            if mt:
                owner = mt.group(1).replace('/', '.')
                if source(owner) is None:
                    if owner.startswith('net.minecraft') or owner.startswith('com.mojang'):
                        problems.append(f'{f}: @At owner missing: {owner}')
                    continue
                r = has_member(owner, mt.group(2))
                if r is False:
                    problems.append(f'{f}: @At "{mt.group(2)}" not in {owner.rsplit(".", 1)[-1]}')
    for m in re.finditer(r'@(Accessor|Invoker)\(\s*"([^"]+)"', t):
        for c in targets:
            if has_member(c, m.group(2)) is False:
                problems.append(f'{f}: {m.group(1)} "{m.group(2)}" not in {c.rsplit(".", 1)[-1]}')
    for m in re.finditer(r'@(Accessor|Invoker)\s*\n\s*\S+\s+(?:\S+\s+)?(get|set|call|invoke|is)(\w+)\(', t):
        name = m.group(3)[0].lower() + m.group(3)[1:]
        for c in targets:
            if has_member(c, name) is False:
                problems.append(f'{f}: {m.group(1)} implied "{name}" not in {c.rsplit(".", 1)[-1]}')
    for m in re.finditer(r'@Shadow\s+(?:@\w+\s+)*(?:private|protected|public)?\s*(?:final\s+)?(?:abstract\s+)?[\w<>\[\].,? ]+?\s(\w+)\s*[;(]', t):
        for c in targets:
            if has_member(c, m.group(1)) is False:
                problems.append(f'{f}: @Shadow "{m.group(1)}" not in {c.rsplit(".", 1)[-1]}')
print('\n'.join(problems) if problems else 'all mixin targets found')

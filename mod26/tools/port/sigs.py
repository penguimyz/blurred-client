"""For each injector: the handler's parameters next to the target method's
parameters in 26.2 (all overloads), so mismatches can be spotted by eye."""
import os
import re

MIXINS = 'C:/Users/Massi/port26/proj/src/main/java/dev/blurredclient/mod/mixin'
SRC = 'C:/Users/Massi/port26/mc262'


def source(cls):
    p = os.path.join(SRC, cls.replace('.', '/').split('$')[0] + '.java')
    return open(p, encoding='utf-8').read() if os.path.exists(p) else None


def params_of(src, name):
    out = []
    for m in re.finditer(r'(?:public|private|protected|static|final|\s)+[\w<>\[\]?,. @]+?\s' + re.escape(name) + r'\s*\(([^)]*)\)\s*(?:throws [\w., ]+)?\{', src):
        ps = re.sub(r'final |@Nullable |\s+', ' ', m.group(1)).strip()
        out.append(','.join(p.strip().rsplit(' ', 1)[0].split('.')[-1] for p in ps.split(',') if p.strip()))
    return out


for f in sorted(os.listdir(MIXINS)):
    t = open(os.path.join(MIXINS, f), encoding='utf-8').read()
    imp = {i.rsplit('.', 1)[1]: i for i in re.findall(r'^import ([\w.]+);', t, re.M)}
    tm = re.search(r'@Mixin\(\s*(?:value\s*=\s*)?\{?([^)}]*)\}?', t)
    if not tm:
        continue
    targets = [imp.get(c.split('.')[0], c) for c in re.findall(r'([\w.$]+)\.class', tm.group(1))]
    for m in re.finditer(r'@(Inject|Redirect|ModifyVariable|ModifyArg|ModifyReturnValue|WrapOperation|ModifyExpressionValue|ModifyConstant)\((.*?)\)\s*\n\s*(?:private|public|protected)\s+(?:static\s+)?[\w<>\[\]?]+\s+([\w$]+)\s*\(([^)]*)\)', t, re.S):
        mm = re.search(r'method\s*=\s*(\{[^}]*\}|"[^"]*")', m.group(2))
        if not mm:
            continue
        handler = re.sub(r'@\w+(\([^)]*\))?\s*', '', m.group(4))
        handler = ','.join(p.strip().rsplit(' ', 1)[0].split('.')[-1] for p in handler.split(',') if p.strip())
        for name in re.findall(r'"([^"]+)"', mm.group(1)):
            n = name.split('(')[0]
            for c in targets:
                s = source(c)
                if s is None:
                    continue
                print(f'{f[:-5]} @{m.group(1)} {n} [{name[len(n):] or "-"}]\n    handler: ({handler})\n    target : {params_of(s, n)}')

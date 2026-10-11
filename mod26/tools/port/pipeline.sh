#!/usr/bin/env bash
# Regenerates the 26.2 port's Java source from the 1.21.x mod:
#   yarn source -> (KitComponent rename) -> Mojang names (Loom migrateMappings)
#   -> flatten Stonecutter branches to 1.21.11 -> bulk 26.2 renames.
# Hand fixes live in port26/patches and are applied last.
set -e
W=C:/Users/Massi/port26
MOD=C:/Users/Massi/Downloads/blurred-client-main/blurred-client-main/mod

rm -rf $W/yarn $W/java
cp -r $MOD/src/main/java $W/yarn
# Our UI kit's `Component` would clash with Minecraft's text class under Mojang names.
python - <<'EOF'
import os, re
root = 'C:/Users/Massi/port26/yarn'
for d, _, fs in os.walk(root):
    for f in fs:
        p = os.path.join(d, f)
        s = open(p, encoding='utf-8').read()
        n = re.sub(r'\bComponent\b', 'KitComponent', s)
        if n != s:
            open(p, 'w', encoding='utf-8', newline='\n').write(n)
os.rename(os.path.join(root, 'dev/blurredclient/mod/ui/kit/Component.java'),
          os.path.join(root, 'dev/blurredclient/mod/ui/kit/KitComponent.java'))
EOF
(cd $MOD && ./gradlew :1.21.11:migrateMappings --mappings "net.minecraft:mappings:1.21.11" --input "$W/yarn" --output "$W/java" --console=plain -q)
# migrateMappings reorders imports and breaks this one Stonecutter block.
python - <<'EOF'
p = 'C:/Users/Massi/port26/java/dev/blurredclient/mod/mixin/PlayerSkinTexturesMixin.java'
s = open(p, encoding='utf-8').read()
s = s.replace('//?} else {\n/*import net.minecraft.client.util.SkinTextures;\n *///?}\n', '', 1)
open(p, 'w', encoding='utf-8', newline='\n').write(s)
EOF
python $W/resolve_sc.py $W/java
python $W/renames.py $W/java
python $W/renames2.py $W/java
rm -rf $W/proj/src/main/java
cp -r $W/java $W/proj/src/main/java
echo pipeline done

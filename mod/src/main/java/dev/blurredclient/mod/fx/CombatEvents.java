package dev.blurredclient.mod.fx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * What's happening in a fight, as seen by your own client: you swung at
 * something, something died, someone used a totem. Effects, sounds and
 * markers listen here instead of each hooking the game themselves.
 *
 * <p>Deaths and totem pops come from the entity status the server already
 * sends to every nearby player (the same message that plays the death
 * animation or the totem particles), so nothing here is hidden information.
 * A "kill" is a death of something you hit in the last few seconds.
 */
public final class CombatEvents {
    private CombatEvents() {}

    /** Something died (or you killed it). */
    public record Death(Entity entity, Vec3d pos, boolean byYou) {}

    private static final List<Consumer<Entity>> ATTACK = new ArrayList<>();
    private static final List<Consumer<Death>> DEATH = new ArrayList<>();
    private static final List<Consumer<Entity>> TOTEM = new ArrayList<>();

    /** Entity id -> when you last hit it (nanoTime). */
    private static final Map<Integer, Long> RECENT = new HashMap<>();
    /** Entity id -> totems it has used since you've been watching. */
    private static final Map<Integer, Integer> POPS = new HashMap<>();
    private static int combo;
    private static long lastHit;

    public static void onAttack(Consumer<Entity> listener) {
        ATTACK.add(listener);
    }

    public static void onDeath(Consumer<Death> listener) {
        DEATH.add(listener);
    }

    public static void onTotem(Consumer<Entity> listener) {
        TOTEM.add(listener);
    }

    /** Hits in a row, each within 1.5 s of the last. */
    public static int combo() {
        return combo;
    }

    public static int pops(Entity e) {
        return POPS.getOrDefault(e.getId(), 0);
    }

    // ---- fed by the game ----

    public static void attacked(Entity target) {
        long now = System.nanoTime();
        combo = now - lastHit < 1_500_000_000L ? combo + 1 : 1;
        lastHit = now;
        RECENT.put(target.getId(), now);
        for (Consumer<Entity> l : ATTACK) {
            l.accept(target);
        }
    }

    /** Called from the entity status handler: 3 is the death animation, 35 a totem. */
    public static void status(LivingEntity e, byte status) {
        if (status == 3) {
            Long hit = RECENT.remove(e.getId());
            boolean byYou = hit != null && System.nanoTime() - hit < 4_000_000_000L
                    && e != MinecraftClient.getInstance().player;
            Death d = new Death(e, e.getBoundingBox().getCenter(), byYou);
            for (Consumer<Death> l : DEATH) {
                l.accept(d);
            }
            POPS.remove(e.getId());
        } else if (status == 35) {
            POPS.merge(e.getId(), 1, Integer::sum);
            for (Consumer<Entity> l : TOTEM) {
                l.accept(e);
            }
        }
    }

    /** Forget everything when you leave a world. */
    public static void reset() {
        RECENT.clear();
        POPS.clear();
        combo = 0;
    }
}

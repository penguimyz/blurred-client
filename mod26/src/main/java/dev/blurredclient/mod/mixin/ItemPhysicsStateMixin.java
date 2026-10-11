package dev.blurredclient.mod.mixin;

import dev.blurredclient.mod.fx.ItemPhysicsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Item physics: the pose on the item's render state (1.21.4+). On 1.21.1 the
// renderer sees the entity itself, so this targets ItemEntity and adds
// nothing that's used.
@Mixin(net.minecraft.client.renderer.entity.state.ItemEntityRenderState.class)
public class ItemPhysicsStateMixin implements ItemPhysicsState {
    @Unique
    private float blurred$angle;
    @Unique
    private float blurred$settle;
    @Unique
    private boolean blurred$flat;

    @Override
    public float blurred$angle() {
        return blurred$angle;
    }

    @Override
    public float blurred$settle() {
        return blurred$settle;
    }

    @Override
    public boolean blurred$flat() {
        return blurred$flat;
    }

    @Override
    public void blurred$setPose(float angle, float settle, boolean flat) {
        this.blurred$angle = angle;
        this.blurred$settle = settle;
        this.blurred$flat = flat;
    }
}

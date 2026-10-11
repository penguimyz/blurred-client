package dev.blurredclient.mod.fx;

/**
 * Item physics pose carried on the item's render state (1.21.4+), since the
 * renderer no longer sees the entity itself. Filled when the state is
 * updated from the entity, read when it's drawn.
 */
public interface ItemPhysicsState {
    float blurred$angle();

    float blurred$settle();

    boolean blurred$flat();

    void blurred$setPose(float angle, float settle, boolean flat);
}

package dev.blurredclient.mod.bridge;

/**
 * One cape in your launcher library, as relayed over the bridge.
 *
 * <p>Carries no image data — the in-game screen previews the *worn* cape using
 * the texture already registered for rendering, so pushing a copy of every
 * cape's pixels just to draw a list would be waste.
 */
public record CapeEntry(String id, String name) {}

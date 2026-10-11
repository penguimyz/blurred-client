package dev.blurredclient.mod.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets Blurred's motion blur pass get fresh numbers every frame. Post-shader
 * settings are normally fixed when the effect loads; this marks the
 * uniform buffers as writable and, just before a pass with a
 * "BlurredMotion" block runs, writes this frame's reprojection into it.
 * Only on 1.21.11 (the post pipeline differs before).
 */
@Mixin(PostPass.class)
public class PostEffectPassMixin {
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private java.util.Map<String, com.mojang.blaze3d.buffers.GpuBuffer> customUniforms;

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "<init>", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
            index = 1)
    private int blurred$writable(int usage) {
        return usage | com.mojang.blaze3d.buffers.GpuBuffer.USAGE_COPY_DST;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "addToFrame", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void blurred$motion(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        var buffer = customUniforms.get(dev.blurredclient.mod.fx.MotionBlurState.BLOCK);
        if (buffer == null || buffer.isClosed()) {
            return;
        }
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var data = com.mojang.blaze3d.buffers.Std140Builder.onStack(stack, 80)
                    .putMat4f(dev.blurredclient.mod.fx.MotionBlurState.reproject())
                    .putVec4(dev.blurredclient.mod.fx.MotionBlurState.strength(), 0.06f, 0f, 0f)
                    .get();
            com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
    }
}

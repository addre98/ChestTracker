package red.jackf.chesttracker.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import red.jackf.chesttracker.api.providers.ProviderUtils;

@Debug(export = true)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin extends ClientCommonPacketListenerImpl {
    protected ClientPacketListenerMixin(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
        throw new AssertionError("mixin apply failed");
    }

    @Unique
    private ResourceKey<Level> ct$oldDimension;

    @Unique
    private ResourceKey<Level> ct$newDimension;

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void ct$captureOldDimension(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (this.minecraft.player != null) {
            this.ct$oldDimension = this.minecraft.player.level().dimension();
            this.ct$newDimension = packet.commonPlayerSpawnInfo().dimension();
        }
    }

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void ct$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (this.ct$oldDimension != null && this.ct$newDimension != null) {
            ProviderUtils.getCurrentProvider().ifPresent(provider -> {
                provider.onRespawn(this.ct$oldDimension, this.ct$newDimension);
            });
        }
    }
}

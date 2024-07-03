package me.Thelnfamous1.randomcraft.mixin;

import me.Thelnfamous1.randomcraft.AlternateRecipeHolder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin extends Slot {

    @Shadow @Final private Player player;

    public ResultSlotMixin(Container pContainer, int pSlot, int pX, int pY) {
        super(pContainer, pSlot, pX, pY);
    }

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeHolder;awardUsedRecipes(Lnet/minecraft/world/entity/player/Player;)V", shift = At.Shift.AFTER))
    private void randomcraft$handleAwardUsedRecipes(ItemStack pStack, CallbackInfo ci){
        if(this.container instanceof AlternateRecipeHolder alternateRecipeHolder){
            if(alternateRecipeHolder.randomcraft$getAlternateRecipeUsed() != null){
                float pitch = 1.0F;
                // pitch randomly between an octave below or above base note
                int semitoneCount = this.player.getRandom().nextIntBetweenInclusive(-12, 12);
                for (int i = 0; i < semitoneCount; i++)
                {
                    pitch *= 1.059463F; // 1.059463F is the value of a semitone
                }
                this.player.playNotifySound(SoundEvents.NOTE_BLOCK_DIDGERIDOO, SoundSource.BLOCKS, 1.0F, pitch);
            }
            alternateRecipeHolder.randomcraft$setAlternateRecipeUsed(null);
        }
    }
}

package me.Thelnfamous1.randomcraft.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.BooleanValue.class)
public interface GameRules$BooleanValueAccessor {

    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> randomcraft$callCreate(boolean defaultValue){
        throw new AssertionError("Mixin not applied!");
    }
}

package me.Thelnfamous1.randomcraft.mixin;

import me.Thelnfamous1.randomcraft.AlternateRecipeHolder;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ResultContainer.class)
public abstract class ResultContainerMixin implements AlternateRecipeHolder {
    @Unique
    @Nullable
    private Recipe<?> randomcraft$alternateRecipeUsed;

    @Nullable
    @Override
    public Recipe<?> randomcraft$getAlternateRecipeUsed() {
        return this.randomcraft$alternateRecipeUsed;
    }

    @Override
    public void randomcraft$setAlternateRecipeUsed(@Nullable Recipe<?> pRecipe) {
        this.randomcraft$alternateRecipeUsed = pRecipe;
    }
}

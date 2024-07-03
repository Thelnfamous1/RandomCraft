package me.Thelnfamous1.randomcraft;


import net.minecraft.world.item.crafting.Recipe;

import javax.annotation.Nullable;

public interface AlternateRecipeHolder {
    void randomcraft$setAlternateRecipeUsed(@Nullable Recipe<?> pRecipe);

    @Nullable
    Recipe<?> randomcraft$getAlternateRecipeUsed();
}

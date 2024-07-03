package me.Thelnfamous1.randomcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.Thelnfamous1.randomcraft.RandomCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/Container;)Lnet/minecraft/world/item/ItemStack;") )
    private static ItemStack getRandomCraftingRecipe(CraftingRecipe original, Container argContainer, Operation<ItemStack> operation,
                                                     AbstractContainerMenu pMenu,
                                                     Level pLevel,
                                                     Player pPlayer,
                                                     CraftingContainer pContainer,
                                                     ResultContainer pResult){
        if(pLevel.getGameRules().getBoolean(RandomCraft.RULE_RANDOM_CRAFTING.get())){
            ResourceLocation recipePairing = RandomCraft.getRandomizedRecipePairing(original.getId());
            if(recipePairing != null){
                return pLevel.getServer().getRecipeManager().byKey(recipePairing)
                        .map(r -> {
                            try{
                                return operation.call(r, argContainer);
                            } catch (ClassCastException e){
                                return r.getResultItem().copy();
                            }
                        })
                        .orElse(operation.call(original, argContainer));
            }
        }
        return operation.call(original, argContainer);
    }
}

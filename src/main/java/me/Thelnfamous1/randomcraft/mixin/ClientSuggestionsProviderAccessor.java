package me.Thelnfamous1.randomcraft.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientSuggestionProvider.class)
public interface ClientSuggestionsProviderAccessor {

    @Accessor("connection")
    ClientPacketListener randomcraft$getConnection();
}

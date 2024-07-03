package me.Thelnfamous1.randomcraft.network;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import me.Thelnfamous1.randomcraft.RandomCraft;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class S2CRandomizedRecipesSync {
    private final Either<Map<ResourceLocation, ResourceLocation>, Either<Pair<ResourceLocation, ResourceLocation>, ResourceLocation>> toUpdate;
    private final SyncType syncType;

    public S2CRandomizedRecipesSync(Map<ResourceLocation, ResourceLocation> pPairings) {
        this.syncType = SyncType.REPLACE_ALL;
        this.toUpdate = Either.left(Util.make(new LinkedHashMap<>(), map -> map.putAll(pPairings)));
    }

    public S2CRandomizedRecipesSync(Pair<ResourceLocation, ResourceLocation> pPair) {
        this.syncType = SyncType.PUT_PAIRING;
        this.toUpdate = Either.right(Either.left(pPair));
    }

    public S2CRandomizedRecipesSync(ResourceLocation pId) {
        this.syncType = SyncType.REMOVE_PAIRING;
        this.toUpdate = Either.right(Either.right(pId));
    }

    public S2CRandomizedRecipesSync(FriendlyByteBuf pBuffer) {
        this.syncType = pBuffer.readEnum(SyncType.class);
        switch (this.syncType){
            case PUT_PAIRING -> this.toUpdate = Either.right(Either.left(Pair.of(pBuffer.readResourceLocation(), pBuffer.readResourceLocation())));
            case REMOVE_PAIRING -> this.toUpdate = Either.right(Either.right(pBuffer.readResourceLocation()));
            default -> this.toUpdate = Either.left(pBuffer.readMap(LinkedHashMap::new, FriendlyByteBuf::readResourceLocation, FriendlyByteBuf::readResourceLocation));
        }
    }

    public void write(FriendlyByteBuf pBuffer) {
        pBuffer.writeEnum(this.syncType);
        switch (this.syncType){
            case REPLACE_ALL -> this.toUpdate.ifLeft(map -> pBuffer.writeMap(map, FriendlyByteBuf::writeResourceLocation, FriendlyByteBuf::writeResourceLocation));
            case PUT_PAIRING -> this.toUpdate.ifRight(either -> either.ifLeft(pair -> {
                pBuffer.writeResourceLocation(pair.getFirst());
                pBuffer.writeResourceLocation(pair.getSecond());
            }));
            case REMOVE_PAIRING -> this.toUpdate.ifRight(either -> either.ifRight(pBuffer::writeResourceLocation));
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            switch (this.syncType){
                case REPLACE_ALL -> this.toUpdate.ifLeft(RandomCraft::replaceRecipePairs);
                case PUT_PAIRING -> this.toUpdate.ifRight(either -> either.ifLeft(RandomCraft::putRecipePairing));
                case REMOVE_PAIRING -> this.toUpdate.ifRight(either -> either.ifRight(RandomCraft::removeRecipePairing));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public enum SyncType {
        REPLACE_ALL,
        PUT_PAIRING,
        REMOVE_PAIRING
    }

}

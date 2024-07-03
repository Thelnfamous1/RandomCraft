package me.Thelnfamous1.randomcraft;

import com.google.common.base.Suppliers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import me.Thelnfamous1.randomcraft.mixin.ClientSuggestionsProviderAccessor;
import me.Thelnfamous1.randomcraft.mixin.GameRules$BooleanValueAccessor;
import me.Thelnfamous1.randomcraft.mixin.RecipeManagerAccessor;
import me.Thelnfamous1.randomcraft.network.S2CRandomizedRecipesSync;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Mod(RandomCraft.MODID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class RandomCraft {
    public static final String MODID = "randomcraft";
    public static final String RANDOMIZE_SUCCESS = "commands." + MODID + ".randomize.success";
    public static final String QUERY_RECIPE_SUCCESS = "commands." + MODID + ".query.recipe.success";
    private static final String QUERY_RECIPE_FAILURE = "commands." + MODID + ".query.recipe.failure";
    private static final String PAIR_RECIPE_SUCCESS = "commands." + MODID + ".pair.recipe.success";
    private static final String REMOVE_RECIPE_SUCCESS = "commands." + MODID + ".remove.recipe.success";
    private static final String REMOVE_RECIPE_FAILURE = "commands." + MODID + ".remove.recipe.failure";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Supplier<GameRules.Key<GameRules.BooleanValue>> RULE_RANDOM_CRAFTING = Suppliers.memoize(() -> GameRules.register(MODID + "." + "do_random_crafting", GameRules.Category.PLAYER, GameRules$BooleanValueAccessor.randomcraft$callCreate(false)));
    public static final String PROTOCOL_VERSION = "1.0";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(MODID, "sync_channel"), () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static Map<ResourceLocation, ResourceLocation> recipePairs = new LinkedHashMap<>();
    public static final Supplier<SuggestionProvider<CommandSourceStack>> SUGGEST_CRAFTING_RECIPES = Suppliers.memoize(() -> SuggestionProviders.register(
            new ResourceLocation(MODID, "crafting_recipes"),
            (context, builder) -> {
                Stream<ResourceLocation> stream;
                if(context.getSource() instanceof CommandSourceStack sourceStack){
                    stream = streamCraftingRecipes(sourceStack.getUnsidedLevel().getRecipeManager());
                } else if(FMLEnvironment.dist == Dist.CLIENT && context.getSource() instanceof ClientSuggestionProvider provider){
                    stream = streamCraftingRecipes(((ClientSuggestionsProviderAccessor)provider).randomcraft$getConnection().getRecipeManager());
                } else{
                    stream = Stream.of();
                }
                return SharedSuggestionProvider.suggestResource(stream, builder);
            }));

    public RandomCraft() {
        MinecraftForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if(event.getPlayer() != null){
                // dont randomize for a single player
                updateRandomRecipesFor(event.getPlayer());
            } else {
                randomizeRecipes(event.getPlayerList().getServer(), true);
                event.getPlayerList().getPlayers().forEach(RandomCraft::updateRandomRecipesFor);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registerCommands(event.getDispatcher()));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(MODID)
                .requires((stack) -> stack.hasPermission(2))
                .then(Commands.literal("randomize")
                        .executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            randomizeForAllPlayers(server);
                            ctx.getSource().sendSuccess(Component.translatable(RANDOMIZE_SUCCESS), false);
                            return 0;
                        }))
                .then(Commands.literal("query")
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                .suggests(SUGGEST_CRAFTING_RECIPES.get())
                                .executes(ctx -> {
                                    Recipe<?> recipe = ResourceLocationArgument.getRecipe(ctx, "recipe");
                                    ResourceLocation pairing = recipePairs.get(recipe.getId());
                                    if(pairing != null){
                                        ctx.getSource().sendSuccess(Component.translatable(QUERY_RECIPE_SUCCESS, recipe.getId(), pairing), true);
                                    } else{
                                        ctx.getSource().sendFailure(Component.translatable(QUERY_RECIPE_FAILURE, recipe.getId()));
                                    }
                                    return 0;
                                })))
                .then(Commands.literal("pair")
                        .then(Commands.argument("key", ResourceLocationArgument.id())
                                .suggests(SUGGEST_CRAFTING_RECIPES.get())
                                .then(Commands.argument("value", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_CRAFTING_RECIPES.get())
                                        .executes(ctx -> {
                                            Recipe<?> key = ResourceLocationArgument.getRecipe(ctx, "key");
                                            Recipe<?> value = ResourceLocationArgument.getRecipe(ctx, "value");
                                            putPairingForAllPlayers(ctx, key, value);
                                            ctx.getSource().sendSuccess(Component.translatable(PAIR_RECIPE_SUCCESS, key.getId(), value.getId()), true);
                                            return 0;
                                        }))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("recipe", ResourceLocationArgument.id())
                                .suggests(SUGGEST_CRAFTING_RECIPES.get())
                                .executes(ctx -> {
                                    Recipe<?> recipe = ResourceLocationArgument.getRecipe(ctx, "recipe");
                                    ResourceLocation previousValue = removePairingForAllPlayers(ctx, recipe);
                                    if(previousValue != null){
                                        ctx.getSource().sendSuccess(Component.translatable(REMOVE_RECIPE_SUCCESS, recipe.getId(), previousValue), true);
                                    } else{
                                        ctx.getSource().sendFailure(Component.translatable(REMOVE_RECIPE_FAILURE, recipe.getId()));
                                    }
                                    return 0;
                                        }))));
    }

    @Nullable
    private static ResourceLocation removePairingForAllPlayers(CommandContext<CommandSourceStack> ctx, Recipe<?> recipe) {
        ResourceLocation previousValue = recipePairs.remove(recipe.getId());
        if(previousValue != null){
            ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CRandomizedRecipesSync(recipe.getId())));
        }
        return previousValue;
    }

    private static void putPairingForAllPlayers(CommandContext<CommandSourceStack> ctx, Recipe<?> key, Recipe<?> value) {
        recipePairs.put(key.getId(), value.getId());
        ctx.getSource().getServer().getPlayerList().getPlayers().forEach(player -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CRandomizedRecipesSync(Pair.of(key.getId(), value.getId()))));
    }

    private static void randomizeForAllPlayers(MinecraftServer server) {
        randomizeRecipes(server, false);
        server.getPlayerList().getPlayers().forEach(RandomCraft::updateRandomRecipesFor);
    }

    private static void randomizeRecipes(MinecraftServer server, boolean reload) {
        recipePairs = new LinkedHashMap<>();
        List<ResourceLocation> originalRecipes = streamCraftingRecipes(server.getRecipeManager())
                .toList();
        List<ResourceLocation> randomizedRecipes = Util.make(new ArrayList<>(originalRecipes), Collections::shuffle);

        for(int i = 0; i < originalRecipes.size(); i++){
            recipePairs.put(originalRecipes.get(i), randomizedRecipes.get(i));
        }
        LOGGER.info("Randomized {} recipes{}!", recipePairs.size(), reload ? "from resource reload" : "");
        recipePairs.forEach((k, v) -> LOGGER.info("{} maps to {}", k.toString(), v.toString()));
    }

    private static Stream<ResourceLocation> streamCraftingRecipes(RecipeManager recipeManager) {
        return ((RecipeManagerAccessor) recipeManager).randomcraft$callByType(RecipeType.CRAFTING).entrySet()
                .stream()
                .filter((entry) -> !entry.getValue().isSpecial())
                .map(Map.Entry::getKey);
    }

    private static void updateRandomRecipesFor(ServerPlayer player) {
        //LOGGER.info("Sending random recipe pairings to {}", player);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CRandomizedRecipesSync(recipePairs));
    }

    private static void registerNetwork(){
        int messageIndex = 0;
        CHANNEL.registerMessage(messageIndex++, S2CRandomizedRecipesSync.class, S2CRandomizedRecipesSync::write, S2CRandomizedRecipesSync::new, S2CRandomizedRecipesSync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(RULE_RANDOM_CRAFTING);
        event.enqueueWork(SUGGEST_CRAFTING_RECIPES);
        event.enqueueWork(RandomCraft::registerNetwork);
    }

    @SubscribeEvent
    public static void onDataGen(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeClient(), new LanguageProvider(event.getGenerator(), MODID, "en_us") {
            @Override
            protected void addTranslations() {
                this.add(RULE_RANDOM_CRAFTING.get().getDescriptionId(), "Random Crafting");
                this.add(RULE_RANDOM_CRAFTING.get().getDescriptionId() + ".description", "If enabled, players will craft randomized items");
                this.add(RANDOMIZE_SUCCESS, "Randomized crafting recipes!");
                this.add(QUERY_RECIPE_SUCCESS, "Recipe %s is paired to recipe %s");
                this.add(QUERY_RECIPE_FAILURE, "Could not find recipe pairing for recipe %s!");
                this.add(PAIR_RECIPE_SUCCESS, "Paired recipe %s to recipe %s!");
                this.add(REMOVE_RECIPE_SUCCESS, "Recipe %s is no longer paired to recipe %s!");
                this.add(REMOVE_RECIPE_FAILURE, "Recipe %s already has no pairing!");
            }
        });
    }

    public static ResourceLocation getRandomizedRecipePairing(ResourceLocation location) {
        return recipePairs.get(location);
    }

    public static void replaceRecipePairs(Map<ResourceLocation, ResourceLocation> pRecipePairs) {
        recipePairs = pRecipePairs;
        //LOGGER.info("Random recipe pairs replaced! Received {} total", recipePairs.size());
    }

    public static void putRecipePairing(Pair<ResourceLocation, ResourceLocation> pair) {
        recipePairs.put(pair.getFirst(), pair.getSecond());
        //LOGGER.info("Recipe {} is now paired to recipe {}", pair.getFirst(), pair.getSecond());
    }

    public static void removeRecipePairing(ResourceLocation id) {
        recipePairs.remove(id);
        //LOGGER.info("Recipe {} is no longer paired", id);
    }
}

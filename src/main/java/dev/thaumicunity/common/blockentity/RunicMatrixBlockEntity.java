package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.recipe.InfusionInput;
import dev.thaumicunity.common.recipe.InfusionRecipe;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.common.registry.TURecipes;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.registry.TUTags;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.spell.SpellFx;
import dev.thaumicunity.core.infusion.StabilityModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Runic matrix: drives an infusion. The central pedestal sits two blocks below the matrix; component pedestals stand
 * on the same level as the central one within {@link TUConfig#INFUSION_RANGE}. Essentia is drawn from warded jars in
 * range, one point every few ticks, then each component is absorbed, then the result appears on the central pedestal.
 * Every step may trigger an instability event depending on {@link StabilityModel}.
 */
public class RunicMatrixBlockEntity extends SyncedBlockEntity {
    private @Nullable Identifier recipeId;
    private AspectList remaining = AspectList.EMPTY;
    private final List<BlockPos> pendingComponents = new ArrayList<>();
    private double eventChance;
    private int stepTimer;
    private @Nullable UUID crafter;
    private boolean active;

    public RunicMatrixBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.RUNIC_MATRIX.get(), pos, state);
    }

    public boolean isActive() {
        return active;
    }

    /** Tries to start an infusion for the player; reports the reason when it cannot. */
    public void tryStart(ServerLevel level, ServerPlayer player) {
        if (active) {
            player.sendSystemMessage(Component.translatable("message.thaumicunity.infusion.busy"));
            return;
        }
        BlockPos centralPos = worldPosition.below(2);
        if (!(level.getBlockEntity(centralPos) instanceof ArcanePedestalBlockEntity central) || central.item().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.thaumicunity.infusion.no_central"));
            return;
        }
        List<ArcanePedestalBlockEntity> pedestals = findComponentPedestals(level, centralPos);
        List<ItemStack> components = pedestals.stream().map(ArcanePedestalBlockEntity::item).filter(s -> !s.isEmpty()).toList();
        Optional<RecipeHolder<InfusionRecipe>> match = level.recipeAccess()
                .getRecipeFor(TURecipes.INFUSION.get(), new InfusionInput(central.item(), components), level);
        if (match.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.thaumicunity.infusion.no_recipe"));
            return;
        }
        InfusionRecipe recipe = match.get().value();
        if (!ResearchManager.knows(player, recipe.research())) {
            player.sendSystemMessage(Component.translatable("message.thaumicunity.infusion.unknown"));
            return;
        }
        StabilityModel.Result stability = evaluateStability(level, centralPos, pedestals, recipe);
        recipeId = match.get().id().identifier();
        remaining = recipe.essentia();
        pendingComponents.clear();
        for (ArcanePedestalBlockEntity p : pedestals) {
            if (!p.item().isEmpty()) {
                pendingComponents.add(p.getBlockPos());
            }
        }
        eventChance = stability.eventChance() * TUConfig.INFUSION_INSTABILITY_SCALE.get();
        crafter = player.getUUID();
        active = true;
        stepTimer = 0;
        level.playSound(null, worldPosition, TUSounds.INFUSION.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        player.sendSystemMessage(Component.translatable("message.thaumicunity.infusion.started",
                String.format(java.util.Locale.ROOT, "%.1f", stability.stability())));
        markForSync();
    }

    private List<ArcanePedestalBlockEntity> findComponentPedestals(Level level, BlockPos centralPos) {
        int range = TUConfig.INFUSION_RANGE.get();
        List<ArcanePedestalBlockEntity> result = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centralPos.offset(-range, 0, -range), centralPos.offset(range, 0, range))) {
            if (!pos.equals(centralPos) && level.getBlockEntity(pos) instanceof ArcanePedestalBlockEntity pedestal) {
                result.add(pedestal);
            }
        }
        return result;
    }

    private StabilityModel.Result evaluateStability(Level level, BlockPos centralPos, List<ArcanePedestalBlockEntity> pedestals,
                                                    InfusionRecipe recipe) {
        List<StabilityModel.Offset> filled = new ArrayList<>();
        List<StabilityModel.Offset> empty = new ArrayList<>();
        for (ArcanePedestalBlockEntity p : pedestals) {
            BlockPos d = p.getBlockPos().subtract(centralPos);
            (p.item().isEmpty() ? empty : filled).add(new StabilityModel.Offset(d.getX(), d.getZ()));
        }
        double stabilizers = 0;
        int range = TUConfig.INFUSION_RANGE.get();
        for (BlockPos pos : BlockPos.betweenClosed(centralPos.offset(-range, -1, -range), centralPos.offset(range, 2, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(TUTags.Blocks.INFUSION_STABILIZERS)) {
                Float value = state.getBlock().builtInRegistryHolder().getData(TUDataMaps.INFUSION_STABILIZER);
                stabilizers += value != null ? value : 0.25;
            }
        }
        double flux = AuraManager.auraAt(level, worldPosition).state().fluxPressure();
        return StabilityModel.evaluate(new StabilityModel.Input(filled, empty, stabilizers, 0, 0, recipe.instability(),
                Double.isFinite(flux) ? flux : 4.0));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RunicMatrixBlockEntity matrix) {
        if (!matrix.active || !(level instanceof ServerLevel server)) {
            return;
        }
        if (++matrix.stepTimer < 5) {
            return;
        }
        matrix.stepTimer = 0;
        if (server.getRandom().nextDouble() < matrix.eventChance) {
            matrix.instabilityEvent(server);
        }
        if (!matrix.remaining.isEmpty()) {
            matrix.drawEssentia(server);
            return;
        }
        if (!matrix.pendingComponents.isEmpty()) {
            matrix.absorbComponent(server);
            return;
        }
        matrix.finish(server);
    }

    private void drawEssentia(ServerLevel level) {
        int range = TUConfig.INFUSION_RANGE.get();
        for (Identifier aspect : List.copyOf(remaining.asMap().keySet())) {
            for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-range, -4, -range), worldPosition.offset(range, 2, range))) {
                if (level.getBlockEntity(pos) instanceof WardedJarBlockEntity jar && jar.take(aspect, 1) > 0) {
                    remaining = remaining.subtract(AspectList.of(aspect, 1));
                    SpellFx.line(level, Vec3.atCenterOf(pos), Vec3.atCenterOf(worldPosition), Aspects.color(level.registryAccess(), aspect));
                    markForSync();
                    return;
                }
            }
        }
        // Nothing found for any remaining aspect: the infusion stalls and grows unstable.
        eventChance = Math.min(0.5, eventChance + 0.01);
    }

    private void absorbComponent(ServerLevel level) {
        BlockPos pos = pendingComponents.remove(0);
        if (level.getBlockEntity(pos) instanceof ArcanePedestalBlockEntity pedestal && !pedestal.item().isEmpty()) {
            SpellFx.line(level, Vec3.atCenterOf(pos).add(0, 0.7, 0), Vec3.atCenterOf(worldPosition.below(2)).add(0, 0.7, 0), 0xC9A7FF);
            pedestal.setItem(ItemStack.EMPTY);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.2f);
        } else {
            // A component was removed mid-infusion: severe instability.
            instabilityEvent(level);
            instabilityEvent(level);
        }
    }

    private void finish(ServerLevel level) {
        active = false;
        BlockPos centralPos = worldPosition.below(2);
        Optional<RecipeHolder<?>> holder = recipeId == null ? Optional.empty()
                : level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, recipeId));
        if (holder.isPresent() && holder.get().value() instanceof InfusionRecipe recipe
                && level.getBlockEntity(centralPos) instanceof ArcanePedestalBlockEntity central) {
            ItemStack result = recipe.assemble(new InfusionInput(central.item(), List.of()));
            central.setItem(result);
            level.playSound(null, centralPos, TUSounds.CRAFT_SUCCESS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            level.sendParticles(SpellFx.spark(0xE6C9FF), centralPos.getX() + 0.5, centralPos.getY() + 1.2, centralPos.getZ() + 0.5,
                    60, 0.4, 0.6, 0.4, 0.15);
            if (crafter != null && level.getPlayerByUUID(crafter) instanceof ServerPlayer player) {
                ResearchManager.onCrafted(player, result);
            }
        }
        recipeId = null;
        remaining = AspectList.EMPTY;
        pendingComponents.clear();
        markForSync();
    }

    /** One instability event: zaps, item ejection, flux, warp or a harmless blast. */
    private void instabilityEvent(ServerLevel level) {
        int roll = level.getRandom().nextInt(5);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        Player victim = level.getNearestPlayer(c.x, c.y, c.z, 10, false);
        switch (roll) {
            case 0 -> {
                if (victim != null) {
                    victim.hurtServer(level, level.damageSources().magic(), 4.0f);
                    SpellFx.line(level, c, victim.position().add(0, 1, 0), 0xB15CFF);
                    level.playSound(null, worldPosition, TUSounds.ZAP.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }
            case 1 -> {
                if (!pendingComponents.isEmpty()
                        && level.getBlockEntity(pendingComponents.get(0)) instanceof ArcanePedestalBlockEntity pedestal
                        && !pedestal.item().isEmpty()) {
                    BlockPos p = pedestal.getBlockPos();
                    level.addFreshEntity(new ItemEntity(level, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, pedestal.item(),
                            (level.getRandom().nextDouble() - 0.5) * 0.4, 0.4, (level.getRandom().nextDouble() - 0.5) * 0.4));
                    pedestal.setItem(ItemStack.EMPTY);
                }
            }
            case 2 -> AuraManager.addFlux(level, worldPosition, 15);
            case 3 -> {
                if (victim instanceof ServerPlayer player && TUConfig.WARP_ENABLED.get()) {
                    player.getData(TUAttachments.KNOWLEDGE).addWarp(0, 0, 1 + level.getRandom().nextInt(3));
                    player.syncData(TUAttachments.KNOWLEDGE);
                    player.sendSystemMessage(Component.translatable("message.thaumicunity.infusion.warp"));
                }
            }
            default -> level.explode(null, c.x, c.y - 1, c.z, 1.2f, Level.ExplosionInteraction.NONE);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.05);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        active = input.getBooleanOr("active", false);
        recipeId = input.getString("recipe").map(Identifier::tryParse).orElse(null);
        remaining = input.read("remaining", AspectList.CODEC).orElse(AspectList.EMPTY);
        pendingComponents.clear();
        input.read("components", BlockPos.CODEC.listOf()).ifPresent(pendingComponents::addAll);
        eventChance = input.getDoubleOr("event_chance", 0);
        crafter = input.getString("crafter").map(UUID::fromString).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("active", active);
        if (recipeId != null) {
            output.putString("recipe", recipeId.toString());
        }
        if (!remaining.isEmpty()) {
            output.store("remaining", AspectList.CODEC, remaining);
        }
        if (!pendingComponents.isEmpty()) {
            output.store("components", BlockPos.CODEC.listOf(), List.copyOf(pendingComponents));
        }
        output.putDouble("event_chance", eventChance);
        if (crafter != null) {
            output.putString("crafter", crafter.toString());
        }
    }

    /** Area in which pedestals and jars are searched, for rendering hints. */
    public AABB workArea() {
        int r = TUConfig.INFUSION_RANGE.get();
        return new AABB(worldPosition).inflate(r, 4, r);
    }
}

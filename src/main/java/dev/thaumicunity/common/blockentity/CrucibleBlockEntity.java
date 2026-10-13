package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.recipe.CrucibleInput;
import dev.thaumicunity.common.recipe.CrucibleRecipe;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TURecipes;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.registry.TUTags;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.spell.SpellFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Crucible: holds water and dissolved essentia. Heated from below it starts to boil; items thrown in are either a
 * recipe catalyst (consuming essentia and producing the result) or dissolve into their aspects. Essentia beyond the
 * capacity boils off as flux.
 */
public class CrucibleBlockEntity extends SyncedBlockEntity {
    public static final int MAX_WATER = 1000;
    public static final int BOIL_HEAT = 150;
    public static final int MAX_HEAT = 200;

    private int water;
    private int heat;
    private AspectList essentia = AspectList.EMPTY;

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.CRUCIBLE.get(), pos, state);
    }

    public int water() {
        return water;
    }

    public int heat() {
        return heat;
    }

    public boolean isBoiling() {
        return water > 0 && heat >= BOIL_HEAT;
    }

    public AspectList essentia() {
        return essentia;
    }

    public void fillWater() {
        water = MAX_WATER;
        markForSync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity crucible) {
        boolean heated = isHeatSource(level.getBlockState(pos.below()));
        int before = crucible.heat;
        if (heated && crucible.water > 0) {
            crucible.heat = Math.min(MAX_HEAT, crucible.heat + 1);
        } else {
            crucible.heat = Math.max(0, crucible.heat - 2);
        }
        if ((before >= BOIL_HEAT) != (crucible.heat >= BOIL_HEAT)) {
            crucible.markForSync();
        }
        if (crucible.isBoiling() && level.getGameTime() % 40 == 0 && !crucible.essentia.isEmpty()
                && level instanceof ServerLevel server) {
            server.sendParticles(SpellFx.spark(crucible.color()), pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                    4, 0.2, 0.05, 0.2, 0.01);
        }
    }

    public static boolean isHeatSource(BlockState below) {
        if (below.is(TUTags.Blocks.CRUCIBLE_HEAT_SOURCES)) {
            return !below.hasProperty(CampfireBlock.LIT) || below.getValue(CampfireBlock.LIT);
        }
        return false;
    }

    /** Average colour of the dissolved essentia, for particles and rendering. */
    public int color() {
        if (essentia.isEmpty() || level == null) {
            return 0x3F76E4;
        }
        long r = 0, g = 0, b = 0, n = 0;
        for (var e : essentia.asMap().entrySet()) {
            int c = Aspects.color(level.registryAccess(), e.getKey());
            int w = e.getValue();
            r += (long) ((c >> 16) & 0xFF) * w;
            g += (long) ((c >> 8) & 0xFF) * w;
            b += (long) (c & 0xFF) * w;
            n += w;
        }
        return (int) (((r / n) << 16) | ((g / n) << 8) | (b / n));
    }

    /** Called when an item entity touches the liquid. */
    public void onItemEntered(ServerLevel level, ItemEntity entity) {
        if (!isBoiling() || entity.isRemoved()) {
            return;
        }
        ItemStack stack = entity.getItem();
        ServerPlayer thrower = entity.getOwner() instanceof ServerPlayer p ? p : null;
        Optional<RecipeHolder<CrucibleRecipe>> recipe = level.recipeAccess()
                .getRecipeFor(TURecipes.CRUCIBLE.get(), new CrucibleInput(stack.copyWithCount(1), essentia), level);
        if (recipe.isPresent() && (thrower == null || ResearchManager.knows(thrower, recipe.get().value().research()))) {
            CrucibleRecipe r = recipe.get().value();
            essentia = essentia.subtract(r.aspects());
            stack.shrink(1);
            if (stack.isEmpty()) {
                entity.discard();
            }
            ItemStack result = r.assemble(new CrucibleInput(stack, essentia));
            ItemEntity out = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, result, 0, 0.25, 0);
            level.addFreshEntity(out);
            water = Math.max(0, water - 50);
            level.playSound(null, worldPosition, TUSounds.CRAFT_SUCCESS.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            level.sendParticles(SpellFx.spark(color()), worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.1);
            if (thrower != null) {
                ResearchManager.onCrafted(thrower, result);
            }
            markForSync();
            return;
        }
        AspectList aspects = ItemAspects.of(stack, false);
        if (aspects.isEmpty()) {
            return;
        }
        int count = stack.getCount();
        for (int i = 0; i < count; i++) {
            essentia = essentia.add(aspects);
        }
        entity.discard();
        water = Math.max(0, water - 2 * count);
        level.playSound(null, worldPosition, TUSounds.BUBBLE.get(), SoundSource.BLOCKS, 0.6f, 0.9f + level.getRandom().nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.5, worldPosition.getY() + 0.9,
                worldPosition.getZ() + 0.5, 6 * count, 0.2, 0.05, 0.2, 0.0);
        overflowToFlux(level);
        markForSync();
    }

    private void overflowToFlux(ServerLevel level) {
        int capacity = TUConfig.CRUCIBLE_CAPACITY.get();
        int total = essentia.total();
        if (total <= capacity) {
            return;
        }
        double fraction = (double) capacity / total;
        AspectList kept = essentia.scale(fraction);
        int lost = total - kept.total();
        essentia = kept;
        AuraManager.addFlux(level, worldPosition, lost * 0.5);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
        Vec3 c = Vec3.atCenterOf(worldPosition);
        level.sendParticles(ParticleTypes.WITCH, c.x, c.y + 0.6, c.z, 12, 0.3, 0.2, 0.3, 0.02);
    }

    /** Empties the crucible: all essentia becomes flux. */
    public void dump(ServerLevel level) {
        if (!essentia.isEmpty()) {
            AuraManager.addFlux(level, worldPosition, essentia.total() * 0.5);
            essentia = AspectList.EMPTY;
        }
        water = 0;
        heat = 0;
        level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
        markForSync();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        water = input.getIntOr("water", 0);
        heat = input.getIntOr("heat", 0);
        essentia = input.read("essentia", AspectList.CODEC).orElse(AspectList.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("water", water);
        output.putInt("heat", heat);
        if (!essentia.isEmpty()) {
            output.store("essentia", AspectList.CODEC, essentia);
        }
    }
}

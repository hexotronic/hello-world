package dev.thaumicunity.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.block.VisCrystalClusterBlock;
import dev.thaumicunity.common.registry.TUFlora;
import dev.thaumicunity.core.aura.PrimalAttunement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * Vis crystal clusters on cave walls: up to three clusters on open stone faces near the origin (tried first, then
 * {@code tries} random spots within {@code spread}). The placement keeps them underground; they only grow on natural
 * stone. Each cluster's primal is drawn by the biome's aura attunement, so fiery places grow more Ignis.
 */
public record CrystalClusterFeature(int tries, int spread) implements Feature {
    public static final MapCodec<CrystalClusterFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 256).fieldOf("tries").forGetter(CrystalClusterFeature::tries),
            Codec.intRange(0, 16).fieldOf("spread").forGetter(CrystalClusterFeature::spread)
    ).apply(i, CrystalClusterFeature::new));

    private static final int MAX_CLUSTERS = 3;

    @Override
    public MapCodec<? extends Feature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int placed = 0;
        for (int i = 0; i < tries && placed < MAX_CLUSTERS; i++) {
            BlockPos pos = i == 0 ? origin : origin.offset(offset(random), offset(random), offset(random));
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            Direction facing = attachment(level, pos, random);
            if (facing == null) {
                continue;
            }
            BlockState state = cluster(level, pos, random).defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
            level.setBlock(pos, state, 2);
            placed++;
        }
        return placed > 0;
    }

    private int offset(RandomSource random) {
        return random.nextInt(spread * 2 + 1) - spread;
    }

    /** A direction the cluster can point in: away from a sturdy stone face next to it. */
    private static @Nullable Direction attachment(WorldGenLevel level, BlockPos pos, RandomSource random) {
        Direction[] directions = Direction.values();
        int start = random.nextInt(directions.length);
        for (int k = 0; k < directions.length; k++) {
            Direction facing = directions[(start + k) % directions.length];
            BlockPos supportPos = pos.relative(facing.getOpposite());
            BlockState support = level.getBlockState(supportPos);
            if (support.is(BlockTags.BASE_STONE_OVERWORLD) && support.isFaceSturdy(level, supportPos, facing)) {
                return facing;
            }
        }
        return null;
    }

    /** The cluster of a primal drawn by the attunement of the biome at {@code pos}. */
    static VisCrystalClusterBlock cluster(WorldGenLevel level, BlockPos pos, RandomSource random) {
        PrimalAttunement attunement = AuraManager.profileOf(level.getBiome(pos)).toAttunement();
        double total = 0;
        for (int i = 0; i < 6; i++) {
            total += attunement.weight(i);
        }
        double roll = random.nextDouble() * total;
        int primal = 5;
        for (int i = 0; i < 6; i++) {
            roll -= attunement.weight(i);
            if (roll <= 0) {
                primal = i;
                break;
            }
        }
        return TUFlora.CRYSTAL_CLUSTERS.get(PrimalAttunement.PRIMAL_ORDER.get(primal)).get();
    }
}

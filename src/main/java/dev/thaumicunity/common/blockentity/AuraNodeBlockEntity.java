package dev.thaumicunity.common.blockentity;

import com.mojang.serialization.Codec;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.aura.ChunkAura;
import dev.thaumicunity.common.aura.node.NodeModifier;
import dev.thaumicunity.common.aura.node.NodeType;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.spell.SpellFx;
import dev.thaumicunity.common.wand.WandVis;
import dev.thaumicunity.common.wand.Wands;
import dev.thaumicunity.core.aura.PrimalAttunement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aura node (docs/DESIGN_DECISIONS.md, D2, hybrid aura): a knot of vis hanging in the air. It holds vis of some of the
 * six primals up to its capacity and refills itself, feeds the vis of its chunk while that is below its base level,
 * and does what its {@link NodeType} does to flux. Wands draw vis from it ({@link #drawInto}).
 */
public class AuraNodeBlockEntity extends SyncedBlockEntity {
    /** Ticks between pulses. */
    public static final int PULSE = 20;
    /** Vis of each primal a wand draws per use. */
    public static final double DRAW_PER_USE = 5.0;
    /** A node feeds its chunk while the chunk's vis is below this share of its base level. */
    private static final double FEED_CEILING = 1.25;
    /** Pulses between updates sent to clients. */
    private static final int SYNC_EVERY = 5;

    private static final Codec<List<Double>> DOUBLES = Codec.DOUBLE.listOf();

    private NodeType type = NodeType.NORMAL;
    private NodeModifier modifier = NodeModifier.NONE;
    private final double[] vis = new double[6];
    private final double[] capacity = new double[6];
    private boolean initialized;
    private int pulses;

    public AuraNodeBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.AURA_NODE.get(), pos, state);
    }

    // --- forming

    /** Gives the node its kind and capacities, full. */
    public void initialize(NodeType type, NodeModifier modifier, double[] capacities) {
        this.type = type;
        this.modifier = modifier;
        for (int i = 0; i < 6; i++) {
            capacity[i] = Math.max(0, capacities[i]);
            vis[i] = capacity[i];
        }
        initialized = true;
        markForSync();
    }

    /**
     * Capacities for a node forming where the aura has the given attunement: 20 to 60 vis in all (scaled by the
     * modifier), most of it in the primals the place resonates with; a few primals may be missing altogether.
     */
    public static double[] randomCapacities(PrimalAttunement attunement, NodeModifier modifier, RandomSource random) {
        double[] weights = new double[6];
        int strongest = 0;
        for (int i = 0; i < 6; i++) {
            double w = attunement.weight(i);
            weights[i] = w * w * (0.3 + random.nextDouble());
            if (weights[i] > weights[strongest]) {
                strongest = i;
            }
        }
        for (int i = 0; i < 6; i++) {
            if (i != strongest && random.nextFloat() < 0.35f) {
                weights[i] = 0;
            }
        }
        double sum = 0;
        for (double w : weights) {
            sum += w;
        }
        double size = (20 + random.nextInt(41)) * modifier.size();
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            out[i] = sum <= 0 ? 0 : Math.round(weights[i] / sum * size * 10) / 10.0;
        }
        return out;
    }

    // --- state

    public NodeType type() {
        return type;
    }

    public NodeModifier modifier() {
        return modifier;
    }

    public double vis(int primal) {
        return vis[primal];
    }

    public double capacity(int primal) {
        return capacity[primal];
    }

    public double totalVis() {
        double total = 0;
        for (double v : vis) {
            total += v;
        }
        return total;
    }

    public double totalCapacity() {
        double total = 0;
        for (double c : capacity) {
            total += c;
        }
        return total;
    }

    /** "Pure Bright Aura Node" and the like. */
    public Component displayName() {
        Component kind = Component.translatable(type.translationKey());
        return modifier == NodeModifier.NONE
                ? Component.translatable("node.thaumicunity.name", kind)
                : Component.translatable("node.thaumicunity.name_modified", kind, Component.translatable(modifier.translationKey()));
    }

    /** What a thaumometer learns from the node: its primals, a quarter of their capacity each. */
    public AspectList aspects() {
        Map<Identifier, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            if (capacity[i] >= 1) {
                map.put(Aspects.PRIMALS.get(i), Math.max(1, (int) Math.round(capacity[i] / 4)));
            }
        }
        return AspectList.of(map);
    }

    /** The node's colour: its primals' colours weighted by the vis it holds, with a touch of its type. */
    public int color(RegistryAccess access) {
        double r = 0;
        double g = 0;
        double b = 0;
        double total = 0;
        for (int i = 0; i < 6; i++) {
            if (vis[i] <= 0) {
                continue;
            }
            int c = Aspects.color(access, Aspects.PRIMALS.get(i));
            r += ARGB.red(c) * vis[i];
            g += ARGB.green(c) * vis[i];
            b += ARGB.blue(c) * vis[i];
            total += vis[i];
        }
        int mixed = total <= 0 ? 0x808080 : ARGB.color(255, (int) (r / total), (int) (g / total), (int) (b / total));
        return ARGB.srgbLerp(type == NodeType.NORMAL ? 0.1f : 0.45f, mixed, type.tint());
    }

    // --- behaviour

    public static void serverTick(Level level, BlockPos pos, BlockState state, AuraNodeBlockEntity node) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!node.initialized) {
            // Placed by hand or by a command: form like a natural node of this place.
            RandomSource random = RandomSource.create(pos.asLong() ^ server.getSeed());
            NodeModifier modifier = NodeModifier.random(random);
            node.initialize(NodeType.forBiome(server.getBiome(pos), random), modifier,
                    randomCapacities(AuraManager.attunementAt(server, pos), modifier, random));
        }
        if ((server.getGameTime() + pos.asLong()) % PULSE == 0) {
            node.pulse(server);
        }
    }

    /** One second of the node's life: refill, fade, feed the aura, and act on flux by type. */
    public void pulse(ServerLevel level) {
        BlockPos pos = getBlockPos();
        if (modifier == NodeModifier.FADING) {
            for (int i = 0; i < 6; i++) {
                capacity[i] *= 0.995;
                vis[i] = Math.min(vis[i], capacity[i]);
            }
            if (totalCapacity() < 1.0) {
                level.removeBlock(pos, false);
                return;
            }
        }
        double rate = modifier.regen();
        for (int i = 0; i < 6; i++) {
            vis[i] = Math.min(capacity[i], vis[i] + capacity[i] * rate);
        }

        ChunkAura aura = AuraManager.auraAt(level, pos);
        double feed = type.feed() * totalVis() / 50.0;
        if (type == NodeType.UNSTABLE) {
            feed *= level.getRandom().nextDouble() * 2;
        }
        if (feed > 0 && aura.vis() < aura.base() * FEED_CEILING) {
            AuraManager.addVis(level, pos, feed);
        }
        if (type.fluxPerSecond() > 0) {
            AuraManager.addFlux(level, pos, type.fluxPerSecond());
        } else if (type.fluxPerSecond() < 0) {
            AuraManager.reduceFlux(level, pos, -type.fluxPerSecond());
        }
        if (type == NodeType.HUNGRY) {
            feedOnAura(level, pos);
        }
        if (type == NodeType.UNSTABLE && level.getRandom().nextFloat() < 0.03f) {
            flare(level, pos);
        }

        int color = color(level.registryAccess());
        level.sendParticles(SpellFx.spark(color), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.25, 0.25, 0.25, 0.01);
        if (++pulses >= SYNC_EVERY) {
            pulses = 0;
            markForSync();
        } else {
            setChanged();
        }
    }

    /** A hungry node drinks half a vis a second from its chunk into whichever primal it holds least of. */
    private void feedOnAura(ServerLevel level, BlockPos pos) {
        int lowest = -1;
        for (int i = 0; i < 6; i++) {
            if (capacity[i] > 0 && vis[i] < capacity[i] * 1.5
                    && (lowest < 0 || vis[i] / capacity[i] < vis[lowest] / capacity[lowest])) {
                lowest = i;
            }
        }
        if (lowest >= 0) {
            vis[lowest] += AuraManager.drainVis(level, pos, 0.5);
        }
    }

    /** An unstable node flares: it spills a quarter of one primal as flux. */
    private void flare(ServerLevel level, BlockPos pos) {
        int primal = level.getRandom().nextInt(6);
        double lost = vis[primal] * 0.25;
        vis[primal] -= lost;
        AuraManager.addFlux(level, pos, 0.5 + lost * 0.1);
        level.sendParticles(SpellFx.spark(0xF0A040), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.05);
    }

    /**
     * Draws up to {@link #DRAW_PER_USE} vis of each primal the node holds into a wand, as far as the wand has room.
     * Returns the vis moved, in hundredths.
     */
    public int drawInto(ServerLevel level, ItemStack wand) {
        WandVis wandVis = Wands.currentVis(level.registryAccess(), wand);
        if (wandVis.capacity() <= 0) {
            return 0;
        }
        int moved = 0;
        for (int i = 0; i < 6; i++) {
            int room = wandVis.capacity() - wandVis.get(i);
            int take = (int) Math.min(Math.min(room, DRAW_PER_USE * WandVis.SCALE), Math.floor(vis[i] * WandVis.SCALE));
            if (take > 0) {
                wandVis = wandVis.with(i, wandVis.get(i) + take);
                vis[i] -= take / (double) WandVis.SCALE;
                moved += take;
            }
        }
        if (moved > 0) {
            wand.set(TUDataComponents.WAND_VIS.get(), wandVis);
            markForSync();
        }
        return moved;
    }

    // --- saving

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        initialized = input.getBooleanOr("initialized", false);
        type = NodeType.byName(input.getString("type").orElse("normal"));
        modifier = NodeModifier.byName(input.getString("modifier").orElse("none"));
        List<Double> savedVis = input.read("vis", DOUBLES).orElse(List.of());
        List<Double> savedCapacity = input.read("capacity", DOUBLES).orElse(List.of());
        for (int i = 0; i < 6; i++) {
            vis[i] = i < savedVis.size() ? savedVis.get(i) : 0;
            capacity[i] = i < savedCapacity.size() ? savedCapacity.get(i) : 0;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("initialized", initialized);
        output.putString("type", type.getSerializedName());
        output.putString("modifier", modifier.getSerializedName());
        List<Double> visList = new ArrayList<>(6);
        List<Double> capacityList = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            visList.add(vis[i]);
            capacityList.add(capacity[i]);
        }
        output.store("vis", DOUBLES, visList);
        output.store("capacity", DOUBLES, capacityList);
    }
}

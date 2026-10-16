package dev.thaumicunity.common.block;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.blockentity.EssentiaTubeBlockEntity;
import dev.thaumicunity.common.essentia.EssentiaNode;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Essentia tube: connects to other tubes and to devices that take part in essentia transport
 * ({@link EssentiaNode}); the six connection properties drive the model and the shape. Right-click with an empty hand
 * to see what it holds and how strongly it pulls.
 */
public class EssentiaTubeBlock extends TUEntityBlock<EssentiaTubeBlockEntity> {
    private static final VoxelShape CORE = box(5.5, 5.5, 5.5, 10.5, 10.5, 10.5);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, box(6.5, 6.5, 0, 9.5, 9.5, 5.5),
            Direction.SOUTH, box(6.5, 6.5, 10.5, 9.5, 9.5, 16),
            Direction.WEST, box(0, 6.5, 6.5, 5.5, 9.5, 9.5),
            Direction.EAST, box(10.5, 6.5, 6.5, 16, 9.5, 9.5),
            Direction.DOWN, box(6.5, 0, 6.5, 9.5, 5.5, 9.5),
            Direction.UP, box(6.5, 10.5, 6.5, 9.5, 16, 9.5)));

    public EssentiaTubeBlock(Properties properties) {
        super(properties, TUBlockEntities.ESSENTIA_TUBE, EssentiaTubeBlockEntity::new, EssentiaTubeBlockEntity::serverTick);
        BlockState state = stateDefinition.any();
        for (Direction side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), false);
        }
        registerDefaultState(withDefaults(state));
    }

    /** Extra default property values of subclasses. */
    protected BlockState withDefaults(BlockState state) {
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PipeBlock.NORTH, PipeBlock.EAST, PipeBlock.SOUTH, PipeBlock.WEST, PipeBlock.UP, PipeBlock.DOWN);
    }

    /** Whether essentia can pass; a closed valve is not. */
    public boolean isOpen(BlockState state) {
        return true;
    }

    /** Whether a tube at {@code pos} would join its neighbour on {@code side}. */
    public static boolean connects(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos otherPos = pos.relative(side);
        BlockState other = level.getBlockState(otherPos);
        if (other.getBlock() instanceof EssentiaTubeBlock tube) {
            return tube.isOpen(other);
        }
        return level.getBlockEntity(otherPos) instanceof EssentiaNode node && node.connectsTo(side.getOpposite());
    }

    /** The state with every connection worked out again. */
    protected BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        boolean open = isOpen(state);
        for (Direction side : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side), open && connects(level, pos, side));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), isOpen(state) && connects(level, pos, direction));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        for (Direction side : Direction.values()) {
            if (state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side))) {
                shape = Shapes.or(shape, ARMS.get(side));
            }
        }
        return shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        EssentiaTubeBlockEntity tube = blockEntity(level, pos);
        if (tube == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            Component contents = tube.aspect() == null ? Component.translatable("message.thaumicunity.tube.empty")
                    : Component.translatable(Aspect.translationKey(tube.aspect()));
            Component wants = tube.currentSuctionAspect() == null ? Component.translatable("message.thaumicunity.tube.any")
                    : Component.translatable(Aspect.translationKey(tube.currentSuctionAspect()));
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.tube.status", contents, tube.currentSuction(), wants));
        }
        return InteractionResult.SUCCESS;
    }
}

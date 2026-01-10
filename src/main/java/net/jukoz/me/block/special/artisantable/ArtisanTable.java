package net.jukoz.me.block.special.artisantable;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.jukoz.me.gui.artisantable.ArtisanTableScreenHandler;
import net.jukoz.me.resources.StateSaverAndLoader;
import net.jukoz.me.resources.datas.Disposition;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class ArtisanTable extends HorizontalFacingBlock {
    public static final EnumProperty<ArtisanTablePart> PART = EnumProperty.of("part", ArtisanTablePart.class);
    private static final Text TITLE = Text.translatable("container.me.artisan_table");

    public ArtisanTable(Settings settings) {
        // FIX 1: Set PistonBehavior in the constructor settings instead of overriding the method
        super(settings.pistonBehavior(PistonBehavior.DESTROY));
        this.setDefaultState(this.stateManager.getDefaultState().with(PART, ArtisanTablePart.LEFT).with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return createCodec(ArtisanTable::new);
    }

    // REMOVED: getPistonBehavior override (it does not exist in superclass in 1.21+)

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if(!world.isClient) {
            player.openHandledScreen(new ExtendedScreenHandlerFactory<>() {
                @Override
                public Object getScreenOpeningData(ServerPlayerEntity player) {
                    Disposition disposition = StateSaverAndLoader.getPlayerState(player).getCurrentDisposition();
                    if (disposition == null){
                        disposition = Disposition.NEUTRAL;
                    }
                    return disposition + "/" + player.isCreative();
                }

                @Override
                public Text getDisplayName() {
                    return TITLE;
                }

                @Nullable
                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
                    Disposition disposition = StateSaverAndLoader.getPlayerState(player).getCurrentDisposition();
                    if (disposition == null){
                        disposition = Disposition.NEUTRAL;
                    }
                    // FIX 3: Pass ScreenHandlerContext to fix duplication/desync issues
                    return new ArtisanTableScreenHandler(
                            syncId,
                            playerInventory,
                            ScreenHandlerContext.create(world, pos),
                            disposition + "/" + player.isCreative()
                    );
                }
            });
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == getDirectionTowardsOtherPart(state.get(PART), state.get(FACING))) {
            return neighborState.isOf(this) && neighborState.get(PART) != state.get(PART) ? state : Blocks.AIR.getDefaultState();
        } else {
            return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    // FIX 2: Robust breaking logic to prevent ghost blocks/half-tables
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            ArtisanTablePart part = state.get(PART);
            BlockPos otherPos = pos.offset(getDirectionTowardsOtherPart(part, state.get(FACING)));
            BlockState otherState = world.getBlockState(otherPos);

            if (otherState.isOf(this) && otherState.get(PART) != part) {
                // Break the other half without dropping items (SKIP_DROPS)
                world.setBlockState(otherPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                world.syncWorldEvent(player, 2001, otherPos, Block.getRawIdFromState(otherState));
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    private static Direction getDirectionTowardsOtherPart(ArtisanTablePart part, Direction direction) {
        return part == ArtisanTablePart.LEFT ? direction.rotateYClockwise() : direction.rotateYCounterclockwise();
    }

    @Nullable
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction direction = ctx.getHorizontalPlayerFacing();
        BlockPos blockPos = ctx.getBlockPos();
        BlockPos blockPos2 = blockPos.offset(direction.rotateYClockwise());
        World world = ctx.getWorld();
        return world.getBlockState(blockPos2).canReplace(ctx) && world.getWorldBorder().contains(blockPos2) ? (BlockState)this.getDefaultState().with(FACING, direction).with(PART, ArtisanTablePart.LEFT) : null;
    }

    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockPos blockPos = pos.offset((Direction)state.get(FACING).rotateYClockwise());
            world.setBlockState(blockPos, (BlockState)state.with(PART, ArtisanTablePart.RIGHT), 3);
            world.updateNeighbors(pos, Blocks.AIR);
            state.updateNeighbors(world, pos, 3);
        }
    }

    public long getRenderingSeed(BlockState state, BlockPos pos) {
        BlockPos blockPos = pos.offset((Direction)state.get(FACING), state.get(PART) == ArtisanTablePart.RIGHT ? 0 : 1);
        return MathHelper.hashCode(blockPos.getX(), pos.getY(), blockPos.getZ());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(FACING, PART);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(PART)){
            case LEFT ->
                    switch (state.get(FACING)){
                        case DOWN, UP -> VoxelShapes.fullCube();
                        case NORTH -> Stream.of(
                                Block.createCuboidShape(1, 0, 1, 4, 12, 15),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(4, 4, 7, 16, 8, 9)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                        case SOUTH -> Stream.of(
                                Block.createCuboidShape(12, 0, 1, 15, 12, 15),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(0, 4, 7, 12, 8, 9)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                        case WEST -> Stream.of(
                                Block.createCuboidShape(1, 0, 12, 15, 12, 15),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(7, 4, 0, 9, 8, 12)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                        case EAST -> Stream.of(
                                Block.createCuboidShape(1, 0, 1, 15, 12, 4),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(7, 4, 4, 9, 8, 16)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                    };
            case RIGHT ->
                    switch (state.get(FACING)) {
                        case DOWN, UP -> VoxelShapes.fullCube();
                        case NORTH -> Stream.of(
                                Block.createCuboidShape(12, 0, 1, 15, 12, 15),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(0, 4, 7, 12, 8, 9)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                        case SOUTH -> Stream.of(
                                Block.createCuboidShape(1, 0, 1, 4, 12, 15),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(4, 4, 7, 16, 8, 9)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                        case WEST -> Stream.of(
                                Block.createCuboidShape(1, 0, 1, 15, 12, 4),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(7, 4, 4, 9, 8, 16)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                        case EAST -> Stream.of(
                                Block.createCuboidShape(1, 0, 12, 15, 12, 15),
                                Block.createCuboidShape(0, 12, 0, 16, 16, 16),
                                Block.createCuboidShape(7, 4, 0, 9, 8, 12)
                        ).reduce((v1, v2) -> VoxelShapes.combineAndSimplify(v1, v2, BooleanBiFunction.OR)).get();
                    };
        };
    }
}
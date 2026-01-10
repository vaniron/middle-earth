package net.jukoz.me.block.special.forge;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.jukoz.me.MiddleEarth;
import net.jukoz.me.block.ModBlockEntities;
import net.jukoz.me.block.ModDecorativeBlocks;
import net.jukoz.me.block.special.bellows.BellowsBlock;
import net.jukoz.me.datageneration.content.models.HotMetalsModel;
import net.jukoz.me.gui.forge.ForgeAlloyingScreenHandler;
import net.jukoz.me.gui.forge.ForgeHeatingScreenHandler;
import net.jukoz.me.item.ModDataComponentTypes;
import net.jukoz.me.item.ModResourceItems;
import net.jukoz.me.item.dataComponents.TemperatureDataComponent;
import net.jukoz.me.recipe.AlloyingRecipe;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.registry.*;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ForgeBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, SidedInventory {
    private static final String ID = "forge";
    public static final int MAX_PROGRESS = 1200;
    public static final int MAX_STORAGE = 2304;
    public static final int MAX_BOOST_TIME = 10;
    public static final int FUEL_SLOT = 0;
    public static final int OUTPUT_SLOT = 5;

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(6, ItemStack.EMPTY);
    protected final PropertyDelegate propertyDelegate;
    protected final Map<Item, Integer> fuelTimeMap = AbstractFurnaceBlockEntity.createFuelTimeMap();

    private int progress = 0;
    private int boostTime = 0;
    private int fuelTime = 0;
    private int maxFuelTime = 0;
    private int mode = 0;
    private int storage = 0;

    private MetalTypes currentMetal = MetalTypes.EMPTY;

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGE, pos, state);
        this.propertyDelegate = new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> ForgeBlockEntity.this.progress;
                    case 1 -> ForgeBlockEntity.this.fuelTime;
                    case 2 -> ForgeBlockEntity.this.maxFuelTime;
                    case 3 -> ForgeBlockEntity.this.mode;
                    case 4 -> ForgeBlockEntity.this.storage;
                    case 5 -> ForgeBlockEntity.this.currentMetal.getId();
                    default -> throw new IllegalStateException("Unexpected value: " + index);
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> ForgeBlockEntity.this.progress = value;
                    case 1 -> ForgeBlockEntity.this.fuelTime = value;
                    case 2 -> ForgeBlockEntity.this.maxFuelTime = value;
                    case 3 -> ForgeBlockEntity.this.mode = value;
                    case 4 -> ForgeBlockEntity.this.storage = value;
                }
            }

            @Override
            public int size() {
                return 6;
            }
        };
    }

    public ItemStack getRenderStack(ForgeBlockEntity entity) {
        // Safety check for rendering
        if (this.currentMetal != MetalTypes.EMPTY && this.currentMetal.getIngot() != null){
            return entity.currentMetal.getIngot().getDefaultStack();
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("screen." + MiddleEarth.MOD_ID + "." + ID);
    }

    public int getStorage() {
        return storage;
    }

    public MetalTypes getCurrentMetal() {
        return currentMetal;
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        if(hasBellows(player.getWorld(), this.pos, player.getWorld().getBlockState(this.pos)) == 1) {
            return new ForgeAlloyingScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
        } else {
            return new ForgeHeatingScreenHandler(syncId, playerInventory, this, this.propertyDelegate);
        }
    }

    public int hasBellows(World world, BlockPos pos, BlockState state){
        Direction direction = state.get(Properties.HORIZONTAL_FACING);
        BlockPos pos1 = pos.offset(direction.rotateYClockwise());
        BlockPos pos2 = pos.offset(direction.rotateYClockwise().getOpposite());

        Direction directionForge = state.get(ForgeBlock.FACING);

        if(world.getBlockState(pos1).isOf(ModDecorativeBlocks.BELLOWS) && world.getBlockState(pos2).isOf(ModDecorativeBlocks.BELLOWS)){
            Direction direction1 = world.getBlockState(pos1).get(BellowsBlock.FACING);
            Direction direction2 = world.getBlockState(pos2).get(BellowsBlock.FACING);
            switch (directionForge){
                case NORTH -> {
                    if (direction1 == Direction.WEST && direction2 == Direction.EAST) return 1;
                }
                case SOUTH ->{
                    if (direction1 == Direction.EAST && direction2 == Direction.WEST) return 1;
                }
                case EAST ->{
                    if (direction1 == Direction.NORTH && direction2 == Direction.SOUTH) return 1;
                }
                case WEST ->{
                    if (direction1 == Direction.SOUTH && direction2 == Direction.NORTH) return 1;
                }
            }
        }
        return 0;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        Inventories.writeNbt(nbt, this.inventory, true, registryLookup);
        nbt.putInt(ID + ".progress", this.progress);
        nbt.putInt(ID + ".boost-time", this.boostTime);
        nbt.putInt(ID + ".fuel-time", this.fuelTime);
        nbt.putInt(ID + ".max-fuel-time", this.maxFuelTime);
        nbt.putInt(ID + ".mode", this.mode);
        nbt.putInt(ID + ".storage", this.storage);
        nbt.putString(ID + ".current-metal", this.currentMetal.getName());
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        this.inventory.clear();
        Inventories.readNbt(nbt, this.inventory, registryLookup);
        this.progress = nbt.getInt(ID + ".progress");
        this.boostTime = nbt.getInt(ID + ".boost-time");
        this.fuelTime = nbt.getInt(ID + ".fuel-time");
        this.maxFuelTime = nbt.getInt(ID + ".max-fuel-time");
        this.mode = nbt.getInt(ID + ".mode");
        this.storage = nbt.getInt(ID + ".storage");
        try {
            this.currentMetal = MetalTypes.valueOf(nbt.getString(ID + ".current-metal").toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            this.currentMetal = MetalTypes.EMPTY;
        }
    }

    public void update() {
        markDirty();
        if(world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    public void setInventory(DefaultedList<ItemStack> inventory) {
        for (int i = 0; i < inventory.size(); i++) {
            this.inventory.set(i, inventory.get(i));
        }
    }

    protected boolean isFuel(Item item) {
        return fuelTimeMap.containsKey(item);
    }

    @Override
    public int[] getAvailableSlots(Direction side) {
        int[] slots = new int[inventory.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        if (this.world.getBlockState(this.pos).get(ForgeBlock.PART) == ForgePart.TOP) return false;
        if (mode == 0 && dir != null) return false;
        if (slot == FUEL_SLOT) {
            return isFuel(stack.getItem());
        }
        return true;
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        if (this.world.getBlockState(this.pos).get(ForgeBlock.PART) == ForgePart.TOP) return false;
        if (dir == Direction.DOWN && slot < 5) return false;
        return true;
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (!getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(this.inventory, slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(inventory, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        this.inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return Inventory.canPlayerUse(this, player);
    }

    @Override
    public void clear() {
        this.inventory.clear();
    }

    public void bellowsBoost() {
        this.boostTime = MAX_BOOST_TIME;
        update();
    }

    // --- REFACTORED OUTPUT LOGIC (CRASH FIX) ---
    public static void outputItemStack(int amount, Vec3d coords, ServerPlayerEntity player){
        BlockPos pos = new BlockPos((int) coords.getX(), (int) coords.getY(), (int) coords.getZ());
        Optional<ForgeBlockEntity> forgeBlockEntity = player.getWorld().getBlockEntity(pos, ModBlockEntities.FORGE);

        if(forgeBlockEntity.isPresent()){
            ForgeBlockEntity entity = forgeBlockEntity.get();

            // 1. Guard against empty metal
            if (entity.currentMetal == MetalTypes.EMPTY) return;

            ItemStack itemstack = ItemStack.EMPTY;
            RegistryWrapper.Impl<ArmorTrimMaterial> armorTrimMaterialRegistry = entity.getWorld().getRegistryManager().getWrapperOrThrow(RegistryKeys.TRIM_MATERIAL);
            RegistryWrapper.Impl<ArmorTrimPattern> armorTrimPatternRegistry = entity.getWorld().getRegistryManager().getWrapperOrThrow(RegistryKeys.TRIM_PATTERN);

            switch (amount){
                case 16 -> {
                    if (entity.currentMetal.getNugget() != null){
                        itemstack = new ItemStack(entity.currentMetal.getNugget());
                    }
                }
                case 144 -> {
                    // 2. Guard against null ingots
                    if (entity.currentMetal.getIngot() != null) {
                        itemstack = new ItemStack(entity.currentMetal.getIngot());
                    }
                }
                case 288, 432 -> {
                    Item rodItem = (amount == 288) ? ModResourceItems.ROD : ModResourceItems.LARGE_ROD;
                    itemstack = new ItemStack(rodItem);

                    // Identify correct registry key for Trim Material
                    Identifier matId;
                    if (entity.currentMetal.isVanilla()){
                        matId = Identifier.of(entity.currentMetal.getName());
                    } else {
                        matId = Identifier.of(MiddleEarth.MOD_ID, entity.currentMetal.getName());
                    }

                    itemstack.set(DataComponentTypes.TRIM, new ArmorTrim(
                            armorTrimMaterialRegistry.getOrThrow(RegistryKey.of(RegistryKeys.TRIM_MATERIAL, matId)),
                            armorTrimPatternRegistry.getOrThrow(RegistryKey.of(RegistryKeys.TRIM_PATTERN, Identifier.of(MiddleEarth.MOD_ID, "smithing_part")))));
                }
            }

            // Stop if item creation failed
            if (itemstack.isEmpty()) return;

            // Apply temperature to result
            itemstack.set(ModDataComponentTypes.TEMPERATURE_DATA, new TemperatureDataComponent(100));

            // Logic to merge into output slot
            ItemStack outputSlotStack = entity.getStack(OUTPUT_SLOT);
            boolean canMerge = false;

            if (outputSlotStack.isEmpty()) {
                canMerge = true;
            } else if (ItemStack.areItemsEqual(outputSlotStack, itemstack) && ItemStack.areItemsAndComponentsEqual(outputSlotStack, itemstack)) {
                canMerge = true;
            }

            if (canMerge) {
                if (amount <= entity.storage && amount > 0) {
                    // Check stack limit
                    if (outputSlotStack.getCount() + 1 <= itemstack.getMaxCount()) {
                        itemstack.setCount(outputSlotStack.getCount() + 1);
                        entity.storage = entity.storage - amount;

                        if (entity.storage <= 0) {
                            entity.storage = 0;
                            entity.currentMetal = MetalTypes.EMPTY;
                        }

                        entity.setStack(OUTPUT_SLOT, itemstack);
                        playExtractSound(entity.getWorld(), pos);
                        entity.update();
                        return;
                    }
                }
            }
            // Fallback for failure
            playFailedExtractSound(entity.getWorld(), pos);
        }
    }

    private static void playExtractSound(World world, BlockPos pos){
        world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    private static void playFailedExtractSound(World world, BlockPos pos){
        world.playSound(null, pos, SoundEvents.BLOCK_DECORATED_POT_INSERT_FAIL, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    // --- OPTIMIZED TICK LOGIC ---
    public static void tick(World world, BlockPos blockPos, BlockState blockState, ForgeBlockEntity entity) {
        if (blockState.get(ForgeBlock.PART) == ForgePart.TOP) return;

        boolean dirty = false; // Sync only once

        entity.fuelTime = Math.max(0, entity.fuelTime - 1);
        entity.boostTime = Math.max(0, entity.boostTime - 1);

        int newMode = entity.hasBellows(world, blockPos, blockState);
        if (entity.mode != newMode) {
            entity.mode = newMode;
            dirty = true;
        }

        boolean progressMade = false;

        if(entity.mode == 1) { // Alloying mode
            if(hasAlloyingRecipe(entity)) {
                if(entity.hasFuel(entity)) {
                    int progressValue = (entity.boostTime > 0) ? 8 : 1;
                    entity.progress += progressValue;
                    progressMade = true;
                    dirty = true;

                    if(entity.progress >= MAX_PROGRESS) {
                        craftItem(entity);
                        entity.progress = 0;
                        dirty = true;
                    }
                }
            }
        } else { // Heating mode
            if (dropExtraItems(entity)) {
                dirty = true;
            }

            if(hasHeatingRecipe(entity)) {
                if(entity.hasFuel(entity)) {
                    int progressValue = (entity.boostTime > 0) ? 16 : 2;
                    entity.progress += progressValue;
                    progressMade = true;
                    dirty = true;

                    if(entity.progress >= MAX_PROGRESS) {
                        for (int i = 1; i <= 4; i++) {
                            entity.getStack(i).set(ModDataComponentTypes.TEMPERATURE_DATA, new TemperatureDataComponent(100));
                        }
                        entity.progress = 0;
                        dirty = true;
                    }
                }
            }
        }

        if (!progressMade && entity.progress > 0){
            entity.progress = Math.max(entity.progress - 2, 0);
            dirty = true;
        }

        boolean isCooking = entity.fuelTime > 0;
        if (blockState.get(AbstractFurnaceBlock.LIT) != isCooking) {
            blockState = blockState.with(AbstractFurnaceBlock.LIT, isCooking);
            BlockState blockStateUp = blockState.with(AbstractFurnaceBlock.LIT, isCooking).with(ForgeBlock.PART, ForgePart.TOP);
            world.setBlockState(blockPos, blockState, Block.NOTIFY_ALL);
            world.setBlockState(blockPos.up(), blockStateUp, Block.NOTIFY_ALL);
            dirty = true;
        }

        if (dirty) {
            entity.update();
        }
    }

    private static void craftItem(ForgeBlockEntity entity) {
        SimpleInventory tempInv = new SimpleInventory(entity.size());
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < entity.size(); i++) {
            tempInv.setStack(i, entity.getStack(i));
            if(i != FUEL_SLOT && i != OUTPUT_SLOT) inputs.add(entity.getStack(i));
        }

        Optional<RecipeEntry<AlloyingRecipe>> match = entity.getWorld().getRecipeManager()
                .getFirstMatch(AlloyingRecipe.Type.INSTANCE, new MultipleStackRecipeInput(inputs), entity.getWorld());

        if(match.isPresent()) {
            // Consume inputs
            for (int i = 1; i <= 4; i++) {
                entity.removeStack(i, 1);
            }
            entity.storage = entity.storage + match.get().value().amount;
            entity.currentMetal = MetalTypes.valueOf(match.get().value().output.toUpperCase());
            // No need to call update() here as tick() does it if dirty=true
        }
    }

    private static boolean hasAlloyingRecipe(ForgeBlockEntity entity) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < entity.size(); i++) {
            if(i != FUEL_SLOT && i != OUTPUT_SLOT) inputs.add(entity.getStack(i));
        }
        if(inputs.isEmpty()) return false;

        MultipleStackRecipeInput input = new MultipleStackRecipeInput(inputs);
        Optional<RecipeEntry<AlloyingRecipe>> match = entity.getWorld().getRecipeManager()
                .getFirstMatch(AlloyingRecipe.Type.INSTANCE, input, entity.getWorld());

        if(match.isEmpty()) return false;

        return canInsertLiquid(entity.storage, entity.currentMetal, match);
    }

    // Returns true if inventory changed
    private static boolean dropExtraItems(ForgeBlockEntity entity) {
        if(entity.getWorld() == null || entity.getWorld().isClient) return false;

        boolean changed = false;
        for (int i = 0; i < entity.size(); i++) {
            if (i != FUEL_SLOT) {
                ItemStack itemStack = entity.getStack(i);
                // Heating slots accept 1 item. Output slot accepts 0 in heating mode.
                int limit = (i == OUTPUT_SLOT) ? 0 : 1;

                if (!itemStack.isEmpty() && itemStack.getCount() > limit) {
                    int difference = itemStack.getCount() - limit;

                    ItemStack extraStack = itemStack.copy();
                    extraStack.setCount(difference);

                    ItemEntity itemEntity = new ItemEntity(entity.getWorld(),
                            entity.getPos().getX() + 0.5f,
                            entity.getPos().getY() + 1.5f,
                            entity.getPos().getZ() + 0.5f, extraStack);
                    itemEntity.setToDefaultPickupDelay();

                    // Add random velocity
                    itemEntity.setVelocity((Math.random() * 0.3) - 0.15, 0.25, (Math.random() * 0.3) - 0.15);
                    entity.getWorld().spawnEntity(itemEntity);

                    if (limit == 0) {
                        entity.setStack(i, ItemStack.EMPTY);
                    } else {
                        itemStack.setCount(limit);
                    }
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean hasHeatingRecipe(ForgeBlockEntity entity) {
        boolean hasColdItem = false;
        boolean hasAnyItem = false;

        for (int i = 0; i < entity.size(); i++) {
            if(i != FUEL_SLOT && i != OUTPUT_SLOT) {
                ItemStack stack = entity.getStack(i);
                if (stack.isEmpty()) continue;

                hasAnyItem = true;
                Item item = stack.getItem();

                if(!HotMetalsModel.nuggets.contains(item) && !HotMetalsModel.ingots.contains(item) && !HotMetalsModel.items.contains(item)) {
                    return false; // Invalid item found, stop heating
                } else {
                    TemperatureDataComponent comp = stack.get(ModDataComponentTypes.TEMPERATURE_DATA);
                    if(comp == null || comp.temperature() < 100) {
                        hasColdItem = true;
                    }
                }
            }
        }
        return hasAnyItem && hasColdItem;
    }

    private boolean hasFuel(ForgeBlockEntity entity) {
        if(this.fuelTime > 0) return true;

        ItemStack fuelStack = entity.getStack(FUEL_SLOT);
        if (fuelStack.isEmpty()) return false;

        if(isFuel(fuelStack.getItem())) {
            getFuel(entity, fuelStack.getItem());
            return true;
        }
        return false;
    }

    private void getFuel(ForgeBlockEntity entity, Item fuelItem) {
        this.fuelTime = Math.round((float) fuelTimeMap.get(fuelItem) / 16);
        this.maxFuelTime = this.fuelTime;

        ItemStack stack = entity.getStack(FUEL_SLOT);
        Item remainder = stack.getItem().getRecipeRemainder();

        stack.decrement(1);

        if (stack.isEmpty() && remainder != null) {
            entity.setStack(FUEL_SLOT, new ItemStack(remainder));
        }
        // Note: If stack is not empty (e.g. 2 lava buckets), we sacrifice the bucket to avoid complexity,
        // or standard vanilla behavior would apply.
    }

    private static boolean canInsertLiquid(int storage, MetalTypes currentMetal, Optional<RecipeEntry<AlloyingRecipe>> match) {
        if (match.isEmpty()) return false;

        MetalTypes metal = MetalTypes.valueOf(match.get().value().output.toUpperCase());
        if((storage + match.get().value().amount) <= MAX_STORAGE){
            return metal == currentMetal || currentMetal == MetalTypes.EMPTY;
        }
        return false;
    }

    @Override
    public Object getScreenOpeningData(ServerPlayerEntity player) {
        return pos;
    }
}
package net.applee;

import baritone.api.BaritoneAPI;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static meteordevelopment.meteorclient.utils.player.InvUtils.findInHotbar;
import static meteordevelopment.meteorclient.utils.world.BlockUtils.*;
import static net.minecraft.block.Block.getBlockFromItem;
import static net.minecraft.util.math.MathHelper.clamp;

public class ElytraFinder extends Module {
    public enum Stage {
        Takeoff,
        Fly,
        Landing,
        Looting,
        Stack,
    }

    public enum TakeoffStage {
        BreakUp,
        Upping
    }

    public enum FlyStage {
        FlyStrict,
        CheckChest,
        FlyToElytras,
    }

    public enum LootingStage {
        BreakFrame,
        CollectElytra
    }

    public enum StackingStage {
        MoveSlot,
        Place,
        Open,
        OpenHandler,
        Put,
        CloseHandler,
        BreakShulker,
        CollectShulker
    }

    private final Item[] defaultDrop = {
        Items.PURPUR_BLOCK,
        Items.PURPUR_PILLAR,
        Items.PURPUR_SLAB,
        Items.PURPUR_STAIRS,
        Items.LADDER,
        Items.ENDER_PEARL
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Direction> defaultDirection = sgGeneral.add(new EnumSetting.Builder<Direction>()
        .name("default-direction")
        .description("Starts fly at direction after staring.")
        .defaultValue(Direction.EAST)
        .build());

    private final Setting<Integer> baritoneRefreshCounter = sgGeneral.add(new IntSetting.Builder()
        .name("baritone-refresh-value")
        .description("How many ticks baritone can standing before he will be refreshed.")
        .defaultValue(200)
        .range(0, 9999)
        .sliderRange(0, 400)
        .build());

    private final Setting<List<Item>> dropItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("items-to-drop")
        .description("Items which will be dropped every tick.")
        .defaultValue(defaultDrop)
        .build());

    private final Setting<Integer> shulkerSwapSlot = sgGeneral.add(new IntSetting.Builder()
        .name("shulker-swap-slot")
        .description("The slot used for the shulker during stacking elytras.")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .build());

    private final Setting<Integer> putAmount = sgGeneral.add(new IntSetting.Builder()
        .name("put-amount")
        .description("How many elytras will be putted in shulker during stacking.")
        .defaultValue(2)
        .range(1, 27)
        .sliderRange(1, 20)
        .build());

    private final Setting<Integer> maxInvSizeStackEnabling = sgGeneral.add(new IntSetting.Builder()
        .name("max-size-for-stackMode")
        .description("")
        .defaultValue(34)
        .range(1, 36)
        .sliderRange(1, 36)
        .build());


    private final Setting<Integer> logoutHeight = sgGeneral.add(new IntSetting.Builder()
        .name("log-height")
        .description("")
        .defaultValue(30)
        .range(-20, 200)
        .sliderRange(20, 40)
        .build());

    private final Setting<Integer> fireworkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("firework-delay")
        .description("")
        .defaultValue(30)
        .range(0, 1000)
        .sliderRange(10, 50)
        .build());

    private final Setting<Integer> minFlySpeed = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-fly-speed")
        .description("")
        .defaultValue(29)
        .range(10, 1000)
        .sliderRange(20, 50)
        .build());

    private final Setting<Double> centeringSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("centering-speed")
        .description("How fast to center you.")
        .defaultValue(1.8)
        .range(0, 6)
        .sliderRange(0.25, 3)
        .build());

    private final Setting<Integer> tridentUseDelay = sgGeneral.add(new IntSetting.Builder()
        .name("trident-use-delay")
        .description("")
        .defaultValue(6)
        .range(1, 200)
        .sliderRange(1, 40)
        .build());

    private final Setting<Integer> badTridentLimit = sgGeneral.add(new IntSetting.Builder()
        .name("bad-trident-uses-limit")
        .description("The limit if uses the trident after which uses the firework.")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 8)
        .build());

    private final Setting<Integer> tridentUpOffset = sgGeneral.add(new IntSetting.Builder()
        .name("trident-up-offset")
        .description("")
        .defaultValue(2)
        .range(0, 10)
        .sliderRange(0, 10)
        .build());


    private final Setting<Integer> stackingDelay = sgGeneral.add(new IntSetting.Builder()
        .name("stacking-actions-delay")
        .description("")
        .defaultValue(4)
        .range(0, 2000)
        .sliderRange(0, 10)
        .build());

    private final Setting<Boolean> overflowLogout = sgGeneral.add(new BoolSetting.Builder()
        .name("log-if-inventory-full")
        .defaultValue(true)
        .build());

    private final Setting<Integer> minDurSwap = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-equipped-durability")
        .description("")
        .defaultValue(20)
        .range(10, 382)
        .sliderRange(10, 431)
        .build());

    private final Setting<Integer> toSwapMinDur = sgGeneral.add(new IntSetting.Builder()
        .name("new-minimum-durability")
        .description("")
        .defaultValue(200)
        .range(10, 431)
        .sliderRange(40, 381)
        .build());

    private final Setting<Integer> minSumLogout = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-all-durability-logout")
        .description("")
        .defaultValue(200)
        .range(10, 16000)
        .sliderRange(80, 1000)
        .build());

    private final Setting<Integer> droppedShulkerCheckDistance = sgGeneral.add(new IntSetting.Builder()
        .name("dropped-shulker-check-distance")
        .description("")
        .defaultValue(10)
        .range(1, 10000)
        .sliderRange(1, 30)
        .build());

    private final Setting<Integer> shulkerPlaceDistance = sgGeneral.add(new IntSetting.Builder()
        .name("shulker-place-distance")
        .description("")
        .defaultValue(3)
        .range(1, 10)
        .sliderRange(1, 6)
        .build());

    private final Setting<Integer> shulkerMaxSlots = sgGeneral.add(new IntSetting.Builder()
        .name("shulker-maximum-slots")
        .description("")
        .defaultValue(26)
        .range(1, 27)
        .sliderRange(1, 26)
        .build());

    private final Setting<Integer> droppedElytraCheckDistance = sgGeneral.add(new IntSetting.Builder()
        .name("dropped-elytra-check-distance")
        .description("")
        .defaultValue(10)
        .range(1, 10000)
        .sliderRange(1, 30)
        .build());

    private final Setting<Integer> landingMinimumHeight = sgGeneral.add(new IntSetting.Builder()
        .name("landing-height-pitch-switch")
        .description("")
        .defaultValue(50)
        .range(1, 10000)
        .sliderRange(20, 70)
        .build());

    private final Setting<Integer> landingPitch = sgGeneral.add(new IntSetting.Builder()
        .name("landing-pitch-after-switch")
        .description("")
        .defaultValue(-20)
        .range(-90, 90)
        .sliderRange(-25, -10)
        .build());

    private final Setting<Integer> brewingStandIgnoresDistance = sgGeneral.add(new IntSetting.Builder()
        .name("brewing-stand-ignoring-distance")
        .description("")
        .defaultValue(40)
        .range(0, 1000)
        .sliderRange(20, 100)
        .build());

    private final Setting<Integer> minimumHeightOverBrewingStandOffset = sgGeneral.add(new IntSetting.Builder()
        .name("landing-height-over-brewing-stand-offset")
        .description("")
        .defaultValue(20)
        .range(-1000, 1000)
        .sliderRange(10, 100)
        .build());

    private final Setting<Integer> brewingStandHeightPitchSwitch = sgGeneral.add(new IntSetting.Builder()
        .name("change-pitch-height-on-brewing-stand")
        .description("")
        .defaultValue(50)
        .range(-1000, 1000)
        .sliderRange(10, 100)
        .build());

    private final Setting<Integer> chestIgnoresDistance2D = sgGeneral.add(new IntSetting.Builder()
        .name("chest-ignoring-XZ-distance")
        .description("")
        .defaultValue(60)
        .range(0, 1000)
        .sliderRange(20, 100)
        .build());

    private final Setting<Integer> minimumHeightOverChest = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-height-over-chest-checking")
        .description("")
        .defaultValue(110)
        .range(0, 1000)
        .sliderRange(100, 300)
        .build());

    private final Setting<Integer> minimumStrictFlyHeight = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-height-strict-fly")
        .description("")
        .defaultValue(200)
        .range(100, 1000)
        .sliderRange(150, 400)
        .build());

    private final Setting<Integer> strictFlyPitch = sgGeneral.add(new IntSetting.Builder()
        .name("pitch-strict-fly")
        .description("")
        .defaultValue(5)
        .range(-90, 90)
        .sliderRange(30, -45)
        .build());


    public ElytraFinder() {
        super(Categories.Misc, "elytra-finder", "Automatically finds and looting elytras in the End.");
    }

    private final AutoEat autoEat = Modules.get().get(AutoEat.class);

    private Stage stage = Stage.Fly;
    private StackingStage stackingStage = StackingStage.MoveSlot;
    private LootingStage lootingStage = LootingStage.BreakFrame;
    private TakeoffStage takeoffStage = TakeoffStage.BreakUp;
    private FlyStage flyStage = FlyStage.FlyStrict;
    private int stackingActionDelay = 0;
    private Entity frame;
    private boolean going = false;
    private BlockPos lastPos;
    private int posCounter = 0;
    private BlockPos goingPos;
    private BlockPos shulkerPos;
    private int shulkerSlot = -1;
    private int putted = 0;
    private int tridentDelay = 0;
    private boolean placeChanged = false;
    private ScreenHandler handler;
    private Direction shipDirection;
    private Direction flyDirection;
    private BlockPos chestPosition;
    private BlockPos brewingStandPos;
    private final List<BlockPos> checkedChests = new ArrayList<>();
    private final List<BlockPos> checkedBrewingStands = new ArrayList<>();
    private int flightHeight = 200;
    private int flyPitch = 10;
    private double beforeTridentHeight = 0;
    private int badTridentUses = 0;
    private int fireworkCounter = 0;


    private void resetValues() {
        stage = Stage.Fly;
        stackingStage = StackingStage.MoveSlot;
        lootingStage = LootingStage.BreakFrame;
        takeoffStage = TakeoffStage.BreakUp;
        flyStage = FlyStage.FlyStrict;
        frame = null;
        lastPos = null;
        posCounter = 0;
        goingPos = null;
        shulkerPos = null;
        shulkerSlot = -1;
        putted = 0;
        tridentDelay = 0;
        placeChanged = false;
        handler = null;
        chestPosition = null;
        brewingStandPos = null;
        flightHeight = 300;
        flyPitch = 10;
        flyDirection = null;
        beforeTridentHeight = 0;
        badTridentUses = 0;
        fireworkCounter = 0;
        hold(false);
        stop();
    }

    @Override
    public void onDeactivate() {
        resetValues();
    }

    @EventHandler
    private void tick(TickEvent.Pre e) {
        if (mc.player.getY() <= logoutHeight.get()) {
            mc.player.setPitch(-25);
            log("§l[Elytra Finder] Player lower " + logoutHeight.get() + " height!");
            return;
        }
        if (getAllDurability() <= minSumLogout.get()) {
            log("§l[Elytra Finder] Elytra durability low!");
            return;
        }
        if (getEquippedDurability() <= minDurSwap.get()) {
            swapCharged(toSwapMinDur.get());
            return;
        }
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) {
            mc.player.closeHandledScreen();
            return;
        }
        dropSelected(dropItems.get());

        // Refresh baritone if he was stopped
        if (going) {
            if (lastPos != null) {
                if (mc.player.getBlockPos().getX() == lastPos.getX() && mc.player.getBlockPos().getZ() == lastPos.getZ())
                    posCounter++;
                else posCounter = 0;
            }
            if (posCounter >= baritoneRefreshCounter.get()) {
                posCounter = 0;
                stop();
                goTo(goingPos);
                return;
            }
            lastPos = mc.player.getBlockPos();
        } else if (posCounter != 0) posCounter = 0;

        // Offing jumpKey
        if (mc.options.jumpKey.isPressed()) {
            mc.options.jumpKey.setPressed(false);
            return;
        }

        switch (stage) {
            case Takeoff -> {
                mc.player.setPitch(-90);

                // Centering and evading
                Vec3d plPos = mc.player.getPos();
                BlockPos offset = evadeOffset();
                plPos = plPos.add(offset.getX(), 0, offset.getZ());
                if (!playerInOneBlock(plPos) || offset.getX() > 0 || offset.getZ() > 0) {

                    // From vector surr
                    double deltaX = clamp(Vec3d.ofCenter(new BlockPos(plPos)).getX() - mc.player.getX(), -0.05, 0.05);
                    double deltaZ = clamp(Vec3d.ofCenter(new BlockPos(plPos)).getZ() - mc.player.getZ(), -0.05, 0.05);
                    double speed = centeringSpeed.get() + 1.0D;
                    double x = deltaX * speed;
                    double z = deltaZ * speed;
                    mc.player.setVelocity(x, mc.player.getVelocity().y, z);

                }

                switch (takeoffStage) {
                    case BreakUp -> {
                        for (int y = 1; y < 7; y++) {
                            BlockPos check = mc.player.getBlockPos().up(y);
                            if (!isAir(check)) {
                                breakBlock(check, true);
                                return;
                            }
                        }
                        takeoffStage = TakeoffStage.Upping;
                    }
                    case Upping -> {
                        if (mc.player.isOnGround() || !mc.player.isFallFlying()) {
                            mc.options.jumpKey.setPressed(true);
                            return;
                        }
                        if (!autoEat.eating) swapToItem(Items.TRIDENT);
                        if (mc.player.getY() >= 310) {
                            takeoffStage = TakeoffStage.BreakUp;
                            stage = Stage.Fly;
                            tridentDelay = 0;
                            flyPitch = -5;

                            hold(false);
                            return;
                        }

                        if (beforeTridentHeight > 0) {
                            if (mc.player.getY() - beforeTridentHeight - tridentUpOffset.get() <= 0) badTridentUses++;
                            else badTridentUses = 0;
                            beforeTridentHeight = 0;
                        }

                        if (fireworkCounter > 0) {
                            fireworkCounter--;
                            return;
                        }
                        if (mc.player.getY() <= logoutHeight.get() + 20) {
                            info("USED_ROCKET_ON_EXTREME_HEIGHT!");
                            useItem(Items.FIREWORK_ROCKET);
                            fireworkCounter = fireworkDelay.get();
                            return;
                        }
                        if (badTridentUses >= badTridentLimit.get()) {
                            badTridentUses = 0;
                            info("USED_ROCKET");
                            useItem(Items.FIREWORK_ROCKET);
                            fireworkCounter = fireworkDelay.get();
                            return;
                        }

                        if (tridentDelay > 0) {
                            tridentDelay--;
                            return;
                        }
                        if (mc.player.getItemUseTime() == 0) hold(true);
                        if (mc.player.getItemUseTime() > 10) {
                            beforeTridentHeight = mc.player.getY();
                            hold(false);
                            tridentDelay = tridentUseDelay.get();
                        }

                    }
                }
            }

            case Fly -> {
                if (flyDirection == null) {
                    if (shipDirection != null) {
                        if (mc.player.getX() > 0 && shipDirection == Direction.EAST) flyDirection = Direction.EAST;
                        else if (mc.player.getX() > 0 && shipDirection == Direction.WEST) flyDirection = Direction.EAST;
                        else if (mc.player.getX() < 0 && shipDirection == Direction.EAST) flyDirection = Direction.WEST;
                        else if (mc.player.getX() < 0 && shipDirection == Direction.WEST) flyDirection = Direction.WEST;
                        else if (mc.player.getZ() > 0 && shipDirection == Direction.NORTH)
                            flyDirection = Direction.SOUTH;
                        else if (mc.player.getZ() > 0 && shipDirection == Direction.SOUTH)
                            flyDirection = Direction.SOUTH;
                        else if (mc.player.getZ() < 0 && shipDirection == Direction.SOUTH)
                            flyDirection = Direction.NORTH;
                        else if (mc.player.getZ() < 0 && shipDirection == Direction.NORTH)
                            flyDirection = Direction.NORTH;
                    } else flyDirection = defaultDirection.get();
                }
                if (flyDirection == null) flyDirection = defaultDirection.get();
                if (shipDirection != null) shipDirection = null;

                if (mc.player.isOnGround()) {
                    stage = Stage.Takeoff;
                    return;
                } else if (!mc.player.isFallFlying()) {
                    mc.options.jumpKey.setPressed(true);
                    return;
                }

                if (mc.player.getItemUseTime() == 0 && mc.player.getY() < flightHeight) {
                    stage = Stage.Takeoff;
                    return;
                }
                if (flyStage != FlyStage.FlyToElytras) mc.player.setPitch(flyPitch);

                if (getSpeed() <= minFlySpeed.get() && flyStage == FlyStage.FlyStrict && mc.player.getItemUseTime() == 0) {
                    if (tridentDelay > 0) {
                        tridentDelay--;
                        return;
                    }
                    tridentDelay = tridentUseDelay.get();
                    hold(true);
                }
                if (mc.player.getItemUseTime() > 10) hold(false);

                switch (flyStage) {
                    case FlyStrict -> {
                        flyPitch = strictFlyPitch.get();
                        mc.player.setYaw(flyDirection.asRotation());
                        flightHeight = minimumStrictFlyHeight.get();

                        for (BlockEntity blockEntity : Utils.blockEntities()) {
                            if (!(blockEntity instanceof ChestBlockEntity chest)) continue;
                            if (checkedChests.contains(chest.getPos())) continue;
                            chestPosition = chest.getPos();
                            flyStage = FlyStage.CheckChest;
                            break;
                        }
                    }

                    case CheckChest -> {
                        flyPitch = 30;
                        mc.player.setYaw((float) Rotations.getYaw(chestPosition));
                        flightHeight = minimumHeightOverChest.get();

                        for (BlockEntity blockEntity : Utils.blockEntities()) {
                            if (!(blockEntity instanceof BrewingStandBlockEntity brewingStand)) continue;
                            if (checkedBrewingStands.contains(brewingStand.getPos())) continue;
                            brewingStandPos = brewingStand.getPos();
                            flyStage = FlyStage.FlyToElytras;
                            return;
                        }

                        if (horizontalDistance(Vec3d.ofCenter(chestPosition), mc.player.getPos())
                            < chestIgnoresDistance2D.get() * chestIgnoresDistance2D.get()) {
                            checkedChests.add(chestPosition);
                            flyStage = FlyStage.FlyStrict;
                            chestPosition = null;
                        }
                    }

                    case FlyToElytras -> {
                        if (brewingStandPos == null) {
                            flyStage = FlyStage.FlyStrict;
                            return;
                        }

                        double YDistance = Math.abs(brewingStandPos.getY() - mc.player.getY());
                        if (YDistance > brewingStandHeightPitchSwitch.get())
                            mc.player.setPitch((float) Rotations.getPitch(brewingStandPos));
                        else mc.player.setPitch(landingPitch.get());

                        mc.player.setYaw((float) Rotations.getYaw(brewingStandPos));
                        flightHeight = brewingStandPos.getY() + minimumHeightOverBrewingStandOffset.get();

                        for (Entity entity : mc.world.getEntities()) {
                            if (!(entity instanceof ItemFrameEntity checkingFrame)) continue;
                            if (checkingFrame.getHeldItemStack().getItem() != Items.ELYTRA) continue;
                            frame = entity;
                            stage = Stage.Landing;
                            flyStage = FlyStage.FlyStrict;
                            checkedBrewingStands.add(brewingStandPos);
                            brewingStandPos = null;
                            shipDirection = setShipDirect(frame);
                            return;
                        }

                        if (brewingStandPos.getSquaredDistance(mc.player.getPos()) < Math.pow(brewingStandIgnoresDistance.get(), 2)) {
                            flyStage = FlyStage.FlyStrict;
                            checkedBrewingStands.add(brewingStandPos);
                            brewingStandPos = null;
                        }
                    }
                }
            }

            case Landing -> {
                hold(false);
                if (!mc.player.isOnGround() && !mc.player.isFallFlying()) {
                    mc.options.jumpKey.setPressed(true);
                    return;
                }
                mc.player.setYaw((float) Rotations.getYaw(frame.getPos()));
                double YDistance = Math.abs(frame.getY() - mc.player.getY());
                if (YDistance > landingMinimumHeight.get())
                    mc.player.setPitch((float) Rotations.getPitch(frame.getPos()));
                else mc.player.setPitch(landingPitch.get());
                if (mc.player.isOnGround()) stage = Stage.Looting;
            }

            case Looting -> {
                switch (lootingStage) {
                    case BreakFrame -> {
                        if (frame == null) {
                            resetValues();
                            return;
                        }
                        if (frame.getBlockPos().getSquaredDistance(mc.player.getBlockPos()) > Math.pow(20, 2)) {
                            resetValues();
                            return;
                        }
                        if (frame.isAlive()) {
                            goingPos = frame.getBlockPos().add(0, -1, 0);
                            if (mc.player.getBlockPos().getSquaredDistance(goingPos) <= 1) {
                                if (mc.player.getAttackCooldownProgress(0.5f) >= 1) {
                                    mc.interactionManager.attackEntity(mc.player, frame);
                                    mc.player.swingHand(Hand.MAIN_HAND);
                                }
                            } else goTo(goingPos);
                        } else {
                            lootingStage = LootingStage.CollectElytra;
                            frame = null;
                        }
                    }

                    case CollectElytra -> {
                        BlockPos droppedElytra = null;
                        for (Entity entity : mc.world.getEntities()) {
                            if (!(entity instanceof ItemEntity item)) continue;
                            if (item.getStack().getItem() != Items.ELYTRA) continue;
                            if (entity.getBlockPos().getSquaredDistance(mc.player.getBlockPos()) > Math.pow(droppedElytraCheckDistance.get(), 2))
                                continue;
                            droppedElytra = entity.getBlockPos();
                            break;
                        }
                        if (droppedElytra != null) {
                            goingPos = droppedElytra;
                            goTo(droppedElytra);

                            if (droppedElytra.getSquaredDistance(mc.player.getBlockPos()) <= 2) stop();

                        } else {
                            stop();
                            if (countFullSlots() >= maxInvSizeStackEnabling.get()) {
                                stage = Stage.Stack;
                                lootingStage = LootingStage.BreakFrame;
                                stackingStage = StackingStage.MoveSlot;

                            } else resetValues();
                        }
                    }
                }
            }

            case Stack -> {
                if (stackingStage != StackingStage.BreakShulker) {
                    if (stackingActionDelay > 0) {
                        stackingActionDelay--;
                        return;
                    } else stackingActionDelay = stackingDelay.get();
                }

                switch (stackingStage) {
                    case MoveSlot -> {
                        if (shulkerSlot == -1) shulkerSlot = findEmptyShulker(shulkerMaxSlots.get());
                        if (shulkerSlot == -1) {
                            if (overflowLogout.get()) log("§lInventory is full!");
                            else error("Inventory is full! Disabling!");
                            toggle();
                            return;
                        }
                        move(shulkerSlot, shulkerSwapSlot.get() - 1);
                        stackingStage = StackingStage.Place;
                    }

                    case Place -> {
                        if (shulkerPos == null) shulkerPos = mc.player.getBlockPos().up();
                        else {
                            if (!canPlace(shulkerPos, true) && !placeChanged) {
                                shulkerPos = mc.player.getBlockPos();
                                switch (shipDirection) {
                                    case EAST -> shulkerPos = shulkerPos.west(shulkerPlaceDistance.get());
                                    case WEST -> shulkerPos = shulkerPos.east(shulkerPlaceDistance.get());
                                    case NORTH -> shulkerPos = shulkerPos.south(shulkerPlaceDistance.get());
                                    case SOUTH -> shulkerPos = shulkerPos.north(shulkerPlaceDistance.get());
                                }
                                placeChanged = true;
                            }
                            if (!canPlace(shulkerPos, false)) {
                                breakBlock(shulkerPos, true);
                                return;
                            }
                            if (!canPlace(shulkerPos.up(), false)) {
                                breakBlock(shulkerPos.up(), true);
                                return;
                            }

                            place(shulkerPos.up(), findInHotbar(itemStack -> getBlockFromItem(itemStack.getItem()) instanceof ShulkerBoxBlock), true, 90, true, true);
                            stackingStage = StackingStage.Open;
                            shulkerSlot = -1;
                        }
                    }

                    case Open -> {
                        if (handler == null) {
                            openStorage(shulkerPos);
                            openStorage(shulkerPos.up());
                            stackingStage = StackingStage.OpenHandler;
                        } else mc.player.currentScreenHandler.close(mc.player);
                    }

                    case OpenHandler -> {
                        handler = mc.player.currentScreenHandler;
                        if (!(handler instanceof ShulkerBoxScreenHandler)) {
                            stackingStage = StackingStage.Open;
                            return;
                        }
                        stackingStage = StackingStage.Put;
                    }

                    case Put -> {
                        int slot = getWithMinDurability();
                        if (slot == -1) {
                            stackingStage = StackingStage.CloseHandler;
                            return;
                        }
                        putInStorage(handler, slot);
                        putted++;
                        if (putted < putAmount.get()) return;

                        putted = 0;
                        stackingStage = StackingStage.CloseHandler;
                    }

                    case CloseHandler -> {
                        mc.player.closeHandledScreen();
                        handler = null;
                        stackingStage = StackingStage.BreakShulker;
                    }

                    case BreakShulker -> {
                        if (mc.player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
                            stackingStage = StackingStage.CloseHandler;
                            return;
                        }
                        if (!isAir(shulkerPos)) {
                            breakBlock(shulkerPos, true);
                            return;
                        }
                        stackingStage = StackingStage.CollectShulker;
                        shulkerPos = null;
                    }

                    case CollectShulker -> {
                        BlockPos droppedShulker = null;
                        for (Entity entity : mc.world.getEntities()) {
                            if (!(entity instanceof ItemEntity item)) continue;
                            if (!(getBlockFromItem(item.getStack().getItem()) instanceof ShulkerBoxBlock))
                                continue;
                            if (entity.getBlockPos().getSquaredDistance(mc.player.getBlockPos()) > Math.pow(droppedShulkerCheckDistance.get(), 2))
                                continue;
                            droppedShulker = entity.getBlockPos();
                            break;
                        }

                        if (droppedShulker != null) {
                            goingPos = droppedShulker;
                            goTo(droppedShulker);
                            if (droppedShulker.getSquaredDistance(mc.player.getBlockPos()) <= 0.5) stop();
                        } else resetValues();
                    }
                }

            }
        }

    }

    private void hold(boolean pressed) {
        mc.options.useKey.setPressed(pressed);
    }

    private BlockPos evadeOffset() {
        if (mc.player.isOnGround()) return new BlockPos(0, 0, 0);
        BlockPos blocked = null;
        for (int y = 2; y < 30; y++) {
            BlockPos check = mc.player.getBlockPos().up(y);
            if (!isAir(check)) {
                blocked = check;
                break;
            }
        }

        if (blocked == null) return new BlockPos(0, 0, 0);

        for (int i = -50; i <= 50; i++) {
            if (isAir(blocked.east(i))) return blocked.east(i);
            else if (isAir(blocked.west(i))) return blocked.west(i);
            else if (isAir(blocked.south(i))) return blocked.south(i);
            else if (isAir(blocked.north(i))) return blocked.north(i);
        }
        return new BlockPos(0, 0, 0);
    }

    private boolean playerInOneBlock(Vec3d pos) {
        double xo = pos.x - (int) pos.x;
        double zo = pos.z - (int) pos.z;
        if (xo < 0) xo++;
        if (zo < 0) zo++;
        return xo >= 0.3 && xo <= 0.7 && zo >= 0.3 && zo < 0.7;
    }

    private Direction setShipDirect(Entity frame) {
        switch (frame.getMovementDirection()) {
            case EAST -> {
                return Direction.WEST;
            }
            case WEST -> {
                return Direction.EAST;
            }
            case NORTH -> {
                return Direction.SOUTH;
            }
            case SOUTH -> {
                return Direction.NORTH;
            }
        }
        return null;
    }

    private int getSpeed() {
        double x = mc.player.getVelocity().getX();
        double z = mc.player.getVelocity().getZ();
        double speed = Math.sqrt(x * x + z * z);
        return (int) (speed * 20);
    }

    private void goTo(BlockPos pos) {
        if (going) return;
        if (pos == null) return;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        going = true;
    }

    private void stop() {
        if (!going) return;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
        going = false;
    }

    public void log(String reason) {
        mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.of(reason)));
    }

    public int getEquippedDurability() {
        ItemStack itemStack = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        return itemStack.getItem() == Items.ELYTRA ? getMaxDurability() - itemStack.getDamage() : 0;
    }

    public int getAllDurability() {
        int durabilityPoints = 0;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i <= 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (!(stack.getItem() instanceof ElytraItem)) continue;
            durabilityPoints += getMaxDurability() - stack.getDamage();
        }

        return durabilityPoints + getEquippedDurability();
    }

    public void dropSelected(List<Item> items) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i <= 35; i++) {
            ItemStack itemStack = inv.getStack(i);
            if (items.contains(itemStack.getItem())) {
                InvUtils.drop().slot(i);
                return;
            }
        }
    }

    public boolean swapCharged(int minDurability) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i <= 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (!(stack.getItem() instanceof ElytraItem)) continue;
            if (getMaxDurability() - stack.getDamage() < minDurability) continue;
            InvUtils.move().from(i).toArmor(2);
            return true;

        }
        return false;
    }

    public int getWithMinDurability() {
        Inventory inv = mc.player.getInventory();
        List<Integer> elytrasDamages = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i <= 36; i++) {
            ItemStack stack = inv.getStack(i);
            if (!(stack.getItem() instanceof ElytraItem)) continue;
            slots.add(i);
            elytrasDamages.add(stack.getDamage());
        }

        if (slots.isEmpty()) return -1;
        int slot = slots.get(elytrasDamages.indexOf(Collections.max(elytrasDamages)));

        return slot;
    }

    public static int getMaxDurability() {
        return 432;
    }

    public boolean useItem(Item item) {
        int mainSlot = mc.player.getInventory().selectedSlot;
        FindItemResult findedItem = InvUtils.find(item);
        if (!findedItem.found()) return false;
        int fireworkSlot = findedItem.slot();
        InvUtils.move().from(fireworkSlot).to(mainSlot);
        if (mc.player.getMainHandStack().getItem() == item)
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        InvUtils.move().from(mainSlot).to(fireworkSlot);
        return true;
    }

    public boolean isAir(BlockPos blockPos) {
        return mc.world != null && mc.world.getBlockState(blockPos).isAir();
    }

    public boolean swapToItem(Item item) {
        if (mc.player.getMainHandStack().getItem() == item) return true;
        FindItemResult findItem = InvUtils.findInHotbar(item);
        if (!findItem.found()) return false;
        InvUtils.swap(findItem.slot(), false);
        return true;
    }

    public static boolean move(int from, int to) {
        if (from < 0 || to < 0 || from == to) return false;
        InvUtils.move().from(from).to(to);
        return true;
    }

    public static double horizontalDistance(Vec3d vec1, Vec3d vec2) {
        double dx = vec1.x - vec2.x;
        double dz = vec1.z - vec2.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public int findEmptyShulker(int maxFullSlots) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            DefaultedList<ItemStack> shulker = getShulkerBySlot(inv, i);
            if (shulker == null) continue;
            if (27 - countEmptySlotsInShulker(shulker) <= maxFullSlots) return i;
        }
        return -1;
    }

    public int countEmptySlotsInShulker(DefaultedList<ItemStack> shulker) {
        int emptySlots = 0;
        for (ItemStack shulkerStack : shulker) if (shulkerStack.isEmpty()) emptySlots++;
        return emptySlots;
    }

    public DefaultedList<ItemStack> getShulkerBySlot(Inventory inv, int slot) {
        ItemStack stack = inv.getStack(slot);
        if (stack == null || !(Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock)) return null;
        DefaultedList<ItemStack> shulker = DefaultedList.ofSize(27, ItemStack.EMPTY);
        NbtCompound compoundTag = stack.getSubNbt("BlockEntityTag");
        if (compoundTag == null) return shulker;
        Inventories.readNbt(compoundTag, shulker);
        return shulker;
    }


    public int countFullSlots() {
        int slots = 0;
        for (int i = 0; i <= 35; i++)
            if (!mc.player.getInventory().getStack(i).isEmpty()) slots++;
        return slots;
    }

    public boolean putInStorage(ScreenHandler handler, int slot) {
        if (slot < 0) return false;
        slot = correctSlot(slot);
        if (!checkHandler(handler)) return false;
        int offset = getRows(handler) * 9;
        InvUtils.quickMove().slotId(offset + slot);
        return true;
    }

    public boolean checkHandler(ScreenHandler handler) {
        if (handler == null) return false;
        return handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler;
    }

    public int getRows(ScreenHandler handler) {
        return (handler instanceof GenericContainerScreenHandler ? ((GenericContainerScreenHandler) handler).getRows() : 3);
    }

    public int correctSlot(int slot) {
        if (slot >= 9 && slot <= 35) return slot - 9;
        else return slot + 27;
    }

    public void openStorage(BlockPos pos) {
        BlockHitResult BHR = new BlockHitResult(new Vec3d(pos.getX(), pos.getY(), pos.getZ()), Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, BHR);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

}

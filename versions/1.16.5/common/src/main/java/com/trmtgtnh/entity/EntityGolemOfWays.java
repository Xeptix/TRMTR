package com.trmtgtnh.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.Level;

import com.trmtgtnh.config.TrmtConfig;

/**
 * The Golem of Ways: an earthen keeper that holds a stretch of ground at the wear you asked for.
 *
 * <p>
 * An {@link net.minecraft.world.entity.PathfinderMob} rather than an iron golem, deliberately. Vanilla's
 * golem carries a village's worth of behaviour - it looks for villages, it picks fights, it is
 * owned by a settlement - and none of that is wanted here; what is wanted is the walking and the
 * pathing, which is the creature part. It is not an {@code Enemy} either, so every "is this a
 * monster" test in the pack answers no about it without having to be told.
 *
 * <p>
 * It does fight, but only on its own narrow terms and only while it is carrying a tamper: it hits
 * back at whatever hit it, and it steps in front of a player or a villager a monster has gone for.
 * Empty-handed it does the opposite and walks away. See {@link GolemCombat}, which holds all of it.
 *
 * <p>
 * Everything it remembers rides in its own NBT: where it works and how far, what wear each kind of
 * ground should be held at, the tools and material it has been given, its upgrade, and who built
 * and last instructed it. What the client needs live goes through the data watcher - which upgrade
 * is fitted, because that decides what it looks like and what it is called, and one byte saying
 * what it is doing this moment and whether it has anything to do it with.
 */
public class EntityGolemOfWays extends PathfinderMob implements net.minecraft.world.WorldlyContainer {

    /** Watched, because the renderer and the name both follow it. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WATCH_UPGRADE = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** Watched so a tooltip can answer "whose golem is this" without asking the server. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> WATCH_SUMMONER = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<String> WATCH_CONFIGURER = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.STRING);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WATCH_RADIUS = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /**
     * What it is doing and what it is carrying, in one byte.
     *
     * <p>
     * Two bits for the kind of work in hand, then one each for "it has a tamper", "it has ground
     * to mend with", "it has been told to hold something" and "it is running from something". The
     * flags are the reason this exists at all: none of the storage and none of the orders ever
     * reach a client, so anything on that side that walked the slots or read the orders would have
     * called a fully stocked golem under full instruction empty and idle. The server, which knows,
     * answers here.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> WATCH_STATE = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    /**
     * Which upgrades an unstable one is carrying this minute, as a mask of ordinals.
     *
     * <p>
     * Watched, because everything that reads it is on the client: the name over its head, the
     * screen, and the tooltip. Rolled on the server and never anywhere else, so the two sides can
     * never disagree about what a golem is currently able to do.
     *
     * <p>
     * Not saved. A part that reshuffles itself every minute has nothing worth restoring, and the
     * first roll after a load happens on the tick it wakes up - which is the same thing that would
     * have happened a minute later anyway.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WATCH_UNSTABLE = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private int unstableRolledAt;

    /**
     * Where its home is, published so its own screen can show it.
     *
     * <p>
     * Three watched values rather than one packed one, because a coordinate does not fit in
     * anything smaller than an int and two of the three are unbounded. They change when somebody
     * moves the golem and never otherwise, so three that sit still cost less than one that has to
     * be unpacked on every read.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WATCH_HOME_X = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WATCH_HOME_Y = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> WATCH_HOME_Z = net.minecraft.network.syncher.SynchedEntityData
        .defineId(EntityGolemOfWays.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /**
     * The entity status the server sends when it swings, and the client turns into an animation.
     *
     * <p>
     * The iron golem's own number and the iron golem's own mechanism, because the ordinary one does
     * not work for anything in this family. Vanilla only advances a swing's animation counter for
     * players and for {@code EntityMob}, and this is neither, so a swing raised the usual way would
     * be a packet a tick for a picture that never moves.
     */
    private static final byte STATUS_SWING = 4;

    /** How many tools and how much material it can hold without the storage upgrade. */
    public static final int BASE_SLOTS = 16;

    /** With it. */
    public static final int DEEP_SLOTS = 64;

    /**
     * The name and identity of the health the stout upgrade adds.
     *
     * <p>
     * A modifier rather than a second base value, so the sixty a pack sets stays the number this
     * is calculated from, and taking the part out takes the health with it. Saved with the rest of
     * the golem's attributes on purpose: the health it is standing at is restored after its
     * maximum is, and an unsaved modifier would mean a stout golem coming back from disk clamped
     * to half of what it had.
     */
    private static final java.util.UUID STOUT_ID = java.util.UUID.fromString("7c1e4a5e-1d43-4c1a-9d2e-3f6b8a0c5e11");

    private static final String STOUT_NAME = "trmtgtnh.golem.stout";

    /**
     * Where the upgrade lives, which is one past the widest the storage ever gets.
     *
     * <p>
     * A constant, and it has to be. This used to be one past however many slots the golem happened
     * to have at that moment, which meant that fitting the storage upgrade moved the upgrade's own
     * index from sixteen to sixty-four while the screen was open - and the slot the screen was
     * still asking about was by then an ordinary storage slot holding nothing. The upgrade
     * appeared to fall out of its own socket the instant it went in, and came back the moment the
     * window was closed and opened, which is exactly how it was reported. Pinning it past the
     * widest case costs the forty-eight empty references a plain golem was already carrying and
     * takes the index out of the argument entirely.
     */
    public static final int UPGRADE_SLOT = DEEP_SLOTS;

    /**
     * The slots a hopper or a dropper is offered, from any side, without and with the storage upgrade.
     *
     * <p>
     * Made once and handed out every time, because a hopper that moves nothing sets no cooldown and
     * asks again on the very next tick - so a golem standing beside one that holds something it
     * refuses is asked this twenty times a second for as long as the two stand there. Neither array
     * includes the upgrade's slot, which automation has no business with in either direction.
     */
    private static final int[] BASE_OPEN = slotsBelow(BASE_SLOTS);

    private static final int[] DEEP_OPEN = slotsBelow(DEEP_SLOTS);

    /** The slot numbers from nought up to, but not including, this many. */
    private static int[] slotsBelow(int count) {
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = i;
        }
        return slots;
    }

    private ItemStack[] inventory = new ItemStack[DEEP_SLOTS];

    private ItemStack upgrade;

    private int radius = 16;

    private int anchorX;
    private int anchorY;
    private int anchorZ;
    private boolean anchored;

    private String summonedBy = "";
    private String configuredBy = "";

    /**
     * The wear each family is to be held at, as a percentage of its whole run, or -1 to leave it
     * alone. Everything starts at -1: a golem that has just been built does nothing until it is
     * told what to do, which is the only safe default for something that rewrites ground.
     */
    private final byte[] targets = new byte[com.trmtgtnh.surface.SurfaceFamily.values().length];

    /**
     * Targets for particular blocks, which win over their family's.
     *
     * <p>
     * A family is the right unit for most orders - hold all grass at forty per cent - but not for
     * every one, because a family can hold several blocks somebody wants held differently. Cobble
     * and andesite are both stone; a plaza of one and a road of the other are not the same job.
     *
     * <p>
     * Keyed by registry name and metadata, insertion-ordered so the screen lists them the way they
     * were added, and capped: this is saved on the entity and read on every square the golem looks
     * at, and an unbounded map would make both worse for a case nobody has.
     */
    private final java.util.LinkedHashMap<String, Byte> blockTargets = new java.util.LinkedHashMap<String, Byte>();

    /** As many block overrides as one golem may hold. */
    public static final int MAX_BLOCK_TARGETS = 16;

    /** Where the round has got to, so each tick looks at somewhere new. */
    private int workCursor;

    /** Counts work done, so the sparing upgrade can take half the durability. */
    private int strokes;

    /**
     * The slot its best tamper sits in, or -1 for none.
     *
     * <p>
     * Worked out when what it is carrying changes rather than every time somebody asks, because
     * this is now the answer to "is it armed" and that question is asked by every task on every
     * evaluation. Every path that can change the storage refreshes it, so it is never a guess.
     */
    private int toolSlot = -1;

    /** The undamaged copy of that tool that goes in its hand, rebuilt only when the tool changes. */
    private ItemStack toolShown;

    /** Whether it is carrying ground it could mend with, worked out in the same pass. */
    private boolean mendingStock;

    /** Whether it has been told to hold anything, worked out in the same pass. */
    private boolean ordered;

    /** How long the tool stays up, counted down so a stroke that never comes cannot strand it. */
    /**
     * The reinforcing material this golem has swallowed and not yet laid.
     *
     * <p>
     * One stack rather than a queue: what matters is how many mouthfuls it owes and what to hand
     * back if it cannot place one, and a golem fed two different materials at once can honestly
     * give either back. Held outside the storage so that feeding it never competes with the tools
     * and mending stock a player put there on purpose.
     */
    private ItemStack masonry;

    /**
     * One entry a chewed-through mouthful: what would still be owed if the golem died holding it.
     *
     * <p>
     * A list rather than a count, and the entries matter as much as the length. A mouthful that came
     * in a container has already had that container handed back, so nothing more is owed for it and
     * its entry is null. A mouthful of something with no container - obsidian, and whatever else a
     * pack names - had nothing handed back, so the block itself is exactly what is owed and it is
     * kept here to be dropped if the golem is killed. Telling those two apart is the whole reason
     * this is not an integer: destroying somebody's obsidian because the arithmetic could not
     * distinguish it from a bucket of concrete is not a rounding error.
     *
     * <p>
     * Its length is the number of mouthfuls ready to lay, so there is one source of truth for that
     * and nothing to keep in step.
     */
    private final java.util.List<ItemStack> masonryOwed = new java.util.ArrayList<ItemStack>();

    /** Ticks left before it will look for somewhere to reinforce again. */
    private int masonrySulk;

    /** Ticks left of the mouthful being chewed, and the eased pose that follows it. */
    private int chewTicks;

    private float chewLean;

    private float prevChewLean;

    private int busyTicks;

    /**
     * The containers a settled golem last found around its anchor, and when it found them.
     *
     * <p>
     * Never saved. A container is a fact about the level rather than about the golem, and a list
     * restored from disk would be a list of inventories that no longer exist - so it is rebuilt on
     * the first stroke after a load, which costs one scan and cannot be wrong.
     */
    java.util.List<GolemStores.Store> stores;

    int storesFoundAt;

    /**
     * The kinds of worn ground the stores were last found unable to pay for.
     *
     * <p>
     * Never saved, for the same reason the list of stores is not: it describes the containers
     * around the golem as they were at one scan, and a golem coming back from disk scans again
     * anyway. See {@link com.trmtgtnh.util.RefusedFetches} for how long each refusal is believed.
     */
    final com.trmtgtnh.util.RefusedFetches refusedFetches = new com.trmtgtnh.util.RefusedFetches();

    /**
     * How many times what it carries has changed, as far as {@link #takeStock()} has been told.
     *
     * <p>
     * Never saved. It is only ever compared with itself within one life of the entity, to tell a
     * refusal for want of room that still stands from one the golem may since have made room for,
     * and a number that starts again at nought after a load is as good for that as any other.
     */
    private int stockChanges;

    private int busyKind = GolemCombat.BUSY_NONE;

    /** Whether it is running from something, which is the one new behaviour worth announcing. */
    private boolean fleeing;

    /** Ticks until it may swing again, so the chase task cannot decide how often it hits. */
    private int fightCooldown;

    /** How long it has held a target without landing anything on it. */
    private int stalemate;

    /** How long it will decline to pick another fight, after giving one up as hopeless. */
    private int giveUpTicks;

    /** How far through one blow the arm is, ticked on both sides because only the client reads it. */
    private int blowTicks;

    /** How far the free arm has come up, eased on both sides so the tool rises rather than appears. */
    private float heldRaise;

    private float prevHeldRaise;

    /**
     * The square it is walking to, when there is nothing to do where it stands.
     *
     * <p>
     * Not saved and not watched. It is a destination the work picks again every stroke, so a
     * golem coming back from disk simply picks a new one on its first round rather than
     * resuming a journey nobody remembers it starting.
     */
    private int workX;

    private int workY;

    private int workZ;

    private boolean walkingToWork;

    /**
     * The work it knows about, nearest home first.
     *
     * <p>
     * Made here and never saved, because it describes ground rather than the golem - and
     * ground somebody may have rebuilt while the chunk was away. One sweep on waking costs
     * less than trusting a description of a level that has moved on.
     */
    private final GolemTargets workList = new GolemTargets();

    /** Set when a mend was refused for want of the right block; cleared when one succeeds. */
    private boolean shortOfBlocks;

    /** Whoever has the golem's screen open, so anything it gives up can be handed over. */
    private Player viewer;

    /**
     * How far into its running posture it is, on the same easing as the arm.
     *
     * <p>
     * Eased rather than switched, because the model is one object drawn for every golem on the
     * screen and can hold no state of its own - anything that has to blend has to blend out here,
     * on the entity, where each golem has somewhere to keep it.
     */
    private float fleeLean;

    private float prevFleeLean;

    /**
     * How much of a fight it is in, and how much of a tamper it is holding, as the pose sees them.
     *
     * <p>
     * Both are yes-or-no facts, and the pose was reading them as such while everything around them
     * was eased - so a golem that stopped fighting and went back to its round moved its working arm
     * through forty-nine degrees between two frames, and one whose last tamper broke stopped its
     * idle stroke mid-lift. Neither is a large motion; both are instantaneous, which is what makes
     * them read as a glitch rather than as movement. The blend rates differ because the two events
     * do not feel the same: coming out of a fight is quick, and noticing you are holding a tool is
     * not.
     */
    private float fightLean;

    private float prevFightLean;

    private float armedShow;

    private float prevArmedShow;

    /**
     * The pose being shown, the pose wanted, and how long the wanted one has held.
     *
     * <p>
     * Two of them rather than one, because a pose has to finish fading out before the next begins:
     * crossing between two of these directly would swap a bowed head for a cast arm inside a frame.
     * The steady count is what stops a flag that flickers for one publish from being believed.
     */
    private int idleKind;

    private int idleWanted;

    private int idleSteady;

    private float idleShow;

    private float prevIdleShow;

    /**
     * Where in its tamping stroke it is, from nothing to one and round again.
     *
     * <p>
     * Accumulated rather than worked out from the age of the golem, and the difference matters
     * exactly once: fitting the upgrade that makes it work twice as often halves the length of the
     * stroke. Derived from age, that halving moves the whole phase at once and the arm arrives
     * somewhere else between two frames. Accumulated, the same change alters how fast the phase
     * advances and not where it currently is, which is what the change actually means.
     */
    private float strokePhase;

    private float prevStrokePhase;

    public EntityGolemOfWays(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        java.util.Arrays.fill(targets, (byte) -1);
        // setSize is gone. A hitbox is declared on the EntityType rather than set on the instance,
        // so the 1.4 by 2.7 that stood here is in ModEntities now - said once, where the type is
        // built, rather than in two places that have to agree.
        // Slow and deliberate. It is doing groundwork, not patrolling.
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, -1.0F);
        // Fitted in priority order, which is worth the care: the list is walked in the order it
        // was built when the AI decides what may start, so a task added out of order can run for
        // a moment beside one it is meant to be locked out of.
        goalSelector.addGoal(0, new FloatGoal(this));
        // Fighting and running away, both gated on whether it is holding a tamper, at one and two.
        // See GolemCombat for why they are fitted once and left in place rather than swapped as it
        // is armed and disarmed.
        GolemCombat.install(this);
        // Walks back when it finds itself outside the ground it keeps. The partner to holdGround
        // below: that one draws the boundary and this one is what brings a golem home which is
        // already on the wrong side of it, whether it strayed there, was carried, or ran.
        // Above walking home and well above wandering: idle wandering would otherwise carry it
        // off the very square it had just decided to go and mend.
        goalSelector.addGoal(3, new GolemWork.WalkToWork(this));
        goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D));
        goalSelector.addGoal(5, new RandomStrollGoal(this, 0.5D));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /**
     * The same four numbers, said to the registry rather than to the instance.
     *
     * <p>
     * They are supplied once per entity type now, before any golem exists, rather than being set on
     * each one as it is built - so this is static and {@code ModEntities} hands it over when it
     * declares the type. A golem built without it has no attributes at all and the game refuses to
     * spawn it, which is at least loud.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 60.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.18D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 64.0D);
        // No attack damage attribute, on purpose. The iron golem does not have one either: its
        // blow is a number in its own code rather than a stat, and so is this one, so nothing in
        // a pack can quietly turn a road-mender into a weapon.
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(WATCH_UPGRADE, Integer.valueOf(GolemUpgrade.NONE.ordinal()));
        entityData.define(WATCH_SUMMONER, "");
        entityData.define(WATCH_CONFIGURER, "");
        entityData.define(WATCH_RADIUS, Integer.valueOf(16));
        entityData.define(WATCH_STATE, Byte.valueOf((byte) 0));
        entityData.define(WATCH_UNSTABLE, Integer.valueOf(0));
        entityData.define(WATCH_HOME_X, Integer.valueOf(0));
        entityData.define(WATCH_HOME_Y, Integer.valueOf(0));
        entityData.define(WATCH_HOME_Z, Integer.valueOf(0));
    }

    /**
     * The two reads that need unboxing, so that every caller can go on saying what it means.
     *
     * <p>
     * The other edition asks its data watcher for an int or a byte by number; here each key carries its
     * own type, and these two put the answer back into the primitive the callers were written against.
     */
    private int intWatched(net.minecraft.network.syncher.EntityDataAccessor<Integer> key) {
        return entityData.get(key)
            .intValue();
    }

    private byte byteWatched(net.minecraft.network.syncher.EntityDataAccessor<Byte> key) {
        return entityData.get(key)
            .byteValue();
    }

    /**
     * Its two lists of goals, lent out so that {@link GolemCombat} can fit the fighting ones.
     *
     * <p>
     * Both were public fields in 1.12.2 and are protected on {@code Mob} here, which the golem can
     * reach and nothing in this package can. Lent rather than moved: where the fighting is fitted is
     * an argument about priority order, and that argument belongs beside the fighting.
     *
     * <p>
     * The order is worth the care the constructor takes over it. The list is walked in the order it
     * was built when the AI decides what may start, so a goal added out of order can run for a
     * moment beside one it is meant to be locked out of.
     */
    public net.minecraft.world.entity.ai.goal.GoalSelector goals() {
        return goalSelector;
    }

    public net.minecraft.world.entity.ai.goal.GoalSelector targets() {
        return targetSelector;
    }

    /**
     * Built, not bred, and never wandering off.
     *
     * <p>
     * The argument is how far away the player it is being asked about is, and it is ignored for the
     * same reason the answer was constant before there was one: this thing was placed deliberately
     * and nothing in the game is entitled to tidy it away.
     */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // The other edition overrides isAIEnabled here to turn its tasks on. 1.12.2 runs an entity's
    // tasks unless something asks it not to, so there is nothing left to say.

    /**
     * Sneak and right-click to open its orders.
     *
     * <p>
     * Sneaking rather than a plain click, so walking into one with a tamper in hand does not open a
     * window instead of doing whatever the tamper was for.
     */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player,
        net.minecraft.world.InteractionHand hand) {
        if (player == null) return net.minecraft.world.InteractionResult.PASS;
        if (!player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS;
        if (!TrmtConfig.golemEnabled) return net.minecraft.world.InteractionResult.SUCCESS;
        // Forge's gui handler addressed a screen by a block position and a golem has none, so that
        // edition sent the entity id in the x argument. There is no such handler here and no such
        // smuggling: GolemMenu is the seam, each loader opens a menu its own way, and the id travels
        // as an id. See GolemMenu for why this cannot be said in a module both loaders share.
        GolemMenu.open(player, this);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------
    // What it is called
    // ------------------------------------------------------------------

    /**
     * Its name follows the upgrade fitted to it.
     *
     * <p>
     * Plain it is the Golem of Ways; with an upgrade it earns that upgrade's epithet, and the
     * one that carries every upgrade at once earns the pun the whole set was named for.
     */
    @Override
    public net.minecraft.network.chat.Component getName() {
        // A custom name is already a component here, so it is handed straight back rather than being
        // flattened to a string and rebuilt - which would have thrown away whatever formatting
        // somebody had put on their name tag.
        if (hasCustomName()) return getCustomName();
        String name = com.trmtgtnh.util.Translate.get(fittedUpgrade().nameKey());
        return new net.minecraft.network.chat.TextComponent(
            fittedUpgrade() == GolemUpgrade.UNSTABLE ? corrupted(name, unstableMask()) : name);
    }

    // ------------------------------------------------------------------
    // What an unstable one happens to be, this minute
    // ------------------------------------------------------------------

    /** Which upgrades are answering yes right now, as a mask of ordinals. */
    public int unstableMask() {
        return intWatched(WATCH_UNSTABLE);
    }

    /**
     * Whether one particular upgrade is in force.
     *
     * <p>
     * Three answers folded into one question, and the order matters. The part itself always counts;
     * the bound version counts for everything; and the loose version counts for whatever it has
     * hold of at this moment. Every place that used to ask the enum now asks this instead, because
     * the enum is a constant and the answer stopped being one.
     */
    public boolean carries(GolemUpgrade which) {
        GolemUpgrade fitted = fittedUpgrade();
        if (fitted == which || fitted.isOmni()) return true;
        if (fitted != GolemUpgrade.UNSTABLE) return false;
        return (unstableMask() & (1 << which.ordinal())) != 0;
    }

    public boolean extendsRange() {
        return carries(GolemUpgrade.RANGE);
    }

    public boolean isFaster() {
        return carries(GolemUpgrade.SPEED);
    }

    public boolean sparesTools() {
        return carries(GolemUpgrade.SPARING);
    }

    public boolean sparesBlocks() {
        return carries(GolemUpgrade.FRUGAL);
    }

    public boolean widensStorage() {
        return carries(GolemUpgrade.DEEP);
    }

    public boolean standsLonger() {
        return carries(GolemUpgrade.STOUT);
    }

    public boolean hitsHarder() {
        return carries(GolemUpgrade.FIERCE);
    }

    public boolean usesStores() {
        return carries(GolemUpgrade.SETTLED);
    }

    /**
     * Holds a band of wear rather than a level, so its round cycles instead of settling.
     *
     * <p>
     * The only upgrade that makes the golem deliberately not hold still. Every other one makes it
     * better at keeping ground where it was put; this one turns the round into something that
     * breathes - worn down to one edge of a band, mended back up to the other, and round again -
     * which is what turns a supply of earth into a supply of what earth sheds.
     */
    public boolean farms() {
        return carries(GolemUpgrade.GREEN);
    }

    /**
     * Which edge of the band it is driving its round to at the moment.
     *
     * <p>
     * One bit, and it has to be a bit somewhere: a cycle needs to tell "at this level on the way
     * down" from "at this level on the way up", and no amount of looking at the square itself can
     * say which. Held by the golem rather than by the square because a round is the golem's, and
     * because a bit per square would be a bit this mod has not got - the erosion word is full.
     *
     * <p>
     * Saved, so a golem does not restart its round from the top every time the chunk comes back.
     */
    private boolean farmWearing = true;

    public boolean farmWearing() {
        return farmWearing;
    }

    /**
     * Whether anything has actually moved since the last time the golem stood back and looked.
     *
     * <p>
     * What tells a farming golem its round is finished. The obvious test - a sweep that finds
     * nothing left wanting doing - looks right and cannot work: the sweep rebuilds the list from
     * scratch every time, so a square the golem has tried and cannot afford is counted afresh on
     * every pass and the count never reaches nought. A round that ran out of dirt half way through
     * would breathe out once and hold its breath for ever.
     *
     * <p>
     * Progress is the honest question, and it answers both endings at once. Everything is where it
     * should be, or nothing left is affordable: either way the round has stopped moving, and a
     * round that has stopped moving is a round to turn.
     */
    private boolean movedSinceLook;

    public void noteMoved() {
        movedSinceLook = true;
    }

    /** Reads the flag and clears it, which is what makes it "since the last look". */
    public boolean movedSinceLook() {
        boolean moved = movedSinceLook;
        movedSinceLook = false;
        return moved;
    }

    /**
     * Whether a column is inside the ground this golem keeps.
     *
     * <p>
     * Asked of the fields rather than through {@link #anchor()}, because the sweep asks it of every
     * column in a square of a thousand and that accessor builds a little array to answer with.
     */
    public boolean withinRound(int x, int z) {
        int[] home = anchor();
        int radius = workRadius();
        return Math.abs(x - home[0]) <= radius && Math.abs(z - home[2]) <= radius;
    }

    /** Whether the mend it last tried was refused for want of the right block. Server side. */
    boolean shortOfBlocks() {
        return shortOfBlocks;
    }

    /**
     * Turns the round round, which is what a finished round means to a farming golem.
     *
     * <p>
     * Called when a sweep of the whole ground finds nothing left wanting doing. That is the only
     * honest definition of "finished" available: a clock would turn the round part-way through and
     * leave half the ground at one edge and half at the other, which is a golem walking in circles
     * rather than a field breathing.
     *
     * <p>
     * The alternative considered and not taken was a phase off the level clock, which two golems
     * with overlapping rounds would agree on by construction. This does not: two farming golems
     * sharing ground can settle into opposite phases and undo each other. If that ever turns up in
     * practice the level clock is the fix, and it costs no state at all.
     */
    public void turnFarm() {
        farmWearing = !farmWearing;
    }

    /**
     * Chooses again which upgrades an unstable one has hold of.
     *
     * <p>
     * Between three and all nine, and the storage is always one of them. That is not a softening of
     * the idea, it is what makes the idea survivable: the storage decides how many slots count as
     * the golem's own, and one that flickered would strand whatever was in the far slots every
     * minute and hand it back through the overflow, for as long as the golem stood there.
     *
     * <p>
     * The extra health goes on and comes off with the rest, which is visible and is meant to be: an
     * unstable golem's bar jumps, and one caught halfway through a fight without it is exactly the
     * risk the nether star buys off.
     */
    private void rollUnstable() {
        GolemUpgrade[] pool = GolemUpgrade.unstablePool();
        int[] others = new int[pool.length];
        int count = 0;
        for (GolemUpgrade upgrade : pool) {
            if (upgrade != GolemUpgrade.DEEP) others[count++] = upgrade.ordinal();
        }
        for (int i = count - 1; i > 0; i--) {
            int swap = getRandom().nextInt(i + 1);
            int keep = others[i];
            others[i] = others[swap];
            others[swap] = keep;
        }

        int wanted = 3 + getRandom().nextInt(Math.max(1, pool.length - 2));
        int mask = 1 << GolemUpgrade.DEEP.ordinal();
        for (int i = 0; i < Math.min(wanted - 1, count); i++) {
            mask |= 1 << others[i];
        }

        entityData.set(WATCH_UNSTABLE, Integer.valueOf(mask));
        unstableRolledAt = tickCount;
        // The extra health is an attribute modifier rather than a number, so it has to be put on
        // and taken off as the roll changes rather than merely reported differently.
        fitStoutness(standsLonger());
        entityData.set(WATCH_RADIUS, Integer.valueOf(workRadius()));
        takeStock();
    }

    /**
     * A name that says what it is by failing to say it.
     *
     * <p>
     * The more of the set an unstable golem has lost hold of, the more of its own name it cannot
     * keep still - so a golem carrying eight of the nine reads almost cleanly and one carrying
     * three is mostly noise. Worked out from the mask rather than from a clock, so two people
     * looking at the same golem see the same word scrambled, and the thing that changes it is the
     * thing it is describing.
     */
    private static String corrupted(String name, int mask) {
        if (name == null || name.isEmpty()) return name;
        int whole = GolemUpgrade.unstablePool().length;
        int lost = Math.max(0, whole - Integer.bitCount(mask));
        if (lost <= 0) return name;

        int threshold = (255 * lost) / Math.max(1, whole);
        StringBuilder out = new StringBuilder(name.length() + 16);
        boolean glitching = false;
        for (int i = 0; i < name.length(); i++) {
            int hash = mask * 0x9E3779B9 + i * 0x85EBCA6B;
            hash ^= hash >>> 15;
            boolean bad = name.charAt(i) != ' ' && (hash & 0xFF) < threshold;
            if (bad != glitching) {
                out.append(bad ? ChatFormatting.OBFUSCATED : ChatFormatting.RESET);
                glitching = bad;
            }
            out.append(name.charAt(i));
        }
        if (glitching) out.append(ChatFormatting.RESET);
        return out.toString();
    }

    // ------------------------------------------------------------------
    // The upgrade
    // ------------------------------------------------------------------

    public GolemUpgrade fittedUpgrade() {
        return GolemUpgrade.byOrdinal(intWatched(WATCH_UPGRADE));
    }

    public ItemStack upgradeStack() {
        return upgrade;
    }

    /**
     * Fits an upgrade, or takes one out.
     *
     * <p>
     * This runs on <em>both</em> sides and the split below is not optional. A client reaches it
     * every time a golem window opens: the upgrade is a container slot like any other, and the
     * packet that fills a freshly opened window writes every slot it carries straight through to
     * the inventory behind it. What may be done on that side is the watched value, which the
     * screen reads back immediately; everything under the return is bookkeeping the client holds
     * no truth for.
     *
     * <p>
     * The radius was the one that bit. It is derived from a field the client is never told, so
     * republishing it there overwrote the number the server had sent with the field's initial
     * value - and the screen, and WAILA with it, went on reporting sixteen for the rest of the
     * session however far the golem had actually been told to work.
     */
    public void setUpgradeStack(ItemStack stack) {
        this.upgrade = stack;
        GolemUpgrade fitted = GolemUpgrade.of(stack);
        entityData.set(WATCH_UPGRADE, Integer.valueOf(fitted.ordinal()));
        // The storage upgrade changes how many slots count as its own, so what it is carrying has
        // to be looked at again rather than trusted from before the upgrade went in or came out.
        // Server-only itself, and left above the line because the screen wants the width right.
        takeStock();
        if (level != null && level.isClientSide()) return;

        // The range upgrade raises the ceiling its radius is clamped against, so a golem told to
        // keep forty blocks and held at sixteen jumps to forty the moment one goes in. Republished
        // here because nothing else will: the radius is only pushed when somebody presses the
        // button, and without this the screen would go on reporting the old number until they did.
        // Rolled here rather than left to the next tick, because everything below this line asks
        // what the golem can currently do - and the sweep in particular. An unstable golem whose
        // set is still empty reports sixteen slots rather than sixty-four, and the sweep would put
        // three quarters of what it was carrying on the floor before its first roll ever happened.
        if (fitted == GolemUpgrade.UNSTABLE && unstableMask() == 0) {
            unstableRolledAt = 0;
            rollUnstable();
        } else if (fitted != GolemUpgrade.UNSTABLE && unstableMask() != 0) {
            entityData.set(WATCH_UNSTABLE, Integer.valueOf(0));
        }

        entityData.set(WATCH_RADIUS, Integer.valueOf(workRadius()));
        tidyStorage();
        fitStoutness(standsLonger());
    }

    /**
     * Puts a point of health back on a stout golem, once an in-game day.
     *
     * <p>
     * The upgrade gave it twice the health and no way at all to get any of it back, which made the
     * second half of that bar a thing spent once. A day a point is deliberately slower than
     * anything else in the mod moves: a golem that walked away from a fight is meant to be worth
     * looking after for a while, not to shrug it off between strokes.
     *
     * <p>
     * Counted off the golem's own age rather than the level clock, so a golem in an unloaded chunk
     * mends nothing while it is not there. That is the same bargain its work already makes, and it
     * keeps a farm from healing its whole crew the moment somebody walks back into the chunk.
     */
    private void mendItself() {
        if (TrmtConfig.golemStoutHealTicks <= 0) return;
        if (tickCount <= 0 || tickCount % TrmtConfig.golemStoutHealTicks != 0) return;
        if (!standsLonger()) return;

        float health = getHealth();
        float most = getMaxHealth();
        // Nothing for the dead and nothing for the whole: setHealth on a full golem would still
        // publish a change, and setHealth on a dead one would quietly bring it back.
        if (health <= 0F || health >= most) return;
        setHealth(Math.min(most, health + 1F));
    }

    /**
     * Puts the extra health on or takes it off, and never twice.
     *
     * <p>
     * Reconciled against what is already there rather than applied blind, because the modifier is
     * saved with the golem and restored before this runs when it comes back from disk - and
     * applying one that is already applied is an exception rather than a no-op.
     *
     * <p>
     * How hurt the golem is carries across the change rather than how many points it has, which
     * matters only because an unstable one asks this every minute. Truncating to the new maximum
     * was right while this ran twice in a golem's life; run sixty times an hour it would make the
     * second half of a stout bar unreachable rather than merely slow to fill.
     */
    private void fitStoutness(boolean wanted) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance health = getAttribute(
            Attributes.MAX_HEALTH);
        if (health == null) return;
        net.minecraft.world.entity.ai.attributes.AttributeModifier fitted = health.getModifier(STOUT_ID);
        if (wanted == (fitted != null)) return;

        // How hurt it is, kept across the change rather than how much health it happens to have.
        // Truncating was right while this ran once, when the part went in or came out; an unstable
        // golem asks it every minute, and a golem clamped to sixty every time the roll dropped the
        // part would never keep a point of what it mended - the second half of that bar would be
        // unreachable rather than merely hard-won.
        float before = getHealth();
        float most = getMaxHealth();
        if (wanted) {
            health.addPermanentModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    STOUT_ID,
                    STOUT_NAME,
                    1.0D,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
        } else {
            health.removeModifier(fitted);
        }

        float now = getMaxHealth();
        if (before <= 0F || most <= 0F || now <= 0F) return;
        setHealth(Math.max(1F, Math.min(now, before * now / most)));
    }

    /**
     * Puts back whatever is sitting outside the storage the golem currently has.
     *
     * <p>
     * Anything past {@link #slotCount()} is invisible and unreachable: no screen draws it, no
     * click can land on it, and the golem's own work will not look at it - which includes the
     * search for the tamper it carries. A golem whose only tamper is out there holds nothing in
     * its hand, does no work at all, and runs from whatever comes near, with nothing on any screen
     * to say why. That is worth going out of the way to make impossible.
     *
     * <p>
     * Three destinations, in the order they were asked for: a slot the golem still has, then
     * whoever has its screen open, then the ground at its feet. Merging into a part-full stack is
     * tried before taking an empty one, so recovering three half stacks of gravel costs one slot
     * rather than three.
     *
     * <p>
     * Run every tick rather than only when an upgrade changes, and that is the whole of the fix.
     * The version that ran only on the change could not reach a golem that was <em>saved</em> with
     * something stranded: the upgrade is restored before the slots are read back, so the sweep ran
     * over an inventory that was still empty and the items arrived immediately afterwards. Every
     * golem carrying something from before this existed, or fed into a hidden slot by a hopper
     * while that was possible, is put right the first time it ticks.
     */
    public boolean tidyStorage() {
        if (level == null || level.isClientSide()) return false;
        int keep = slotCount();
        if (keep >= inventory.length) return false;

        Player offerTo = watching();
        boolean moved = false;
        for (int slot = keep; slot < inventory.length; slot++) {
            ItemStack stranded = inventory[slot];
            if (stranded == null) continue;
            inventory[slot] = null;
            moved = true;
            stranded = packInto(stranded, keep);
            // A tamper never goes on the floor while there is stock in there worth less than it.
            if (stranded != null && GolemCombat.isTamper(stranded)) {
                // What comes back is whatever the tamper displaced, or nothing when every kept slot
                // already holds a tamper and there was no room to make - and then the tamper itself
                // is still the thing looking for a home. Writing that nothing over it is how a golem
                // relieved of its storage upgrade deleted the tampers that no longer fitted, with no
                // drop and no word.
                ItemStack displaced = makeRoomForTool(stranded, keep);
                if (displaced != null) stranded = displaced;
            }
            if (stranded != null && offerTo != null && offerTo.inventory.add(stranded)) {
                stranded = null;
            }
            if (stranded != null && !stranded.isEmpty()) spawnAtLocation(stranded, 0.5F);
        }
        // What it is holding was worked out from slots that have just moved under it, and the
        // whole point of this is that a tamper out there left the golem empty-handed.
        if (moved) takeStock();
        return moved;
    }

    /**
     * Makes room in the kept storage for a tamper, by turning out something that is not one.
     *
     * <p>
     * The sweep above would otherwise drop a tamper on the ground when the sixteen slots it is
     * packing into are full of mending stock - and a golem that throws its own tool away, once,
     * quietly, is far worse than one holding a stack of gravel it cannot reach. Stock is
     * replaceable and a graded tamper is not, so the stock is what goes.
     *
     * <p>
     * The last such slot rather than the first, because the first is where the golem's own
     * gathering piles up and the last is where a player's leftovers sit. Returns what was turned
     * out, for the caller to hand on, or null if every slot already holds a tamper - in which case
     * the golem is armed regardless and the extra one can go.
     */
    private ItemStack makeRoomForTool(ItemStack tool, int keep) {
        for (int at = keep - 1; at >= 0; at--) {
            ItemStack there = inventory[at];
            if (there == null || GolemCombat.isTamper(there)) continue;
            inventory[at] = tool;
            return there;
        }
        return null;
    }

    /** Merges into what is already there first, then takes an empty slot; null when it all fits. */
    private ItemStack packInto(ItemStack stranded, int keep) {
        int cap = Math.min(stranded.getMaxStackSize(), getMaxStackSize());
        for (int into = 0; into < keep && !stranded.isEmpty(); into++) {
            ItemStack there = inventory[into];
            if (there == null || !there.sameItem(stranded) || !ItemStack.tagMatches(there, stranded)) {
                continue;
            }
            int room = cap - there.getCount();
            if (room <= 0) continue;
            int shifted = Math.min(room, stranded.getCount());
            there.grow(shifted);
            stranded.shrink(shifted);
        }
        if (stranded.isEmpty()) return null;
        for (int into = 0; into < keep; into++) {
            if (inventory[into] != null) continue;
            inventory[into] = stranded;
            return null;
        }
        return stranded;
    }

    /**
     * Whoever has this golem open, or null.
     *
     * <p>
     * Held rather than looked up, because "the nearest player" is a different question and gets a
     * different answer when there are two of them. Checked again here because somebody can stop
     * being a viewer without closing anything - by dying, by leaving, or by opening something
     * else.
     */
    private Player watching() {
        if (viewer == null) return null;
        if (!viewer.isAlive() || !(viewer.containerMenu instanceof ContainerGolem)
            || ((ContainerGolem) viewer.containerMenu).golem() != this) {
            viewer = null;
        }
        return viewer;
    }

    /** Told by the screen itself, which is the only thing that knows. */
    public void setViewer(Player player) {
        this.viewer = player;
    }

    /** Whether an upgrade may be fitted at all, which a pack can switch off. */
    public boolean upgradesAllowed() {
        return TrmtConfig.golemUpgrades;
    }

    // ------------------------------------------------------------------
    // Where it works
    // ------------------------------------------------------------------

    public int workRadius() {
        int cap = extendsRange() ? TrmtConfig.golemMaxRadius : TrmtConfig.golemBaseRadius;
        return Math.max(1, Math.min(radius, cap));
    }

    /**
     * What it was last told to keep, before whatever cap is in force clamps it.
     *
     * <p>
     * The screen steps the radius by adding to what it reads back, and what it reads back is
     * clamped - so a golem whose cap drops for a minute, which is what an unstable one does when
     * the range upgrade falls out of the roll, would have its stored number quietly rewritten down
     * to the smaller cap and never get it back.
     */
    public int storedRadius() {
        return radius;
    }

    public void setWorkRadius(int wanted) {
        this.radius = Math.max(1, Math.min(wanted, TrmtConfig.golemMaxRadius));
        entityData.set(WATCH_RADIUS, Integer.valueOf(workRadius()));
    }

    /** The point it works around, which starts where it was built. */
    public int[] anchor() {
        if (!anchored) {
            return new int[] { Mth.floor(getX()), Mth.floor(getY()), Mth.floor(getZ()) };
        }
        return new int[] { anchorX, anchorY, anchorZ };
    }

    public void setAnchor(int x, int y, int z) {
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        anchored = true;
        publishHome();
        // The list of containers it works out of is a list of places around the old home, so a
        // golem that has just been moved must not go on reaching into the ones it left behind.
        stores = null;
        storesFoundAt = 0;
        // And what those stores could not pay for says nothing about the ones around the new home.
        refusedFetches.forget();
    }

    /** Tells every client watching where its home is now. */
    private void publishHome() {
        if (level == null || level.isClientSide()) return;
        entityData.set(WATCH_HOME_X, Integer.valueOf(anchorX));
        entityData.set(WATCH_HOME_Y, Integer.valueOf(anchorY));
        entityData.set(WATCH_HOME_Z, Integer.valueOf(anchorZ));
    }

    /** Where its home is, as the client is told it: x, y and z. */
    public int[] watchedHome() {
        return new int[] { intWatched(WATCH_HOME_X), intWatched(WATCH_HOME_Y), intWatched(WATCH_HOME_Z) };
    }

    /** Ticks between strokes: slow by default, twice as often with the swift upgrade. */
    public int workPeriod() {
        return isFaster() ? 10 : 20;
    }

    /**
     * How far from its anchor it will stand, which is a little wider than the ground it keeps.
     *
     * <p>
     * A boundary has to have somewhere on the far side of it. A golem allowed to stand only where
     * it works could not step off a road to hit something standing beside it, and one told to keep
     * a single square would have no room at all to move. The same number bounds the search for a
     * fight, the chase, the idle wander and the walk home, so all four agree without any of them
     * having to be told about the others.
     *
     * <p>
     * A floor under it as well as a margin above it, because one number doing four jobs means the
     * tightest of the four sets all of them. A golem told to keep a single square would otherwise
     * be penned into nine blocks: it could not step off that square to meet something coming at
     * it, and standing perfectly still on one block is not what anybody pictures when they narrow
     * a golem's round.
     */
    public double guardReach() {
        return Math.max(TrmtConfig.golemRoamFloor, workRadius() + TrmtConfig.golemGuardRange);
    }

    // ------------------------------------------------------------------
    // Who
    // ------------------------------------------------------------------

    public String summonedBy() {
        return summonedBy;
    }

    /** The watched copies, which is what a client-side tooltip can actually see. */
    public String watchedSummoner() {
        return entityData.get(WATCH_SUMMONER);
    }

    public String watchedConfigurer() {
        return entityData.get(WATCH_CONFIGURER);
    }

    public int watchedRadius() {
        return intWatched(WATCH_RADIUS);
    }

    public void setSummonedBy(String name) {
        this.summonedBy = name == null ? "" : name;
        entityData.set(WATCH_SUMMONER, this.summonedBy);
    }

    public String configuredBy() {
        return configuredBy;
    }

    public void setConfiguredBy(String name) {
        this.configuredBy = name == null ? "" : name;
        entityData.set(WATCH_CONFIGURER, this.configuredBy);
    }

    // ------------------------------------------------------------------
    // What it carries
    // ------------------------------------------------------------------

    /** How many slots it actually has, which the storage upgrade widens. */
    public int slotCount() {
        return widensStorage() ? DEEP_SLOTS : BASE_SLOTS;
    }

    public ItemStack[] inventory() {
        return inventory;
    }

    // ------------------------------------------------------------------
    // What it is holding the ground at
    // ------------------------------------------------------------------

    /** The wear this family is held at, 0 to 100, or -1 when the golem leaves it alone. */
    public int targetFor(com.trmtgtnh.surface.SurfaceFamily family) {
        if (family == null) return -1;
        int ordinal = family.ordinal();
        return ordinal < 0 || ordinal >= targets.length ? -1 : targets[ordinal];
    }

    public void setTargetFor(com.trmtgtnh.surface.SurfaceFamily family, int percent) {
        if (family == null) return;
        int ordinal = family.ordinal();
        if (ordinal < 0 || ordinal >= targets.length) return;
        targets[ordinal] = (byte) (percent < 0 ? -1 : Math.min(100, percent));
        takeStock();
    }

    /** The key a block and its metadata are remembered under. */
    public static String blockKey(net.minecraft.world.level.block.Block block, int meta) {
        if (block == null) return null;
        Object name = net.minecraft.core.Registry.BLOCK.getKey(block);
        return name == null ? null : name + "#" + (meta & 0xF);
    }

    /**
     * The wear this exact block is held at, falling back to its family's.
     *
     * <p>
     * The override is consulted first and answers even when it says "leave alone", which is the
     * point of it: a block can be excluded from a family that is otherwise being worked.
     */
    public int targetForBlock(net.minecraft.world.level.block.Block block, int meta, com.trmtgtnh.surface.SurfaceFamily family) {
        if (!blockTargets.isEmpty()) {
            Byte own = blockTargets.get(blockKey(block, meta));
            if (own != null) return own.byteValue();
        }
        return targetFor(family);
    }

    /** The override for a key, or {@link #NO_BLOCK_TARGET} when it simply follows its family. */
    public int blockTargetFor(String key) {
        if (key == null) return NO_BLOCK_TARGET;
        Byte own = blockTargets.get(key);
        return own == null ? NO_BLOCK_TARGET : own.byteValue();
    }

    /** What {@link #blockTargetFor} answers when there is no override at all. */
    public static final int NO_BLOCK_TARGET = 127;

    /**
     * Sets or clears one block's override.
     *
     * @param percent 0-100 to hold it there, -1 to leave that block alone, or
     *                {@link #NO_BLOCK_TARGET} to drop the override and follow the family again
     */
    public void setBlockTarget(String key, int percent) {
        if (key == null) return;
        if (percent == NO_BLOCK_TARGET) {
            blockTargets.remove(key);
            takeStock();
            return;
        }
        if (!blockTargets.containsKey(key) && blockTargets.size() >= MAX_BLOCK_TARGETS) return;
        blockTargets.put(key, Byte.valueOf((byte) (percent < 0 ? -1 : Math.min(100, percent))));
        takeStock();
    }

    public int blockTargetCount() {
        return blockTargets.size();
    }

    /** Whether it has been told to do anything at all yet. */
    public boolean hasOrders() {
        for (Byte override : blockTargets.values()) {
            if (override != null && override.byteValue() >= 0) return true;
        }
        for (byte target : targets) {
            if (target >= 0) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Working
    // ------------------------------------------------------------------

    /**
     * Its whole tick: what is in its hand, then the AI, then a stroke of work.
     *
     * <p>
     * The order matters more than it looks. The hand and the state byte are settled before the AI
     * runs, so every task that asks a question this tick gets the same answer and the blow one of
     * them may swing is swung by a golem already holding its tool. The work comes after, because a
     * stroke is the thing that puts the tool up in the first place and there is no sense deciding
     * that before it has happened.
     */
    @Override
    public void aiStep() {
        // Both sides. A client has no other tick to ease the arm on and no history to interpolate
        // one from, and the three floating-point operations the server pays for the symmetry are
        // cheaper than a second code path that only one side runs.
        easePose();
        if (blowTicks > 0) blowTicks--;

        boolean live = level != null && !level.isClientSide() && isAlive();
        if (live) {
            if (busyTicks > 0) busyTicks--;
            if (masonrySulk > 0) masonrySulk--;
            if (chewTicks > 0) chewTicks--;
            // Asked every tick rather than only on the tick the counter runs out, so a golem
            // that unloaded with something in its mouth finishes it on the first tick back
            // instead of holding it for the life of the level.
            if (chewTicks <= 0 && masonry != null) finishMouthful();
            if (fightCooldown > 0) fightCooldown--;
            if (giveUpTicks > 0) giveUpTicks--;
            // A golem that was never given an anchor writes one where it stands, once. Left
            // unanchored the point it works around is wherever its feet are, so its home moves
            // with it and every boundary drawn from that home is satisfied everywhere - which was
            // harmless while it only wandered and is not once it can be led away by a monster.
            if (!anchored) setAnchor(Mth.floor(getX()), Mth.floor(getY()), Mth.floor(getZ()));
            // A minute at a time, and one straight away for a golem that has just woken up: the
            // mask is not saved, so a freshly loaded unstable one would otherwise stand there with
            // none of the set until its first minute was up.
            // Not gated on the config that decides whether one can be made. That setting is about
            // the crafting bench, and a golem already carrying the part has to go on working when
            // somebody turns it off - otherwise flipping a recipe switch strips nine upgrades off
            // every unstable golem in the level and empties three quarters of each one onto the
            // floor.
            if (fittedUpgrade() == GolemUpgrade.UNSTABLE && (unstableRolledAt <= 0
                || tickCount - unstableRolledAt >= Math.max(20, TrmtConfig.golemUnstablePeriod))) {
                rollUnstable();
            }
            // Every tick rather than every stroke, because four separate pieces of vanilla read
            // this one boundary and a golem that refreshed it twice a second would spend the gap
            // answering with whatever a lead or a load last left behind. Skipped while it is on a
            // lead, because a lead sets the same boundary to the person holding it and the two
            // fighting over it every tick would tear the golem in half.
            if (!isLeashed()) holdGround();
            if (getTarget() != null) markBusy(GolemCombat.BUSY_FIGHT, GolemCombat.FIGHT_HOLD);
            // Cheap when there is nothing to do: it returns on the first comparison for a golem
            // with the storage upgrade fitted, and on forty-eight null checks for every other one.
            tidyStorage();
            mendItself();
            publish();
        }

        super.aiStep();

        if (level == null || level.isClientSide() || !isAlive()) return;

        // The work is switched off with the golem; what it is holding up is not, because a golem
        // switched off mid-stroke has to be seen to put its tool away rather than freeze holding it.
        if (TrmtConfig.golemEnabled && tickCount % workPeriod() == 0) {
            // A look, not a change. Counted as a change it would tell the memory of what the stores
            // could not pay for that the golem's stock had moved on every stroke, and a refusal for
            // want of room would be forgotten as fast as it was learned.
            reckonStock();
            // Emptied before it is worked, not after, because the state this answers is "it has
            // nowhere to put the next thing it picks up" - and the next thing it picks up happens
            // three lines below.
            if (isFull() && GolemStores.reaches(this)) GolemStores.unload(this);
            GolemWork.workOnce(this);
            if (TrmtConfig.golemPickup) GolemWork.gather(this);
        }

        mindTheTarget();
        publish();
    }

    public int workCursor() {
        return workCursor;
    }

    public void advanceCursor(int by) {
        workCursor += by;
        if (workCursor < 0) workCursor = 0;
    }

    /**
     * The tamper it works and fights with, or -1 when it has none and so can do nothing.
     *
     * <p>
     * The best one it is carrying rather than the first one it comes across, so that the tool in
     * its hand is the tool it fights with is the tool wearing down - three things that would
     * otherwise be allowed to disagree with each other where somebody can see them.
     * {@link GolemCombat#bestToolSlot} is where the ordering is argued. Answered from what the
     * last change to its storage worked out, because this is also the answer to "is it armed" and
     * that is asked far too often to go looking for it each time.
     */
    public int findTool() {
        return toolSlot;
    }

    /**
     * Wears the tool it is working with, sparing it every other stroke when fitted to.
     *
     * <p>
     * A tool that runs out is dropped from the slot rather than left as a broken stub, so the
     * next one in the row is picked up on the following stroke without anybody clearing it out.
     *
     * <p>
     * Two things it refuses to touch. Anything that is not a tamper, because setting damage on a
     * stack is setting its metadata and a slot holding gravel would quietly become a slot holding
     * something else. And anything whose maximum is zero, which is how an item says it has no
     * durability at all - the four fixed tiers answer that when a pack turns the durability scale
     * off, and damaging one by a point would put it past a maximum of zero and destroy it on the
     * first square it touched.
     */
    public void wearTool(int slot) {
        if (slot < 0 || slot >= inventory.length) return;
        ItemStack tool = inventory[slot];
        if (tool == null || !GolemCombat.isTamper(tool)) return;
        strokes++;
        if (sparesTools() && (strokes % 2) == 0) return;
        if (tool.getItem() instanceof com.trmtgtnh.item.ItemChunkTamper
            && !((com.trmtgtnh.item.ItemChunkTamper) tool.getItem()).wearsOut(tool)) {
            return;
        }
        if (tool.getMaxDamage() <= 0) return;
        tool.setDamageValue(tool.getDamageValue() + 1);
        if (tool.getDamageValue() >= tool.getMaxDamage()) {
            inventory[slot] = null;
            // Looked at again at once rather than at the next stroke, because until it is, the
            // golem is armed on paper and holding up a picture of a tool it no longer has.
            takeStock();
        }
    }

    /**
     * How many blocks one purchase of mending costs this golem: golem.blockCost, halved by the
     * frugality upgrade rounding down, and never below one.
     *
     * <p>
     * A purchase rather than a gradation. How much mending one purchase buys is decided by the
     * stroke's ledger, in {@code GolemWork.strokeLedger}, so this figure means the same whichever
     * way the golem is priced. Held at one at the bottom as well as halved, so neither a setting nor
     * an upgrade can make a golem's mending free: a keeper that cost nothing to run would never need
     * looking after.
     */
    public int mendPrice() {
        int cost = Math.max(1, TrmtConfig.golemBlockCost);
        return sparesBlocks() ? Math.max(1, cost / 2) : cost;
    }

    /**
     * How many things it carries that would pay under this rule, in the storage it currently has,
     * counting no further once {@code enough} is reached.
     *
     * <p>
     * What counts as payment is the player's rule, asked of the same class that answers it for bone
     * meal and the chunk tamper. The golem used to have its own test that demanded the very block
     * being mended, down to the metadata - so it could not mend a lawn without a stack of turf,
     * which is the one thing the level will not give you, and repairBlocks went unread. Stopping at
     * enough matters because a stroke asks this for every purchase it considers, and a deep golem
     * carrying sixty-four full stacks would otherwise add up all of them to learn it has two dirt.
     */
    int countPaying(com.trmtgtnh.server.HealingCost pays, int enough) {
        if (pays == null) return 0;
        int found = 0;
        int slots = Math.min(slotCount(), inventory.length);
        for (int slot = 0; slot < slots && found < enough; slot++) {
            ItemStack held = inventory[slot];
            if (pays.paidBy(held)) found += held.getCount();
        }
        return found;
    }

    /**
     * Takes this many things that would pay under this rule, or nothing at all.
     *
     * <p>
     * All or nothing, for the reason {@code MendPurse.Quote.take} gives: a take that stopped halfway
     * would have spent stock on a gradation it then refused. The ground's own kind goes first,
     * wherever it sits, so the exact turf fills the rut before a cousin that merely counts - the
     * same order a player's own search uses. An emptied slot is nulled rather than left holding a
     * stack of nought, and what it carries is looked at again once, at the end, because spending
     * the last of something changes whether it has anything to mend with, and the tooltip that says
     * so reads that rather than the slots.
     *
     * @return the distinct kinds taken, as {@code MendPurse.kindOf} makes them; an empty list for a
     *         count of nought; null when fewer than that many are carried, and then nothing is taken
     */
    java.util.List<Object> takePayment(com.trmtgtnh.server.HealingCost pays, int count) {
        if (count <= 0) return new java.util.ArrayList<Object>(0);
        if (pays == null || countPaying(pays, count) < count) return null;
        java.util.List<Object> kinds = new java.util.ArrayList<Object>(2);
        int remaining = takeFrom(pays, count, true, kinds);
        if (remaining > 0) takeFrom(pays, remaining, false, kinds);
        takeStock();
        return kinds;
    }

    /**
     * One pass of {@link #takePayment}: the ground's own kind only, or anything that pays. Returns what is still owed.
     */
    private int takeFrom(com.trmtgtnh.server.HealingCost pays, int remaining, boolean ownOnly,
        java.util.List<Object> kinds) {
        int slots = Math.min(slotCount(), inventory.length);
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack held = inventory[slot];
            if (ownOnly ? !pays.paidByOwn(held) : !pays.paidBy(held)) continue;
            Object kind = com.trmtgtnh.server.MendPurse.kindOf(held);
            // Never a null among them. The ledger refuses a purchase that names one by throwing, and
            // an exception inside a ticking entity is a crash every time its chunk loads.
            if (kind != null && !kinds.contains(kind)) kinds.add(kind);
            int taken = Math.min(remaining, held.getCount());
            held.shrink(taken);
            remaining -= taken;
            // Null, the storage's one word for a free slot: every reader carried from 1.7.10 asks for null, and an
            // EMPTY left here read as occupied - stock went to the floor beside free slots (0.9.222, spec GO32).
            if (held.isEmpty()) inventory[slot] = null;
        }
        return remaining;
    }

    /** How many times what it carries has changed since it was made or loaded; see the field. Server side. */
    int stockChanges() {
        return stockChanges;
    }

    /** Whether every slot it owns is spoken for, which is when it is worth emptying. */
    public boolean isFull() {
        for (int slot = 0; slot < slotCount() && slot < inventory.length; slot++) {
            if (inventory[slot] == null) return false;
        }
        return true;
    }

    /**
     * Looks at what it is carrying again.
     *
     * <p>
     * The public face of {@link #takeStock()}, for the one place outside this class that writes a
     * slot without going through a method here: a settled golem emptying its surplus into the
     * stores around it, which sets slots through {@link #inventory()} directly. The fetch that
     * brings something back needs no such call - it arrives through {@link #store}, which already
     * takes stock of itself.
     */
    public void restock() {
        takeStock();
    }

    /**
     * How many of a kind it could actually take in, which is what a fetch must not exceed.
     *
     * <p>
     * The largest single slot rather than the sum of them, because {@link #store} merges a stack
     * into one slot or refuses it whole - so a golem with twelve slots each one short of full has
     * room for one, not twelve. A fetch that asked for more would take a stack out of somebody's
     * chest, fail to store any of it, and hand the whole lot back, every stroke, for ever.
     */
    public int roomFor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0;
        int most = 0;
        for (int slot = 0; slot < slotCount() && slot < inventory.length; slot++) {
            ItemStack held = inventory[slot];
            if (held == null) {
                most = Math.max(most, stack.getMaxStackSize());
                continue;
            }
            if (!held.sameItem(stack) || !ItemStack.tagMatches(held, stack)) continue;
            most = Math.max(most, held.getMaxStackSize() - held.getCount());
        }
        return Math.max(0, most);
    }

    /** Puts something it found into storage, or false when there is no room and it must be left. */
    public boolean store(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (int slot = 0; slot < slotCount(); slot++) {
            ItemStack held = inventory[slot];
            if (held == null) {
                inventory[slot] = stack.copy();
                takeStock();
                return true;
            }
            if (held.sameItem(stack) && ItemStack.tagMatches(held, stack)
                && held.getCount() + stack.getCount() <= held.getMaxStackSize()) {
                held.grow(stack.getCount());
                takeStock();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // What it is doing, and what it is showing
    // ------------------------------------------------------------------

    /**
     * Says what it is doing and for how long the tool should stay up.
     *
     * <p>
     * A countdown rather than a flag somebody has to remember to switch off. Every stroke sets it
     * a little longer than the gap between strokes, so a golem in the middle of a job never puts
     * its tool away between two of them, and one that has stopped - because the ground is already
     * right, because the chunk went away, because it was switched off - puts it away a moment
     * later without anything having had to notice that it stopped.
     */
    public void markBusy(int kind, int ticks) {
        busyKind = kind;
        if (ticks > busyTicks) busyTicks = ticks;
    }

    /** Whether it may swing yet, which is once a second and not once a tick. */
    public boolean readyToStrike() {
        return fightCooldown <= 0;
    }

    /**
     * Books one swing: the second before the next, the tool up, and the arm coming down.
     *
     * <p>
     * The animation goes out as an entity status rather than the usual swing, for the reason given
     * where that status is named. Called for the swing rather than for the blow, because a golem
     * that swings and connects with nothing has still visibly swung.
     */
    public void swingTool() {
        fightCooldown = GolemCombat.FIGHT_PERIOD;
        markBusy(GolemCombat.BUSY_FIGHT, GolemCombat.FIGHT_HOLD);
        blowTicks = GolemCombat.BLOW_TICKS;
        if (level != null && !level.isClientSide()) level.broadcastEntityEvent(this, STATUS_SWING);
    }

    /** Says a blow actually landed, which is what tells it this fight is going somewhere. */
    public void noteBlowLanded() {
        stalemate = 0;
    }

    /**
     * The ordinary swing, refused.
     *
     * <p>
     * The chase task asks for one every tick it is in reach, and for this entity every one of them
     * would be a packet to every watching client for an animation that cannot play: vanilla only
     * advances the swing counter for players and for {@code EntityMob}, and a golem is neither.
     * The blow has an animation of its own, booked once per swing where the swing is booked. The
     * client half still lets the base class through, because on that side this is called by the
     * server's own animation packet rather than by anything of ours.
     */
    @Override
    public void swing(net.minecraft.world.InteractionHand hand) {
        if (level != null && level.isClientSide()) super.swing(hand);
    }

    /** Whether it is carrying a tamper - from the slots on the server, from the byte on a client. */
    public boolean isArmed() {
        if (level != null && !level.isClientSide()) return toolSlot >= 0;
        return (byteWatched(WATCH_STATE) & GolemCombat.ARMED_BIT) != 0;
    }

    /** Whether it is carrying ground it could mend with, as the client can see it. */
    public boolean hasMendingStock() {
        return (byteWatched(WATCH_STATE) & GolemCombat.STOCK_BIT) != 0;
    }

    /** Whether it has been told to hold anything, as the client can see it. */
    public boolean watchedOrders() {
        return (byteWatched(WATCH_STATE) & GolemCombat.ORDERS_BIT) != 0;
    }

    /**
     * Whether the last mend it wanted was refused for want of the right block.
     *
     * <p>
     * Cleared the moment one succeeds or anything moves in its store, so it reports the situation
     * rather than the history: putting the right block in makes it go away without waiting for the
     * golem to come round to that square again.
     */
    public boolean watchedShortOfBlocks() {
        return (byteWatched(WATCH_STATE) & GolemCombat.SHORT_BIT) != 0;
    }

    /** Told by the work, which is the only thing that finds out. */
    public void noteShortOfBlocks(boolean short_) {
        this.shortOfBlocks = short_;
    }

    /** The list of squares it knows want doing. Server side; never null. */
    GolemTargets workList() {
        return workList;
    }

    /** Where it is heading for want of anything to do here, or null. */
    public int[] workTarget() {
        return walkingToWork ? new int[] { workX, workY, workZ } : null;
    }

    public boolean hasWorkTarget() {
        return walkingToWork;
    }

    public void setWorkTarget(int x, int y, int z) {
        workX = x;
        workY = y;
        workZ = z;
        walkingToWork = true;
    }

    public void clearWorkTarget() {
        walkingToWork = false;
    }

    /** Whether it is walking away from something, as the client can see it. */
    public boolean isFleeing() {
        return (byteWatched(WATCH_STATE) & GolemCombat.FLEE_BIT) != 0;
    }

    /** Told by the retreat itself, because nothing else knows when one starts or stops. */
    public void setFleeing(boolean running) {
        fleeing = running;
    }

    /**
     * Mouthfuls it holds, counting the one in its mouth.
     *
     * <p>
     * Both, because this answers "how full is it", and a mouthful being chewed is as much in the way
     * as one already swallowed.
     */
    public int masonryHeld() {
        return masonryOwed.size() + (masonry == null ? 0 : 1);
    }

    /** Mouthfuls ready to lay, not counting the one still being chewed. */
    public int masonryReady() {
        return masonryOwed.size();
    }

    /** Ticks left before it will look for somewhere to reinforce again. */
    public int masonrySulk() {
        return masonrySulk;
    }

    /** Sets that wait, after a look that found nowhere to put a mouthful. */
    public void setMasonrySulk(int ticks) {
        masonrySulk = Math.max(0, ticks);
    }

    /**
     * What is in its mouth this moment, or null when its mouth is empty.
     *
     * <p>
     * Asked rather than {@code isChewing}, which reads a watched byte meant for the model: that byte
     * follows the chew timer, and the timer can be running for a mouthful that has already been
     * counted. This is the item itself, and the item is what decides whether another will fit.
     */
    public ItemStack masonryInMouth() {
        return masonry;
    }

    /**
     * Puts one piece of material in its mouth.
     *
     * <p>
     * One, and only when its mouth is empty, so a stack thrown down is eaten one at a time with the
     * chewing between them rather than vanishing in a single tick. The caller starts the chew; this
     * only takes the item.
     *
     * @return false when it is already chewing
     */
    public boolean swallowMasonry(ItemStack one) {
        if (one == null || one.isEmpty()) return false;
        if (masonry != null) return false;
        masonry = one.copy();
        masonry.setCount(1);
        return true;
    }

    /**
     * Ends the mouthful: the container goes back, and what was in it is counted.
     *
     * <p>
     * The container is returned here rather than when the material is finally laid, and that is the
     * whole shape of this. A player who throws a bucket down watches the golem eat it and has the
     * bucket handed straight back, instead of finding it minutes later beside a square they can see
     * no change in - and a bucket that comes back the moment it is emptied cannot be mistaken for
     * the golem refusing the job, which is exactly how the old arrangement read.
     *
     * <p>
     * The whole stack rather than one of it, which matters only once and matters a great deal then.
     * A golem saved by a build before this one kept its entire gullet in this single field, up to
     * sixteen of them, and counting that as one mouthful would have destroyed the other fifteen on
     * the first tick after the level loaded. This build never puts more than one here, so the loop
     * runs once and costs nothing; it exists for the worlds that already have golems in them.
     *
     * <p>
     * Safe to call when nothing is being chewed, and called that way on purpose: a golem that
     * unloaded mid-mouthful comes back with the item still in its mouth and no chew left to run, and
     * the tick that notices finishes it rather than leaving it there for ever.
     */
    public void finishMouthful() {
        if (masonry == null) return;
        ItemStack one = masonry;
        masonry = null;
        int units = Math.max(1, one.getCount());
        ItemStack empty = com.trmtgtnh.server.ReinforceCost.leftoverOf(one);
        for (int i = 0; i < units; i++) {
            if (empty == null) {
                // Nothing came back, so the material itself is still owed. Remembered rather than
                // dropped, because it has been eaten - it is owed only if the golem dies holding it.
                ItemStack owed = one.copy();
                owed.setCount(1);
                masonryOwed.add(owed);
            } else {
                // A copy each: entityDropItem keeps the stack it is handed rather than copying it,
                // so handing the same one to several item entities would be a duplication bug.
                spawnAtLocation(empty.copy(), 0.5F);
                masonryOwed.add(null);
            }
        }
    }

    /**
     * Takes one mouthful to spend on a square.
     *
     * <p>
     * Nothing comes back and nothing is dropped: a container went back when the mouthful was
     * finished, so what is spent here is the material and only the material. The oldest entry goes
     * first, so a mouthful that has actually been laid can never be handed back afterwards.
     *
     * @return false when it has none ready
     */
    public boolean spendMasonry() {
        if (masonryOwed.isEmpty()) return false;
        masonryOwed.remove(0);
        return true;
    }

    /** Whether it is mid-mouthful. Read by the model, which is the only thing that cares. */
    public boolean isChewing() {
        return (byteWatched(WATCH_STATE) & GolemCombat.CHEW_BIT) != 0;
    }

    /** How much of a mouthful the pose should show this frame. */
    public float chewShow(float partial) {
        return prevChewLean + (chewLean - prevChewLean) * partial;
    }

    /** Ticks left of the mouthful it is working on, set when one goes in. */
    public void startChewing(int ticks) {
        if (ticks > chewTicks) chewTicks = ticks;
    }

    public int busyKind() {
        return byteWatched(WATCH_STATE) & GolemCombat.BUSY_MASK;
    }

    /** How far the free arm has come up this frame, for the model to pose the tool with. */
    public float heldRaise(float partial) {
        return prevHeldRaise + (heldRaise - prevHeldRaise) * partial;
    }

    /**
     * How far through a blow the arm is, from nothing at the start to one at the end.
     *
     * <p>
     * Interpolated by taking the fraction off the countdown rather than by keeping a second copy
     * of it from last tick, which is the iron golem's own trick and is exact for a counter that
     * only ever goes down by one.
     */
    public float blowProgress(float partial) {
        if (blowTicks <= 0) return 0F;
        float left = blowTicks - partial;
        if (left < 0F) left = 0F;
        float through = 1F - left / GolemCombat.BLOW_TICKS;
        return through < 0F ? 0F : (through > 1F ? 1F : through);
    }

    /** How far into its running posture it is this frame. */
    public float fleeLean(float partial) {
        return prevFleeLean + (fleeLean - prevFleeLean) * partial;
    }

    /** Ticks in one tamping stroke, halved when it is fitted to work twice as often. */
    public static final int STROKE_TICKS = 40;

    /** How far through its stroke it is this frame, wrapped into nothing-to-one. */
    public float strokePhase(float partial) {
        float at = prevStrokePhase + (strokePhase - prevStrokePhase) * partial;
        if (at < 0F) at += 1F;
        return at - (int) at;
    }

    /** How much of a fight the pose should show this frame. */
    public float fightLean(float partial) {
        return prevFightLean + (fightLean - prevFightLean) * partial;
    }

    /** How much of a tamper the pose should show this frame. */
    public float armedShow(float partial) {
        return prevArmedShow + (armedShow - prevArmedShow) * partial;
    }

    /** Which of the three standing poses is being shown, whatever it is currently worth. */
    public int idlePose() {
        return idleKind;
    }

    /**
     * How much of that pose to show this frame, with every reason not to already taken out.
     *
     * <p>
     * Damped here rather than where it is drawn, and that is the point of it existing at all. Two
     * things read this - the model, for a head and an arm, and the renderer, for the whole body -
     * and only one of them is handed the walk cycle. A gate applied in the model would leave the
     * renderer rolling a golem that had started walking, started fighting or started running away.
     * One number, one set of gates, and the two cannot disagree about when a pose is over.
     *
     * <p>
     * The movement term is the model's own: the same fifth of a step over which its legs come up.
     * Being hurt slams the swing to full on both sides, so a golem that is struck drops its pose in
     * a tick and takes it back over the next eight, which is the right answer for free.
     */
    public float idleShow(float partial) {
        if (!TrmtConfig.golemIdlePoses) return 0F;
        float show = prevIdleShow + (idleShow - prevIdleShow) * partial;
        if (show <= 0F) return 0F;
        float moving = Math.min(1F, (animationSpeedOld + (animationSpeed - animationSpeedOld) * partial) * 5F);
        return show * (1F - moving) * (1F - heldRaise(partial)) * (1F - fightLean(partial)) * (1F - fleeLean(partial));
    }

    /** Brings the arm up while it is busy, lets it down when it is not, and hunches it to run. */
    private void easePose() {
        prevHeldRaise = heldRaise;
        float wanted = busyKind() == GolemCombat.BUSY_NONE ? 0F : 1F;
        heldRaise += (wanted - heldRaise) * 0.25F;
        if (heldRaise < 0.001F) heldRaise = 0F;
        if (heldRaise > 0.999F) heldRaise = 1F;

        prevFleeLean = fleeLean;
        // Slower than the arm on purpose. An arm comes up to do a job and can snap to it; a
        // posture is the whole body and has to take a moment either way or the golem reads as
        // teleporting between two poses.
        fleeLean += ((isFleeing() ? 1F : 0F) - fleeLean) * 0.12F;
        if (fleeLean < 0.001F) fleeLean = 0F;
        if (fleeLean > 0.999F) fleeLean = 1F;

        prevFightLean = fightLean;
        fightLean += ((busyKind() == GolemCombat.BUSY_FIGHT ? 1F : 0F) - fightLean) * 0.25F;
        if (fightLean < 0.001F) fightLean = 0F;
        if (fightLean > 0.999F) fightLean = 1F;

        // Eased like the rest, and faster than most, because a mouthful is short enough that a
        // slow blend would still be arriving when it was over.
        prevChewLean = chewLean;
        chewLean += ((isChewing() ? 1F : 0F) - chewLean) * 0.3F;
        if (chewLean < 0.001F) chewLean = 0F;
        if (chewLean > 0.999F) chewLean = 1F;

        prevArmedShow = armedShow;
        armedShow += ((isArmed() ? 1F : 0F) - armedShow) * 0.12F;
        if (armedShow < 0.001F) armedShow = 0F;
        if (armedShow > 0.999F) armedShow = 1F;

        // Two rates rather than one, and the asymmetry is doing real work. Rising slowly is what
        // makes a pose dawn rather than appear - about three seconds, so a golem between two jobs
        // never strikes an attitude about it. Falling at the same rate would take a hundred and
        // thirty-five ticks to reach the floor that lets the next pose start, which would make a
        // change of pose cost the better part of ten seconds. Coming down is the rate everything
        // else here uses for a thing that has simply stopped being true.
        prevIdleShow = idleShow;
        int nowWanted = GolemCombat.idlePose(this);
        if (nowWanted != idleWanted) {
            idleWanted = nowWanted;
            idleSteady = 0;
        } else if (idleSteady < GolemCombat.IDLE_DWELL) {
            idleSteady++;
        }
        float wantIdle = idleWanted == idleKind && idleKind != GolemCombat.IDLE_NONE ? 1F : 0F;
        idleShow += (wantIdle - idleShow) * (wantIdle > idleShow ? 0.05F : 0.25F);
        if (idleShow < 0.001F) idleShow = 0F;
        if (idleShow > 0.999F) idleShow = 1F;
        // Only ever changed while nothing is being shown, so no pose is ever half another one. The
        // dwell is waived on the way in from nothing, because there is no pose to protect yet.
        if (idleShow == 0F && idleKind != idleWanted
            && (idleKind == GolemCombat.IDLE_NONE || idleSteady >= GolemCombat.IDLE_DWELL)) {
            idleKind = idleWanted;
        }

        // Both marks move together across the wrap, so the fraction between them stays a step
        // forward rather than becoming a whole stroke backwards for one frame.
        prevStrokePhase = strokePhase;
        strokePhase += 1F / (isFaster() ? STROKE_TICKS * 0.5F : STROKE_TICKS);
        if (strokePhase >= 1F) {
            strokePhase -= 1F;
            prevStrokePhase -= 1F;
        }
    }

    /**
     * Something it carries or something it was told has just changed: counts the change, then looks.
     *
     * <p>
     * Called from every path that can change either, which is what lets everything else read a
     * field instead of walking sixty-four slots. Cheap enough to call on a click and far too
     * expensive to call on a tick, which is exactly the shape of the thing. The count is what tells
     * a refusal for want of room, remembered by the stores, that the golem may since have made some.
     */
    private void takeStock() {
        if (level != null && level.isClientSide()) return;
        stockChanges++;
        reckonStock();
    }

    /**
     * Works out what it is carrying and what it has been told, in one pass, without calling it a
     * change.
     *
     * <p>
     * Kept apart from {@link #takeStock()} for the one caller that looks when nothing need have
     * changed: the start of every stroke. Counted there, a change would be recorded twice a second
     * whatever happened, and every refusal for want of room would be forgotten the stroke after it
     * was learned.
     */
    private void reckonStock() {
        if (level != null && level.isClientSide()) return;
        // Whatever it could not pay for a moment ago, it may be able to pay for now.
        shortOfBlocks = false;
        int slot = GolemCombat.bestToolSlot(this);
        if (slot != toolSlot || !GolemCombat.stillShows(toolShown, slot < 0 ? null : inventory[slot])) {
            toolSlot = slot;
            toolShown = slot < 0 ? null : GolemCombat.displayCopy(inventory[slot]);
        }
        mendingStock = false;
        int slots = Math.min(slotCount(), inventory.length);
        for (int look = 0; look < slots; look++) {
            if (isMendingStock(inventory[look])) {
                mendingStock = true;
                break;
            }
        }
        ordered = hasOrders();
    }

    /**
     * Puts the tool in its hand or takes it out, and tells every client watching what it is up to.
     *
     * <p>
     * The equipment slot rather than a packet of the mod's own, because that slot is already
     * broadcast to exactly the set of clients that can see this golem, and is re-sent in full to
     * anyone who walks into range afterwards - which is the part a packet cannot do without
     * inventing a handshake of its own. What sits in the slot is a stable undamaged copy, so the
     * comparison vanilla makes against it every tick finds nothing to send on every tick where
     * nothing changed.
     *
     * <p>
     * It carries the tool whenever it has one, not only while it is swinging it. A golem that
     * produced a tamper for a stroke and put it away again read as a conjuring trick, and it also
     * cost two equipment broadcasts per stroke where this costs one when the tool changes and
     * nothing at all in between. The arm still lifts only for the work, so the stroke is as
     * legible as it was - the difference is that an idle golem now stands there holding the thing
     * it works with, which is the honest picture of what it is carrying.
     */
    private void publish() {
        ItemStack wanted = toolShown == null ? ItemStack.EMPTY : toolShown;
        if (getMainHandItem() != wanted) setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wanted);

        int packed = (busyTicks > 0 ? busyKind & GolemCombat.BUSY_MASK : GolemCombat.BUSY_NONE)
            | (toolSlot >= 0 ? GolemCombat.ARMED_BIT : 0)
            | (mendingStock ? GolemCombat.STOCK_BIT : 0)
            | (ordered ? GolemCombat.ORDERS_BIT : 0)
            | (fleeing ? GolemCombat.FLEE_BIT : 0)
            | (shortOfBlocks ? GolemCombat.SHORT_BIT : 0)
            | (chewTicks > 0 ? GolemCombat.CHEW_BIT : 0);
        // Narrowed before it is compared, and that is not a formality. The eating flag is the top
        // bit of the byte, so a chewing golem packs 128 or more while the byte that holds it reads
        // back as a negative number, and an int-to-int comparison of the two can never agree. The
        // watcher itself drops the write where nothing changed, so all this ever cost was a boxed
        // byte a tick - but the byte is what is on the wire and the byte is what to compare.
        byte packedByte = (byte) packed;
        if (packedByte != byteWatched(WATCH_STATE)) {
            entityData.set(WATCH_STATE, Byte.valueOf(packedByte));
        }
    }

    // ------------------------------------------------------------------
    // Fighting
    // ------------------------------------------------------------------

    /**
     * Keeps its home the same ground as its work area, which is what stops a fight becoming a
     * journey.
     *
     * <p>
     * Vanilla's home area is not decoration on this entity: the target search refuses anything
     * standing outside it, the chase gives a target up the moment it steps outside, the wander
     * will not choose a spot beyond it, and the walk home is the thing that answers when the golem
     * is on the wrong side of it. One anchor and one distance therefore leash all four at once,
     * which is the only way the anchor contract survives combat without a second set of rules that
     * then has to be kept in step with the first.
     */
    private void holdGround() {
        // The fields rather than the accessor, because this runs every tick and that one builds a
        // little array to answer with. By the time it is called the anchor has been written, so
        // the two say the same thing.
        restrictTo(new net.minecraft.core.BlockPos(anchorX, anchorY, anchorZ), (int) guardReach());
    }

    /**
     * Lets go of a fight that has stopped being one.
     *
     * <p>
     * Three things end a fight and only one of them is a task's business. A target that dies or
     * leaves its ground the tasks handle themselves; a target set from outside a task - by another
     * mod, or by anything in a pack that decides this thing should fight - nothing would ever
     * clear, so that is checked here. And a target it simply cannot reach, which is the one that
     * needs a clock: a skeleton on a ledge inside the golem's own ground satisfies every test the
     * chase makes for ever, and a golem holding one would stand there with its tool up until
     * somebody killed it. So a fight that has gone ten seconds without a blow landing is given up,
     * and it declines to start another for five, which is long enough to walk back to its road
     * rather than turn straight round and stare at the same skeleton again.
     */
    private void mindTheTarget() {
        LivingEntity quarry = getTarget();
        if (quarry == null) {
            stalemate = 0;
            return;
        }
        if (!TrmtConfig.golemEnabled || !TrmtConfig.golemCombat
            || !quarry.isAlive()
            || !isArmed()
            || !GolemCombat.isFoe(quarry)
            || !isWithinRestriction(
                new net.minecraft.core.BlockPos(
                    Mth.floor(quarry.getX()),
                    Mth.floor(quarry.getY()),
                    Mth.floor(quarry.getZ())))) {
            setTarget(null);
            return;
        }
        if (++stalemate < GolemCombat.GIVE_UP) return;
        giveUpTicks = GolemCombat.SHRUG;
        setTarget(null);
    }

    /**
     * Monsters and nothing else, decided by class before anything is looked at.
     *
     * <p>
     * The cheap half of the gate: it stops a target search even considering a player, a villager,
     * an animal or another golem, so none of them ever costs a path search. Vanilla's own version
     * of this is not called underneath, and that is deliberate - its two carve-outs compare the
     * exact class, so a pack's own creeper walks straight through them, and the same two are made
     * properly by family in the test the rest of the fighting uses.
     */
    @Override
    public boolean canAttack(LivingEntity other) {
        if (other == null) return false;
        if (!(other instanceof Enemy)) return false;
        if (other instanceof Player) return false;
        if (other instanceof EntityGolemOfWays) return false;
        if (other instanceof Creeper) return false;
        return !(other instanceof Ghast);
    }

    /**
     * The one gate everything that picks a fight has to come through.
     *
     * <p>
     * Targets arrive from three places - the task that answers being hit, the task that answers a
     * monster going for somebody, and anything else in a pack that decides this entity should
     * fight - and only the first two are ours. So the refusal lives here rather than in the tasks:
     * an unarmed golem holds no target at all, a target is never anything but a monster it may
     * hit, and one it has just given up on cannot be handed straight back to it.
     */
    @Override
    public void setTarget(LivingEntity quarry) {
        if (quarry != null) {
            if (!TrmtConfig.golemEnabled || !TrmtConfig.golemCombat) return;
            if (giveUpTicks > 0) return;
            if (!isArmed()) return;
            if (!GolemCombat.isFoe(quarry)) return;
        }
        if (quarry != getTarget()) stalemate = 0;
        super.setTarget(quarry);
    }

    /**
     * What a swing actually does, which the base class answers for nothing but a player.
     *
     * <p>
     * Left to {@link GolemCombat#strike}, which is where the whole of the fighting lives and where
     * the size of the blow is argued.
     */
    @Override
    public boolean doHurtTarget(Entity victim) {
        return GolemCombat.strike(this, victim);
    }

    /**
     * The client's half of the blow.
     *
     * <p>
     * Nothing but the animation: the damage happened on the server and arrived by the ordinary
     * route. Anything that is not ours is handed straight back up, because two of the numbers that
     * come through here are being hurt and dying.
     */
    @Override
    public void handleEntityEvent(byte status) {
        if (status == STATUS_SWING) {
            blowTicks = GolemCombat.BLOW_TICKS;
            return;
        }
        super.handleEntityEvent(status);
    }

    // ------------------------------------------------------------------
    // IInventory - the sixteen slots, and the upgrade beside them
    // ------------------------------------------------------------------

    /** The upgrade lives past the widest the storage gets; see {@link #UPGRADE_SLOT}. */
    public int upgradeSlot() {
        return UPGRADE_SLOT;
    }

    /**
     * Every slot it could ever have, whatever is fitted to it right now.
     *
     * <p>
     * Fixed for the same reason the upgrade's index is: a size that moves under a container is a
     * container addressing the wrong things. What the golem is currently <em>using</em> is
     * {@link #slotCount()}, and that is what decides which of these the screen shows.
     */
    @Override
    public int getContainerSize() {
        return DEEP_SLOTS + 1;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == upgradeSlot()) return orEmpty(upgrade);
        return slot < 0 || slot >= inventory.length ? ItemStack.EMPTY : orEmpty(inventory[slot]);
    }

    /**
     * An empty slot, said the way 1.12.2 says it.
     *
     * <p>
     * The other edition hands out a null for one, and the mod's own reads all test for a
     * null and go on doing so. What changed is who else is listening: a null reaching
     * vanilla's container or hopper code here is a crash rather than an empty square, so
     * it is turned at this edge and nowhere else.
     */
    private static ItemStack orEmpty(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * Takes part or all of a slot, which is the screen's door out of the golem.
     *
     * <p>
     * It cannot tell a player from a caller that never asked {@link #canTakeItemThroughFace}: a mod that
     * moves items without the sided checks comes through here as well, and takes whatever it likes.
     * The same holds for {@link #getStackInSlotOnClosing}, and for changing a stack handed out by
     * {@link #getItem} in place. Refusing any of those would refuse the golem's own screen, so
     * they stay open; what can be refused is refused where a hopper asks.
     */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack held = getItem(slot);
        if (held.isEmpty()) return ItemStack.EMPTY;
        if (held.getCount() <= amount) {
            setItem(slot, ItemStack.EMPTY);
            return held;
        }
        ItemStack taken = held.split(amount);
        if (held.isEmpty()) setItem(slot, ItemStack.EMPTY);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack held = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return held;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == upgradeSlot()) {
            setUpgradeStack(stack);
            return;
        }
        if (slot < 0 || slot >= inventory.length) return;
        // The game hands EMPTY for nothing at this version; the storage keeps null, which is what every reader asks
        // for (0.9.222, spec GO28). getItem turns it back into EMPTY on the way out.
        inventory[slot] = stack == null || stack.isEmpty() ? null : stack;
        // Somebody has just reached in. Whatever it was carrying a moment ago is no longer a safe
        // answer, and the difference between armed and unarmed is the difference between a golem
        // that stands its ground and one that runs.
        takeStock();
    }

    /**
     * Whether it is holding nothing at all.
     *
     * <p>
     * The upgrade counts. A golem carrying nothing but the upgrade fitted to it is not an
     * empty container, and anything that cleared it on that answer would take the rarest
     * item in the mod with it.
     */
    @Override
    public boolean isEmpty() {
        if (!orEmpty(upgrade).isEmpty()) return false;
        for (int slot = 0; slot < inventory.length; slot++) {
            if (!orEmpty(inventory[slot]).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Empties it, upgrade and all.
     *
     * <p>
     * Nothing in this mod calls it; a command or another mod's automation can, and it goes
     * through the same reckoning a reach-in does so that a golem emptied this way knows
     * straight away that it is no longer armed.
     */
    @Override
    public void clearContent() {
        for (int slot = 0; slot < inventory.length; slot++) {
            inventory[slot] = null;
        }
        setUpgradeStack(ItemStack.EMPTY);
        takeStock();
    }

    // The little numbers a furnace shows its burn with are gone from the interface entirely:
    // 1.14 moved them to ContainerData, which is a thing a menu holds rather than something every
    // container has to answer. A golem kept none of them, so there is nothing to move and the three
    // methods that said so are simply not needed. Said here because "it used to answer that and now
    // does not" is worth a line, and because the next port will look for them.

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    /**
     * The other way somebody's hands reach the storage.
     *
     * <p>
     * A container that merges a stack into a slot writes through the stack it was handed and then
     * says so here rather than through the setter, so this is the second of the two doors and has
     * to be watched as closely as the first.
     */
    @Override
    public void setChanged() {
        takeStock();
    }

    @Override
    public boolean stillValid(Player player) {
        return isAlive() && player.distanceToSqr(this) <= 64.0D;
    }

    @Override
    public void startOpen(Player player) {}

    @Override
    public void stopOpen(Player player) {}

    /**
     * What anything other than a player's hands may put in: the ground it mends with, into the
     * storage it currently has, and never into its last empty slot.
     *
     * <p>
     * A player's own slots never ask this. The screen's slots and its shift-click carry the player's
     * rule, which lets tampers and an upgrade in as well, so this is the question for a hopper, a
     * dropper, and any mod that asks before it puts.
     *
     * <p>
     * The upgrade's slot is refused because a part that a hopper fits or swaps is not a small thing
     * to have happen by accident: it re-rolls an unstable golem, and taking the storage upgrade out
     * of a deep one spills forty-eight slots on the floor. Tampers are refused so that the tool a
     * golem depends on is always one somebody chose to give it.
     *
     * <p>
     * The last empty slot is refused because a hopper fills the lowest free slot within a tick. A
     * golem kept topped up that way had the slot its broken tamper left filled with stock before its
     * next stroke, so it had nowhere to put the tamper it went to fetch, and stood unarmed beside a
     * crate of them. Merging into a stack already there is never refused on that account, and the
     * golem's own storing may still take that slot, which is what it is being kept for.
     *
     * <p>
     * Bounded by what the golem is currently using rather than by how many slots it has, which are
     * not the same number. A caller that walks every index this entity admits to having would
     * otherwise have a plain golem go on swallowing gravel into the forty-eight slots it is too
     * narrow to reach - saved, dropped on its death, and invisible to every screen and every piece
     * of its own work until a storage upgrade went in.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= slotCount() || slot == upgradeSlot()) return false;
        if (!isMendingStock(stack)) return false;
        return inventory[slot] != null || leavesAnEmptySlot(slot);
    }

    /**
     * Whether a slot other than this one is empty. Counting stops at the first, and it is only asked
     * of an empty target, so a hopper topping up a part stack never pays for it.
     */
    private boolean leavesAnEmptySlot(int filling) {
        int limit = Math.min(slotCount(), inventory.length);
        for (int i = 0; i < limit; i++) {
            if (i != filling && inventory[i] == null) return true;
        }
        return false;
    }

    /**
     * The storage it currently has, and the same from every side.
     *
     * <p>
     * Every side, so a dropper underneath a golem feeds it as surely as a hopper beside it does. The
     * arrays are made once, because a hopper that moves nothing asks this again on every tick.
     */
    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return widensStorage() ? DEEP_OPEN : BASE_OPEN;
    }

    /**
     * The same answer as {@link #canPlaceItem}, which is also where a slot past the storage in
     * use is refused. It has to be refused there and not only left out of the offered slots: a
     * caller putting in from side minus one walks every slot the golem admits to having and never
     * looks at the list.
     */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return canPlaceItem(slot, stack);
    }

    /**
     * Nothing, to anyone who asks.
     *
     * <p>
     * A hopper underneath a golem, or beside it under its overhang, would otherwise take the tamper
     * and the upgrade out of it - and taking the upgrade spills a deep golem and strips a stout one.
     * What it gathers stays for a player to collect by hand for the same reason: a rule that let
     * seeds out and kept tampers in would be a rule about kinds of item, and a hopper cannot be told
     * which slot holds which.
     *
     * <p>
     * The screen takes things out through {@link #removeItem}, which does not ask this, and so
     * does any caller that moves items without the sided checks. That door cannot be closed without
     * closing the screen, so it is left open and a mod that ignores this question is not refused.
     */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) {
        return false;
    }

    /**
     * What automation may hand a golem: a block of a family the mod wears, and never a tamper.
     *
     * <p>
     * Any family the mod wears, not only those the golem has orders for, because orders change and
     * stock handed over for tomorrow's orders is not a mistake. The same set is what the golem counts
     * as having something to mend with.
     */
    public static boolean isMendingStock(ItemStack stack) {
        return stack != null && !GolemCombat.isTamper(stack) && acceptsIntoStorage(stack);
    }

    /**
     * What it will pick up off the ground, which is a wider set than what you may hand it.
     *
     * <p>
     * The two are deliberately different and were briefly, wrongly, the same. What a player may put
     * in is the tools and the ground it mends with, so the storage cannot be used as a chest. What
     * the golem may pick up is that plus whatever its own work shook loose - the seeds, the flint,
     * the snowballs - because those are its earnings and it is collecting them for you. Once you
     * take them out you cannot put them back, which is the whole point of the asymmetry.
     *
     * <p>
     * It still will not sweep up a battlefield. Mob drops are neither, so a golem that has been
     * fighting does not end up with sixteen slots of rotten flesh and nowhere to put its gravel.
     */
    public static boolean gathersFromGround(ItemStack stack) {
        return acceptsIntoStorage(stack) || com.trmtgtnh.erosion.WearDrops.isWearDrop(stack)
            || com.trmtgtnh.erosion.GroundCover.isCoverDrop(stack);
    }

    /**
     * What a player may put into the storage: the tools it works with, and the ground it mends with.
     * Automation asks {@link #canPlaceItem} instead, which is narrower.
     *
     * <p>
     * Both, because it needs both - a golem that could only be handed tampers could never mend
     * anything, and one that could be handed anything would become a chest with legs. A block
     * counts when it belongs to a family the mod actually wears, which is exactly the set of
     * blocks a mend can be paid in.
     *
     * <p>
     * The tools are the two tamper families rather than everything wearing the tamper marker,
     * because that marker is about a left-click gesture and the two comparison tools wear it too.
     * A slot that accepted a tool the golem can never work with would be a trap, and the golem
     * would go on calling itself empty-handed with one sitting in it.
     *
     * <p>
     * Asked only on the way in. A player may take anything back out, because a golem that has
     * picked up seeds on its round has to be able to hand them over.
     */
    public static boolean acceptsIntoStorage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (GolemCombat.isTamper(stack)) return true;

        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(stack.getItem());
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) return false;
        com.trmtgtnh.surface.SurfaceFamily family = com.trmtgtnh.surface.SurfaceRegistry
            .familyOf(block);
        return family != null && family.staged;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // The tool in its hand is a picture, not a possession: the real one is in the storage
        // below and is saved with it. Left in, the equipment the creature class writes for us
        // would come back on load as a tamper the golem does not have and never had.
        tag.remove("Equipment");
        // Written as HandItems at this version, and the picture came back on load until the next tick replaced it
        // (0.9.222, spec GO11).
        tag.remove("HandItems");
        tag.putInt("radius", radius);
        tag.putBoolean("anchored", anchored);
        tag.putInt("ax", anchorX);
        tag.putInt("ay", anchorY);
        tag.putInt("az", anchorZ);
        tag.putString("summonedBy", summonedBy);
        tag.putString("configuredBy", configuredBy);
        tag.putByteArray("targets", targets);
        if (!blockTargets.isEmpty()) {
            net.minecraft.nbt.ListTag saved = new net.minecraft.nbt.ListTag();
            for (java.util.Map.Entry<String, Byte> entry : blockTargets.entrySet()) {
                CompoundTag one = new CompoundTag();
                one.putString("k", entry.getKey());
                one.putByte(
                    "v",
                    entry.getValue()
                        .byteValue());
                saved.add(one);
            }
            tag.put("blockTargets", saved);
        }

        if (upgrade != null) {
            CompoundTag fitted = new CompoundTag();
            upgrade.save(fitted);
            tag.put("upgrade", fitted);
        }
        // What it has swallowed and not yet laid. Saved for the same reason the storage is: a
        // player handed it over, and a golem that forgot a stack of concrete across a reload would
        // be the mod eating somebody's material.
        if (masonry != null) {
            CompoundTag held = new CompoundTag();
            masonry.save(held);
            tag.put("masonry", held);
        }
        // Both outside the block above, which is where the sulk used to sit. A field written
        // only when an unrelated one happens to be set is a field that forgets itself the
        // moment that other one is empty - and the count in particular is material a player
        // handed over, which the mod may not lose across a reload.
        net.minecraft.nbt.ListTag owed = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < masonryOwed.size(); i++) {
            ItemStack entry = masonryOwed.get(i);
            CompoundTag slot = new CompoundTag();
            // An empty compound for a mouthful whose container has already gone back. The
            // slot is still written, because the number of them is the number of mouthfuls.
            if (entry != null) entry.save(slot);
            owed.add(slot);
        }
        tag.put("masonryOwed", owed);
        tag.putInt("masonrySulk", masonrySulk);
        tag.putBoolean("farmWearing", farmWearing);
        // Saved after all, and for one reason: the extra health an unstable golem has this minute
        // is an attribute modifier, and the game saves that. Come back without the set that
        // justified it and the modifier is stripped on the way in, taking whatever the golem had
        // mended above its plain maximum with it - every load, for ever.
        if (unstableMask() != 0) tag.putInt("unstableSet", unstableMask());

        ListTag items = new ListTag();
        for (int slot = 0; slot < inventory.length; slot++) {
            if (inventory[slot] == null) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("slot", (byte) slot);
            inventory[slot].save(entry);
            items.add(entry);
        }
        tag.put("items", items);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        radius = tag.getInt("radius");
        if (radius <= 0) radius = 16;
        anchored = tag.getBoolean("anchored");
        anchorX = tag.getInt("ax");
        anchorY = tag.getInt("ay");
        anchorZ = tag.getInt("az");
        // Told to the client as well, because the fields are what the golem walks back to and the
        // watched copy is what its screen shows - and the screen offers that copy back as the
        // thing to type over. A golem restored from disk whose screen said nought, nought, nought
        // would be one square press of Enter away from actually living there.
        publishHome();
        // Missing on a golem saved before this existed, and false is not the default - a fresh
        // round starts by wearing, so an absent key has to read as true rather than as nothing.
        farmWearing = !tag.contains("farmWearing") || tag.getBoolean("farmWearing");
        // Through the setters, which are the only things that publish these to the watcher, and
        // the watcher is not part of NBT. Assigned raw, as they were, a golem that had been saved
        // and loaded held both names on the server and showed neither anywhere: the credit on its
        // screen and in the WAILA tooltip read the watched copy, which was still the empty string
        // it was seeded with. The anchor two lines up is republished for exactly this reason; these
        // two were missed.
        setSummonedBy(tag.getString("summonedBy"));
        setConfiguredBy(tag.getString("configuredBy"));
        blockTargets.clear();
        net.minecraft.nbt.ListTag savedBlocks = tag.getList("blockTargets", 10);
        for (int i = 0; i < savedBlocks.size() && blockTargets.size() < MAX_BLOCK_TARGETS; i++) {
            CompoundTag one = savedBlocks.getCompound(i);
            String key = one.getString("k");
            if (key != null && !key.isEmpty()) blockTargets.put(key, Byte.valueOf(one.getByte("v")));
        }
        byte[] savedTargets = tag.getByteArray("targets");
        java.util.Arrays.fill(targets, (byte) -1);
        for (int i = 0; i < targets.length && i < savedTargets.length; i++) {
            targets[i] = savedTargets[i];
        }

        // Before the upgrade goes back in, because fitting it asks what the golem can do and the
        // answer for an unstable one is this number.
        if (tag.contains("unstableSet")) {
            entityData.set(WATCH_UNSTABLE, Integer.valueOf(tag.getInt("unstableSet")));
        }
        setUpgradeStack(tag.contains("upgrade") ? ItemStack.of(tag.getCompound("upgrade")) : null);
        masonry = tag.contains("masonry") ? ItemStack.of(tag.getCompound("masonry")) : null;
        masonryOwed.clear();
        net.minecraft.nbt.ListTag owed = tag.getList("masonryOwed", 10);
        for (int i = 0; i < owed.size(); i++) {
            CompoundTag slot = owed.getCompound(i);
            masonryOwed.add(slot.isEmpty() ? null : ItemStack.of(slot));
        }
        masonrySulk = tag.getInt("masonrySulk");

        inventory = new ItemStack[DEEP_SLOTS];
        ListTag items = tag.getList("items", 10);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getByte("slot") & 0xFF;
            // A stack whose item is gone loads as EMPTY, which every reader takes for a full slot; a free one is
            // null here (0.9.222).
            ItemStack loaded = ItemStack.of(entry);
            if (slot < inventory.length) inventory[slot] = loaded.isEmpty() ? null : loaded;
        }
        // Nothing about a fight or a stroke survives being put away and taken out again, so a
        // golem always comes back empty-handed and calm. What it is carrying it works out now
        // rather than at its first stroke: until it does it would answer that it has no tamper,
        // and for that second a fully stocked golem would refuse to fight and run from anything
        // standing near it - every time somebody rode back into the chunk.
        busyTicks = 0;
        busyKind = GolemCombat.BUSY_NONE;
        blowTicks = 0;
        fightCooldown = 0;
        stalemate = 0;
        giveUpTicks = 0;
        fleeing = false;
        takeStock();
    }

    /**
     * Everything it was holding falls where it stood, whatever the loot rules say.
     *
     * <p>
     * Not from {@code dropFewItems}, which is where this used to be and is the wrong hook by a long
     * way. Vanilla calls that one only behind the {@code doMobLoot} game rule and behind the drops
     * event - so on a server that has switched mob loot off, which is an ordinary thing to do for
     * the sake of the tick rate, a golem killed by a creeper took sixty-four slots of tools and
     * material and its fitted upgrade with it and left nothing at all. That upgrade can be the most
     * expensive item in the mod.
     *
     * <p>
     * None of it is loot. It is what somebody put there by hand, and a chest does not withhold its
     * contents because mob loot is off. So it is spilled here, before the rule is ever consulted.
     *
     * <p>
     * Here rather than in {@code setDead} deliberately: the demo yard clears its crew with setDead
     * precisely because that does not spill anything, and moving the spill there would have a
     * demonstration scatter sixty-four slots across the floor every time it tidied up.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        if (level != null && !level.isClientSide()) {
            for (int i = 0; i < inventory.length; i++) {
                ItemStack held = inventory[i];
                if (held == null) continue;
                inventory[i] = null;
                spawnAtLocation(held, 0.5F);
            }
            if (upgrade != null) {
                ItemStack fitted = upgrade;
                upgrade = null;
                spawnAtLocation(fitted, 0.5F);
            }
            // Including what it had in its mouth. It was given, not found.
            //
            // The mouthful it was chewing, whole, because nothing has been handed back for it
            // yet. And then whatever is still owed for the ones it had already chewed: a
            // mouthful that came in a container gave that container back at the time and is
            // square, but one that came as a plain block gave nothing back, and the block is
            // exactly what is owed. Telling those apart is what the memory is for - the
            // alternative destroyed somebody's obsidian because it could not distinguish it
            // from a bucket of concrete.
            if (masonry != null) {
                ItemStack swallowed = masonry;
                masonry = null;
                spawnAtLocation(swallowed, 0.5F);
            }
            for (int i = 0; i < masonryOwed.size(); i++) {
                ItemStack owing = masonryOwed.get(i);
                if (owing != null) spawnAtLocation(owing, 0.5F);
            }
            masonryOwed.clear();
        }
        super.die(cause);
    }

    /**
     * Nothing here. What this golem was carrying has already fallen; see {@link #onDeath}.
     *
     * <p>
     * Emptied rather than deleted, so that anything reaching for the vanilla hook finds it saying
     * so rather than finding the old body and concluding the contents drop twice.
     */
    @Override
    protected void dropCustomDeathLoot(net.minecraft.world.damagesource.DamageSource cause,
        int looting, boolean hitRecently) {}

    /**
     * Nothing falls out of its hand, because nothing was ever in it.
     *
     * <p>
     * What the creature class would drop from an equipment slot here is the copy the renderer is
     * shown, and the real tool has already fallen with the rest of the storage above. Left to
     * vanilla it would drop a second one now and then - not often, which is exactly what would
     * make it hard to believe when somebody reported it.
     */
    @Override
    protected void dropEquipment() {}
}

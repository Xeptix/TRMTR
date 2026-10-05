package com.trmtgtnh.entity;

import java.util.List;
import java.util.Random;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.trmtgtnh.config.TrmtConfig;
import com.trmtgtnh.item.ItemChunkTamper;
import com.trmtgtnh.item.ItemTamper;

/**
 * When a Golem of Ways fights, who it fights, and what it fights with.
 *
 * <p>
 * The whole of it turns on one question: is it carrying a tamper. Armed, it hits back at whatever
 * hit it and it steps between a monster and whoever that monster went for. Unarmed it does the
 * opposite and walks away, because a thing built to rake gravel has no business trading blows with
 * a zombie bare-handed. That is the same question its work already turns on, so a golem that can
 * work can defend itself and a golem that can do neither says so in the one screen that already
 * explains a golem standing still.
 *
 * <p>
 * It never starts anything. Unlike the iron golem it does not sweep the neighbourhood for monsters
 * and go looking for them; it answers three things and no others - something hurt it, something is
 * hurting a player, something is hurting a villager - and it answers them inside its own work area
 * and nowhere else. A keeper that chased every zombie it saw would not be anywhere near its road by
 * morning, and the road is the whole point of it.
 */
public final class GolemCombat {

    // ------------------------------------------------------------------
    // The watched byte
    // ------------------------------------------------------------------

    /** Idle: hands empty, whatever it happens to be carrying. */
    public static final int BUSY_NONE = 0;

    /** Wearing a square further along its run. */
    public static final int BUSY_WEAR = 1;

    /** Mending one back, which is the one that spends blocks. */
    public static final int BUSY_MEND = 2;

    /** Swinging at something. */
    public static final int BUSY_FIGHT = 3;

    /** The two bits the busy kind occupies in the watched byte. */
    public static final int BUSY_MASK = 3;

    /**
     * Standing about, in whichever of the three ways it is standing about.
     *
     * <p>
     * Not watched and not packed into anything: every fact these are worked out from is already on
     * the byte below and already reaches the client. A fourth flag would be a second copy of what
     * three existing ones say, and a second copy is a thing that can disagree.
     */
    public static final int IDLE_NONE = 0;

    /** Told nothing at all. */
    public static final int IDLE_UNTOLD = 1;

    /** Told, and without the means. */
    public static final int IDLE_SHORT = 2;

    /** Told, equipped, and finished. */
    public static final int IDLE_DONE = 3;

    /** Set while it is carrying a tamper at all. */
    public static final int ARMED_BIT = 4;

    /** Set while it is carrying ground it could mend with. */
    public static final int STOCK_BIT = 8;

    /** Set while it has been told to hold anything at all. */
    public static final int ORDERS_BIT = 16;

    /**
     * Set while it is walking away from something.
     *
     * <p>
     * Watched for the same reason as the three above: none of this reaches a client on its own, and
     * a golem sprinting off its road is far and away the most alarming thing this feature does. It
     * is worth a line in the tooltip that says plainly what is happening rather than leaving
     * somebody to work it out. Five flags and a two-bit kind was six bits, which left the sign bit
     * clear; {@link #CHEW_BIT} has since taken it, so what is packed is narrowed to a byte before
     * it is compared with the byte that holds it.
     */
    public static final int FLEE_BIT = 32;

    /**
     * How long a change of mind has to hold before the pose believes it.
     *
     * <p>
     * Longer than a slow work period, and that is the whole of the number. The short-of-blocks flag
     * is refreshed once per stroke and cleared eagerly by everything that touches the store - a
     * block picked up, a fetch from a chest, somebody opening the screen - so it drops between two
     * publishes and stays down until the next stroke sets it again. That is right for the tooltip,
     * which should stop complaining the moment the right block goes in, and quite wrong for a pose,
     * which would pulse at the tempo of the work. The next stroke refutes the clear well inside
     * twenty ticks, so a dwell of twenty-five never sees it.
     */
    public static final int IDLE_DWELL = 25;

    /**
     * Set when it wanted to mend a square and could not pay for it.
     *
     * <p>
     * Different from {@link #STOCK_BIT}, and the difference is the useful part. That one says
     * the store holds something the golem could mend with; this one says the square in front
     * of it wanted a block the store has not got. A golem holding a stack of dirt over a worn
     * stone road satisfies the first and fails the second, and it is the second that explains
     * why nothing is happening.
     */
    public static final int SHORT_BIT = 64;

    /**
     * It is eating, and the pose should say so.
     *
     * <p>
     * The last free bit of the state byte, spent deliberately. The busy field is two bits and all
     * four of its values are already spoken for, so widening that would have shifted every flag
     * above it; and a whole data-watcher slot for one boolean costs more traffic than a bit that
     * was going spare. If a second pose ever wants telling, slots 29 to 31 are still free on this
     * entity and that is where it should go rather than here.
     */
    public static final int CHEW_BIT = 128;

    /**
     * Which of the three ways it is standing about, or none because it is not.
     *
     * <p>
     * One place, because a pose can only show one thing at a time where a tooltip can print four.
     * The order is the order somebody would fix them in: a golem with no orders is not short of
     * anything, it has simply not been asked, and telling it what to do is the first move whatever
     * else is also true.
     *
     * <p>
     * The test for being busy belongs to all three rather than to the last of them. Without it a
     * golem that is wearing ground perfectly well while unable to mend any of it would throw the
     * short pose the moment its arm came down between two strokes, which is about a second - and
     * the whole point of these is that they are what a golem does when it has stopped.
     */
    public static int idlePose(EntityGolemOfWays golem) {
        if (golem == null || golem.busyKind() != BUSY_NONE) return IDLE_NONE;
        if (!golem.watchedOrders()) return IDLE_UNTOLD;
        if (!golem.isArmed() || !golem.hasMendingStock() || golem.watchedShortOfBlocks()) return IDLE_SHORT;
        return IDLE_DONE;
    }

    // ------------------------------------------------------------------
    // Timings
    // ------------------------------------------------------------------

    /**
     * Ticks between one blow and the next.
     *
     * <p>
     * Kept here rather than left to the chase task, which cannot be trusted with it: vanilla's
     * attack task counts down to twenty and then tests for twenty or less, so the test passes
     * again on the very next tick and the task calls for a blow every single tick it is in reach.
     * What normally hides that is the victim's own half-second of invulnerability, which throttles
     * a monster to a hit every ten ticks - so a golem leaning on that would strike twice a second
     * and wear its tamper out twice as fast as anything here says it should. Counting the seconds
     * ourselves is the only way the numbers in the config mean what they say.
     */
    public static final int FIGHT_PERIOD = 20;

    /** How long one swing keeps the tool up, so it stays up between blows. */
    public static final int FIGHT_HOLD = 24;

    /**
     * How much longer than a stroke the tool stays up.
     *
     * <p>
     * Longer than the gap between two strokes, so a working golem never puts its tool away in the
     * middle of working; short enough that one which has stopped - because the ground is already
     * right, because the chunk went away, because somebody switched it off - has visibly put it
     * away within a quarter of a second.
     */
    public static final int WORK_HOLD_SLACK = 6;

    /** How long the blow itself takes to play out, which is the arm coming down and back. */
    public static final int BLOW_TICKS = 8;

    /**
     * How long it will keep swinging at something it never manages to hit before letting it go.
     *
     * <p>
     * A monster on a ledge, behind a fence, or across water is a target the chase can hold for
     * ever: the chase gives a target up when it leaves the golem's ground and not otherwise, and a
     * monster standing still inside that ground never triggers it. Without this the golem stands
     * with its tool raised and its feet still, for ever, and every tooltip says it is fighting.
     */
    public static final int GIVE_UP = 200;

    /** How long it refuses to pick another fight after giving one up, so it can get back to work. */
    public static final int SHRUG = 100;

    /** Longest one retreat runs before it stops to look round again. */
    public static final int RUN_TICKS = 100;

    // ------------------------------------------------------------------
    // Distances and speeds
    // ------------------------------------------------------------------

    /** How near something hostile has to be before an unarmed golem walks away from it. */
    private static final float FLEE_RANGE = 10.0F;

    /** How far off something that has already hurt it still counts, seen or not. */
    private static final double STRUCK_RANGE = 24.0D;

    /** How far a retreat looks for somewhere to go, and how far up and down, as vanilla's does. */
    private static final int RUN_SPAN = 16;

    private static final int RUN_RISE = 7;

    /**
     * How fast it walks away, and how fast when the thing is nearly on top of it.
     *
     * <p>
     * Deliberately quicker than its working pace and quicker than a zombie or a skeleton, because
     * a golem that cannot break contact is not running from a fight, it is losing one slowly. Still
     * far short of a spider, which is the honest outcome for something this heavy and the reason
     * running is not a strategy so much as a way of buying the time to be restocked.
     */
    private static final double RETREAT_SPEED = 1.4D;

    private static final double PANIC_SPEED = 1.8D;

    /** How fast it closes on a fight: quicker than working, slower than fleeing. */
    private static final double CHASE_SPEED = 1.2D;

    private GolemCombat() {}

    // ------------------------------------------------------------------
    // Who counts as what
    // ------------------------------------------------------------------

    /**
     * Whether this is a tamper, as against merely something with a tamper's left-click.
     *
     * <p>
     * The marker interface every tamper implements is about the gesture rather than the tool: the
     * two comparison tools implement it as well, because they wanted the same left-click routing,
     * and neither of them has any durability at all. A golem that treated them as tampers would
     * call itself armed while holding one, spend a stroke on it, and - since a maximum of zero
     * makes any damage at all count as broken - destroy somebody's tool on the first square it
     * touched. So this asks about the two families that really are tampers.
     */
    public static boolean isTamper(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemTamper || stack.getItem() instanceof ItemChunkTamper;
    }

    /**
     * Whether this is something an unarmed golem should get away from.
     *
     * <p>
     * {@code Enemy} and nothing else, which is both the test the ward already uses and the test
     * every "is this a monster" question in a pack is asked in. It answers no about players,
     * villagers, animals and both kinds of golem without any of them having to be named, because
     * none of them are monsters - and it is the same reason this entity was deliberately not made
     * an {@code Enemy} itself.
     *
     * <p>
     * What does have to be named is the pet. A mod that hands somebody a tamed hostile has handed
     * them something that is a monster by that test and is also theirs, and a keeper that fled from
     * a player's wolf-thing every time it walked past would look broken.
     */
    public static boolean isDanger(Entity thing) {
        if (!(thing instanceof LivingEntity)) return false;
        if (!(thing instanceof Enemy)) return false;
        if (thing instanceof Player) return false;
        if (thing instanceof EntityGolemOfWays) return false;
        if (thing instanceof TamableAnimal || thing instanceof net.minecraft.world.entity.animal.horse.AbstractHorse) {
            String owner = ownerNameOf((TamableAnimal) thing);
            if (owner != null && !owner.isEmpty()) return false;
        }
        return ((LivingEntity) thing).isAlive();
    }

    /**
     * Who owns a tamed thing.
     *
     * <p>
     * The other edition keeps an owner's name and this one keeps their id, but the only
     * thing either caller wants to know is whether there is somebody, so the id is handed
     * back as text and the question above is left word for word as it was.
     */
    private static String ownerNameOf(TamableAnimal thing) {
        java.util.UUID owner = thing.getOwnerUUID();
        return owner == null ? null : owner.toString();
    }

    /**
     * Whether a path actually reaches where it was asked for.
     *
     * <p>
     * The other edition asks the path itself; 1.12.2 has no such question, so it is asked
     * of the last point the way that one asks it - the two flat coordinates only, and
     * truncated rather than floored, because a path that stops one block short of a
     * retreat is a golem standing still while something walks up to it.
     */
    private static boolean endsAt(Path route, Vec3 wanted) {
        net.minecraft.world.level.pathfinder.Node last = route.getEndNode();
        return last != null && last.x == (int) wanted.x && last.z == (int) wanted.z;
    }

    /**
     * Whether this is something a golem may raise a tamper to, which is less than it will run from.
     *
     * <p>
     * Everything dangerous except the two that answering would cost more than ignoring. A creeper
     * killed at arm's length takes the road with it, and a keeper that blew up the ground it was
     * built to look after would be worse than one that never fought at all; a ghast is out of reach
     * of a thing with no ranged attack, so a golem that tried would simply stand there swinging at
     * the sky. Vanilla makes both of those carve-outs itself, but by comparing the exact class -
     * so a pack's own creeper, which is a different class, walks straight through it. Naming the
     * families instead means a modded creeper is a creeper.
     *
     * <p>
     * Note the asymmetry, which is the point: excluded here and not from {@link #isDanger}, so an
     * unarmed golem still walks away from a creeper. That is the one thing it most needs to do.
     */
    public static boolean isFoe(Entity thing) {
        if (!isDanger(thing)) return false;
        if (thing instanceof Creeper) return false;
        return !(thing instanceof Ghast);
    }

    /** Somebody the golem is here to stand in front of. */
    public static boolean isWard(Entity thing) {
        return thing instanceof Player || thing instanceof Villager;
    }

    // ------------------------------------------------------------------
    // Which tool
    // ------------------------------------------------------------------

    /**
     * The best tamper a golem is carrying, or -1 when it is carrying none.
     *
     * <p>
     * Best means most durable, and that is a decision rather than a shortcut. The mod already
     * treats a material's durability as the thing that says how good it is: a configured grade
     * with no reach written down has its reach worked out from its use count, which is only
     * defensible because the two say the same thing about a metal. There is no other ordering
     * anywhere to borrow - the grade list is whatever a pack configured and comes back in
     * configured order, and the four fixed tiers are ordered by name rather than by quality, gold
     * being deliberately the soft wide one. Durability is the single number every tamper answers,
     * through the same per-stack method the durability bar reads, so all four kinds rank against
     * each other without any of them being asked to rank itself.
     *
     * <p>
     * Above every one of them sits a tamper that never wears out, which is the Wayfarer. That one
     * has to be named rather than measured: it answers a grade's use count like anything else and
     * would otherwise sort as iron, which is the wrong end of the list for the hardest thing in
     * the mod to make.
     *
     * <p>
     * Ties go to the lowest slot, which is the order the old first-found search used, so a golem
     * handed two of the same tamper still works through them in the order they were put in. Two
     * grades can genuinely tie: durability is capped at what a short can hold, and a pack that
     * scales its numbers up will push several materials onto that ceiling together. They are then
     * equally good as far as anything in the mod can tell, and taking the first is as defensible
     * as any other answer.
     */
    public static int bestToolSlot(EntityGolemOfWays golem) {
        ItemStack[] carried = golem.inventory();
        int slots = Math.min(golem.slotCount(), carried.length);
        int found = -1;
        long best = Long.MIN_VALUE;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack held = carried[slot];
            if (held == null || held.isEmpty()) continue;
            if (!isTamper(held)) continue;
            long rank = rankOf(held);
            if (rank > best) {
                best = rank;
                found = slot;
            }
        }
        return found;
    }

    /** How good one tamper is, with anything that never wears ranking above everything that does. */
    private static long rankOf(ItemStack tool) {
        if (tool.getItem() instanceof ItemChunkTamper && !((ItemChunkTamper) tool.getItem()).wearsOut(tool)) {
            return Long.MAX_VALUE;
        }
        // A maximum of zero is how the durability scale says "unbreakable" for the four fixed
        // tiers, which is the same claim by another route and belongs at the same end of the list.
        // The wear accounting knows about that answer too and leaves such a tool alone.
        int max = tool.getMaxDamage();
        return max <= 0 ? Long.MAX_VALUE - 1L : max;
    }

    /**
     * The stack a golem puts in its free hand, which is a copy and deliberately an undamaged one.
     *
     * <p>
     * A copy, because whatever sits in the equipment slot is compared against its own saved copy
     * every tick and broadcast to every client watching the moment the two differ - and the real
     * tool loses a point of durability every time it is used. A live reference would put the whole
     * stack on the wire once a second for a change nobody can see. Damage stripped for the same
     * reason, so the picture only changes when the tool itself does. The grade rides in the
     * stack's own data and is copied along with it, which is what makes the right material's
     * sprite appear in its hand.
     */
    public static ItemStack displayCopy(ItemStack tool) {
        if (tool == null) return null;
        ItemStack shown = tool.copy();
        shown.setCount(1);
        shown.setDamageValue(0);
        return shown;
    }

    /** Whether a copy already in its hand is still a picture of the tool it was made from. */
    public static boolean stillShows(ItemStack shown, ItemStack tool) {
        if (shown == null || tool == null) return shown == tool;
        return shown.getItem() == tool.getItem() && ItemStack.tagMatches(shown, tool);
    }

    // ------------------------------------------------------------------
    // The blow
    // ------------------------------------------------------------------

    /**
     * One swing, and what it costs.
     *
     * <p>
     * A flat number rather than one that follows the tamper's grade, and that is a decision too.
     * The grade list is config, it comes back in whatever order a pack wrote it, and a GregTech
     * pack has hundreds of materials in it - so any curve drawn over that list would be a weapon
     * nobody had balanced, changing under an operator who only meant to add a metal. One number in
     * one place is a number an operator can find and set.
     *
     * <p>
     * The number itself is deliberately small beside the iron golem's seven-to-twenty-one. This
     * one has sixty health and cannot be knocked back, so it wins by outlasting rather than by
     * hitting hard, and a maintenance golem that cleared a spawner in ten seconds would have
     * stopped being a maintenance golem. There is no upward toss either: that launch is the iron
     * golem's signature and borrowing it would make this read as one, where a rammer should drive
     * a thing back and down.
     *
     * <p>
     * The blow spends a point of the tool through the same accounting a stroke of work spends, and
     * that is the part that keeps the whole feature honest. Fighting eats the tampers it was given
     * to work with, so a golem left standing in a mob farm disarms itself - and then, being
     * unarmed, runs. Only a landed blow costs anything; one that arrives while the monster is
     * still shrugging off somebody else's still costs the golem its second.
     */
    public static boolean strike(EntityGolemOfWays golem, Entity victim) {
        if (!TrmtConfig.golemEnabled || !TrmtConfig.golemCombat) return false;
        if (!golem.readyToStrike()) return false;
        if (!isFoe(victim)) return false;
        int slot = golem.findTool();
        if (slot < 0) return false;

        golem.swingTool();
        float force = Math.max(0f, TrmtConfig.golemAttackDamage);
        if (golem.hitsHarder()) force *= 2f;
        boolean landed = victim.hurt(DamageSource.mobAttack(golem), force);
        if (landed) {
            golem.wearTool(slot);
            golem.noteBlowLanded();
            // A vanilla sound rather than one of the mod's own, because the mod ships none for
            // this entity and a name with nothing behind it logs a line every time it is played.
            // The same one its gestures already use, an octave down, so a golem at work and a
            // golem at war sound like the same thing doing different jobs.
            golem.playSound(net.minecraft.sounds.SoundEvents.STONE_BREAK, 1.0F, 0.6F);
        }
        return landed;
    }

    // ------------------------------------------------------------------
    // Fitting the tasks
    // ------------------------------------------------------------------

    /**
     * Adds the fighting and the running to a golem, once, when it is built.
     *
     * <p>
     * Both are installed for good and each decides for itself whether it applies, rather than
     * being added and removed as a tamper is put in and taken out. Task lists are walked with a
     * live iterator while the AI runs, so anything that rebuilt one from a tick would eventually
     * throw; and the two can never both want to run anyway, since one needs a tamper and the other
     * needs the absence of one. Gating them where they are asked costs one field read.
     *
     * <p>
     * The chase sits above everything that moves the golem, so a fight interrupts a stroll, and
     * the retreat sits just under it and above the walk home - which is right, because a golem
     * being chased has more pressing business than its anchor. Nothing here calls for help:
     * vanilla's version of that recruits by exact class out to the whole follow range, which would
     * drag every other golem on the road into a fight none of them can reach without leaving its
     * own ground.
     */
    public static void install(EntityGolemOfWays golem) {
        golem.goals()
            .addGoal(1, new MeleeAttackGoal(golem, CHASE_SPEED, true));
        golem.goals()
            .addGoal(2, new Flee(golem));
        golem.targets()
            .addGoal(1, new HurtByFoe(golem));
        golem.targets()
            .addGoal(2, new Defend(golem));
    }

    /**
     * Hitting back, but only with something in its hand.
     *
     * <p>
     * Vanilla's own retaliation task with one question in front of it. Asked here rather than only
     * where the target is finally set, so an unarmed golem being chewed on is not re-evaluating a
     * fight it cannot have every three ticks.
     *
     * <p>
     * Its leash is narrowed to the same ground the rest of the fighting uses. Left alone it would
     * be the follow range attribute, which is sixty-four blocks on this entity - far enough that a
     * golem could be dragged well off its road by something it merely traded blows with once.
     */
    public static final class HurtByFoe extends HurtByTargetGoal {

        private final EntityGolemOfWays golem;

        public HurtByFoe(EntityGolemOfWays golem) {
            super(golem);
            this.golem = golem;
        }

        @Override
        protected double getFollowDistance() {
            return golem.guardReach();
        }

        @Override
        public boolean canUse() {
            return TrmtConfig.golemEnabled && TrmtConfig.golemCombat && golem.isArmed() && super.canUse();
        }
    }

    /**
     * Stepping in front of somebody a monster has gone for.
     *
     * <p>
     * Written out rather than assembled from vanilla's nearest-attackable task, for two reasons
     * that are really the same reason. That task searches out to the follow range attribute, which
     * is sixty-four blocks here - a box a hundred and twenty-nine across, walked every third tick,
     * per golem, in a mod whose whole fantasy is a golem on every stretch of road. And it hands a
     * filter one candidate at a time, which is enough to ask a monster who it is going for but not
     * enough to ask the other way round.
     *
     * <p>
     * Asking the other way round matters. A monster that has picked a fight usually says so plainly
     * in its own attack target, but a good many do not: vanilla's slimes never set one, they pick a
     * player inside their own movement code, and a pack full of hand-written monsters is full of
     * the same. What every one of them does leave behind is a mark on the victim, because being
     * hurt at all records who did it. So one search, one list, and two ways of reading it: a
     * monster that names a ward as its quarry, or a ward that names the monster as the thing that
     * last hurt it. Between them they cover about everything that can happen to a villager.
     */
    public static final class Defend extends TargetGoal {

        private final EntityGolemOfWays golem;

        private LivingEntity quarry;

        public Defend(EntityGolemOfWays golem) {
            // Sight not required, reachability required. The same pair the iron golem picks, and
            // for the same reason: a golem should step round a corner to a scream, but should not
            // set out after something on a ledge it can never climb.
            super(golem, false, true);
            this.golem = golem;
            setFlags(java.util.EnumSet.of(Goal.Flag.TARGET));
        }

        /**
         * Its own ground and a step past it, rather than the follow range.
         *
         * <p>
         * One number for the search, for whether a candidate is close enough to be worth having,
         * and for how far a fight may wander before it is dropped - which is what keeps the golem
         * on the road it keeps without a second set of rules to hold in step with the first.
         */
        @Override
        protected double getFollowDistance() {
            return golem.guardReach();
        }

        @Override
        public boolean canUse() {
            quarry = null;
            if (!TrmtConfig.golemEnabled || !TrmtConfig.golemCombat) return false;
            if (!golem.isArmed()) return false;

            double reach = getFollowDistance();
            List<LivingEntity> around = golem.level.getEntitiesOfClass(
                LivingEntity.class,
                golem.getBoundingBox()
                    .inflate(reach, 4.0D, reach));

            double nearest = Double.MAX_VALUE;
            for (int index = 0; index < around.size(); index++) {
                LivingEntity suspect = around.get(index);
                if (!isFoe(suspect)) continue;
                double away = golem.distanceToSqr(suspect);
                if (away >= nearest) continue;
                if (!threatens(suspect, around)) continue;
                // Last, because this is the one that pathfinds. Nothing reaches it that was not
                // already the closest monster in the box with somebody in its sights.
                if (!canAttack(suspect, net.minecraft.world.entity.ai.targeting.TargetingConditions.DEFAULT)) continue;
                nearest = away;
                quarry = suspect;
            }
            return quarry != null;
        }

        @Override
        public void start() {
            golem.setTarget(quarry);
            super.start();
        }

        /** Whether this monster has gone for somebody, by either of the two things it leaves behind. */
        private static boolean threatens(LivingEntity suspect, List<LivingEntity> around) {
            if (suspect instanceof Mob && isWard(((Mob) suspect).getTarget())) {
                return true;
            }
            for (int index = 0; index < around.size(); index++) {
                LivingEntity maybe = around.get(index);
                if (!isWard(maybe)) continue;
                if (maybe.getLastHurtByMob() == suspect) return true;
            }
            return false;
        }
    }

    /**
     * Walking away from a fight it has nothing to fight with.
     *
     * <p>
     * Written out rather than built on vanilla's avoidance task, which cannot do this job. That one
     * takes the first entity the chunk happens to list rather than the nearest, so with two
     * monsters about it will cheerfully run from the far one and into the near one; it only sees
     * eight blocks and only what it has line of sight to, so an unarmed golem being shot at from
     * twenty blocks stands still and dies; and it picks where to run with a helper that refuses
     * every spot outside the home area - which for a golem keeping a small patch means it is
     * offered nowhere to go at all and simply never runs.
     *
     * <p>
     * So: the nearest thing it can see, or whatever last hurt it however far off that is standing,
     * and somewhere away from it chosen without reference to the home area. Running is the one
     * thing here that is allowed off the golem's ground, because a retreat bounded by the thing
     * you are retreating around is not a retreat. The walk home is a task of its own and picks the
     * golem up again once the monster has gone.
     *
     * <p>
     * Vanilla's panic task is deliberately not used beside this one either. That one flees the
     * situation rather than the thing - a random spot within five blocks, chosen without reference
     * to where the threat is - and it fires on any damage at all and on catching fire, so an
     * unarmed golem would scuttle when a player tapped it and would run in circles inside a fire it
     * should be walking out of.
     */
    public static final class Flee extends Goal {

        private final EntityGolemOfWays golem;

        private Entity threat;

        private Path route;

        private int until;

        public Flee(EntityGolemOfWays golem) {
            this.golem = golem;
            setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!TrmtConfig.golemEnabled || !TrmtConfig.golemCombat) return false;
            if (golem.isArmed()) return false;

            Entity found = nearestThreat();
            if (found == null) return false;

            Vec3 bolt = boltHole(golem, found);
            if (bolt == null) return false;
            // Somewhere further off than where it is standing, or there is no point going.
            if (found.distanceToSqr(bolt.x, bolt.y, bolt.z) < found.distanceToSqr(golem)) {
                return false;
            }

            route = golem.getNavigation()
                .createPath(bolt.x, bolt.y, bolt.z, 0);
            if (route == null || !endsAt(route, bolt)) return false;
            threat = found;
            return true;
        }

        @Override
        public void start() {
            until = golem.tickCount + RUN_TICKS;
            golem.setFleeing(true);
            golem.getNavigation()
                .moveTo(route, RETREAT_SPEED);
        }

        @Override
        public boolean canContinueToUse() {
            if (!TrmtConfig.golemEnabled || !TrmtConfig.golemCombat) return false;
            // Handed a tamper mid-retreat, it turns round.
            if (golem.isArmed()) return false;
            if (golem.tickCount >= until) return false;
            return !golem.getNavigation()
                .isDone();
        }

        @Override
        public void tick() {
            if (threat == null) return;
            golem.getNavigation()
                .setSpeedModifier(golem.distanceToSqr(threat) < 49.0D ? PANIC_SPEED : RETREAT_SPEED);
        }

        @Override
        public void stop() {
            threat = null;
            route = null;
            golem.setFleeing(false);
            golem.getNavigation()
                .stop();
        }

        /** The nearest monster it can see, or whatever hurt it last, whichever it should mind more. */
        private Entity nearestThreat() {
            // Something that has already hurt it counts however far off it is standing and whether
            // or not it can be seen, which is the only way an unarmed golem answers an archer.
            LivingEntity struckBy = golem.getLastHurtByMob();
            if (struckBy != null && isDanger(struckBy) && golem.distanceToSqr(struckBy) < STRUCK_RANGE * STRUCK_RANGE) {
                return struckBy;
            }

            List<LivingEntity> around = golem.level.getEntitiesOfClass(
                LivingEntity.class,
                golem.getBoundingBox()
                    .inflate(FLEE_RANGE, 4.0D, FLEE_RANGE));
            Entity closest = null;
            double nearest = Double.MAX_VALUE;
            for (int index = 0; index < around.size(); index++) {
                LivingEntity one = around.get(index);
                if (!isDanger(one)) continue;
                double away = golem.distanceToSqr(one);
                if (away >= nearest) continue;
                // Last, because it is a ray through the world. Sight is wanted here so a golem on
                // a road does not bolt from every zombie in the caves under it.
                if (!golem.getSensing()
                    .canSee(one)) {
                    continue;
                }
                nearest = away;
                closest = one;
            }
            return closest;
        }

        /**
         * Somewhere to run, chosen the way vanilla chooses one and without the home clamp.
         *
         * <p>
         * Ten guesses in a box around it, thrown away unless they point away from the thing, and
         * the one the golem's own pathing likes best wins. The clamp vanilla applies to the same
         * search is what makes it useless here, so it is left out and nothing else is changed.
         */
        private static Vec3 boltHole(EntityGolemOfWays golem, Entity threat) {
            double awayX = golem.getX() - threat.getX();
            double awayZ = golem.getZ() - threat.getZ();
            Random dice = golem.getRandom();
            int atX = Mth.floor(golem.getX());
            int atY = Mth.floor(golem.getY());
            int atZ = Mth.floor(golem.getZ());

            float best = -99999F;
            int bestX = 0;
            int bestY = 0;
            int bestZ = 0;
            boolean found = false;
            for (int look = 0; look < 10; look++) {
                int offX = dice.nextInt(2 * RUN_SPAN) - RUN_SPAN;
                int offY = dice.nextInt(2 * RUN_RISE) - RUN_RISE;
                int offZ = dice.nextInt(2 * RUN_SPAN) - RUN_SPAN;
                if (offX * awayX + offZ * awayZ < 0.0D) continue;

                int x = atX + offX;
                int y = atY + offY;
                int z = atZ + offZ;
                float weight = golem.getWalkTargetValue(new net.minecraft.core.BlockPos(x, y, z));
                if (weight <= best) continue;
                best = weight;
                bestX = x;
                bestY = y;
                bestZ = z;
                found = true;
            }
            return found ? new Vec3(bestX, bestY, bestZ) : null;
        }
    }
}

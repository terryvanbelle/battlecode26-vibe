package immediate_defector;

import battlecode.common.*;

import java.util.Random;

/**
 * Iteration 1 (see TRAINING_ALGORITHM.md): a real cooperation-mode strategy.
 * Rat King grows the population and defends itself; Baby Rats collect and
 * deliver cheese, swarm cats when locally outnumbered-in-our-favor, flee a
 * lone cat otherwise, and never initiate a backstab (they only fight enemy
 * rats once `!rc.isCooperation()`, i.e. the other team already broke coop --
 * see TRAINING_ALGORITHM.md's "Cooperation and backstab strategy").
 *
 * Deliberately still simple: no traps, no ratnap/throw, no dirt digging, no
 * multi-King economy, no cheese-spend-on-bite bonus damage. These are the
 * natural next hypotheses (see TRAINING_LOG.md).
 *
 * Symmetry notes (TRAINING_ALGORITHM.md's "Play symmetry"): no hardcoded
 * compass direction is ever used as a default/fallback -- movement targets
 * are always resolved via `MapLocation.directionTo()` (target-relative) or
 * `Direction.rotateLeft()/rotateRight()` (relative to a target-relative
 * direction), and the per-robot RNG is seeded from `rc.getID()` (unique,
 * not team-correlated) rather than a fixed shared seed -- fixing exactly
 * the anti-pattern BC22's LEARNINGS.md documents ("a shared, fixed-seed
 * Random instance produces identical output for corresponding robots on
 * both teams").
 */
public class RobotPlayer {

    /** ARCHETYPE DIFFERENCE (immediate_defector): stays within this
     *  squared radius of its own Rat King. */
    static final int LEASH_RADIUS_SQUARED = 100;

    static Random rng;
    static int builtCount = 0;
    static int buildWindowStart = 0;      // Iteration 38, see runRatKing
    static boolean replacementMode = false; // Iteration 39, see runRatKing
    static int trapsSinceBuild = 0; // Iteration 102, see runRatKing
    static int cheeseCheckpoint = -1;
    static int cheeseCheckpointRound = 0;
    static boolean economyStruggling = false;
    static MapLocation locOneRoundAgo;
    static MapLocation locTwoRoundsAgo;
    static int stuckCycles = 0;
    static MapLocation exploreLocOneCallAgo;
    static MapLocation exploreLocTwoCallsAgo;
    static int exploreStuckCycles = 0;
    // Bug-navigation state (Iteration 35, see moveToward) -- per-robot, since
    // static fields are per-robot instances in Battlecode, not team-shared.
    static MapLocation bugTarget;
    static int bugClosestDistSq = Integer.MAX_VALUE;
    static boolean bugRotateLeft = false;
    static int bugRoundsFollowing = 0;

    public static void run(RobotController rc) throws GameActionException {
        rng = new Random(rc.getID());
        while (true) {
            int roundAtTurnStart = rc.getRoundNum();
            try {
                if (rc.getType() == UnitType.RAT_KING) {
                    runRatKing(rc);
                } else {
                    runBabyRat(rc);
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Exception: " + e);
                e.printStackTrace();
            } finally {
                reportBytecodeBudget(rc, roundAtTurnStart);
                Clock.yield();
            }
        }
    }

    // ---------------------------------------------------------------- King

    static void runRatKing(RobotController rc) throws GameActionException {
        rc.writeSharedArray(0, rc.getLocation().x + 1);
        rc.writeSharedArray(1, rc.getLocation().y + 1);

        // Iteration 11/12 (TRAINING_LOG.md): `whereisthecheese` (only 2
        // cheese mines) showed the King's own unconditional 2/round upkeep
        // (RULES.md) can outrun income no spending policy prevents. Detect
        // a losing economic trend (every 200 rounds, cheese down more than
        // 150 from the last checkpoint) and latch it permanently -- the
        // same `richHome`-style one-time-branch pattern BC22's
        // LEARNINGS.md found effective for economic doctrine.
        if (rc.getRoundNum() - cheeseCheckpointRound >= 200) {
            if (cheeseCheckpoint >= 0 && cheeseCheckpoint - rc.getGlobalCheese() > 150) {
                economyStruggling = true;
            }
            cheeseCheckpoint = rc.getGlobalCheese();
            cheeseCheckpointRound = rc.getRoundNum();
        }
        // Once the economy is both on a losing trend *and* actually
        // critical (cheese already below RESERVE), broadcast desperation.
        // Iteration 11 tried making desperate Baby Rats merely *willing*
        // to fight a sighted enemy pre-backstab -- rejected as inert,
        // because on this map Kings spawn at opposite corners and nothing
        // ever proactively closes that distance. Iteration 12 adds the
        // missing piece: also broadcast a guessed enemy-King location so
        // desperate rats have somewhere to actually go, instead of only
        // reacting to whatever they happen to stumble across. Shared
        // array slot 2: 1 = desperate. Slots 3/4: guessed enemy King
        // x/y+1 (0 = not yet computed).
        final int RESERVE = 150;
        boolean desperate = economyStruggling && rc.getGlobalCheese() < RESERVE;
        if (desperate) {
            rc.writeSharedArray(2, 1);
            if (rc.readSharedArray(3) == 0) {
                // Best guess at the enemy King's location without ever
                // having seen it: maps are guaranteed symmetric
                // (RULES.md), but the specific symmetry type (rotation vs.
                // horizontal/vertical reflection) isn't exposed by
                // RobotController -- only width/height are. 180-degree
                // rotation is guessed as the single most common case
                // (BC22's LEARNINGS.md: even there, where this *was*
                // queryable, several maps turned out non-rotational, so
                // this is a real, accepted source of error, not a
                // guaranteed-correct computation) -- a wrong guess still
                // sends rats generally away from home and across the map
                // rather than continuing to wander locally, which is
                // strictly more likely to encounter the enemy than not
                // trying at all.
                int guessX = rc.getMapWidth() - 1 - rc.getLocation().x;
                int guessY = rc.getMapHeight() - 1 - rc.getLocation().y;
                rc.writeSharedArray(3, guessX + 1);
                rc.writeSharedArray(4, guessY + 1);
            }
        }

        // Iteration 99 (TRAINING_LOG.md): the King's attack moved BELOW the
        // build attempt -- re-testing Iteration 77 against the counter that
        // actually matters.
        //
        // Traced on `bench_stroke__knifefight__botB`, a 17-round loss:
        //
        //     round   them                              us
        //     1-5     SpawnAction every round           SpawnAction x4
        //     6-17    SpawnAction + RatAttack x3-6      RatAttack x1, nothing else
        //     17      -                                 our King dies
        //
        // They spawn 16 rats in 17 rounds; we spawn 4 and then stop. From
        // round 6 our only action is a single RatAttack per round -- that is
        // the KING, swinging instead of building, because
        // `attackNearestHostile` runs before the build and a King has one
        // action per turn. The moment an enemy arrives, production halts.
        //
        // Iteration 77 made exactly this change and I rejected it as inert on
        // 5/162 WINS. I never looked at the early-wipe rate, which is the
        // counter upstream of 91% of these losses and the only one with
        // variance on this instrument. Re-testing on that counter.

        // The King never moved at all in the first cut of this iteration.
        // Cats patrol fixed, map-specific waypoints (RULES.md), so a King
        // that never relocates is a sitting target once one's cycle brings
        // it nearby -- react the same way a Baby Rat does: flee a close cat.
        // (Movement is expensive -- movementCooldown 40 -- so this is a
        // last-resort reflex, not a relocation strategy; still strictly
        // better than never moving at all. Not yet confirmed as a cause of
        // any specific loss -- see the build-throttle note below for the
        // fix that *is* replay-confirmed.)
        RobotInfo[] nearby = rc.senseNearbyRobots();
        RobotInfo nearestCat = nearestOfType(rc, nearby, UnitType.CAT);
        if (nearestCat != null && nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= 20) {
            flee(rc, nearestCat.getLocation());
        }

        pickUpBestNearbyCheese(rc);

        // Confirmed root cause of the first cut's round-145 loss on `tiny`
        // (tools/replay-dump.sh; see TRAINING_LOG.md): the King built Baby
        // Rats every round it could afford to, with no regard for whether
        // income (cheese delivered by those very rats) was keeping pace.
        // Team cheese hit 0 by round ~100 (40 rats built from a starting
        // 2500) while the opponent -- who never builds anything -- still
        // had 2300; the King then starved (RULES.md: 2 cheese/round upkeep,
        // or 10 HP damage if it can't pay) and died at round 145. Keep a
        // fixed reserve so a temporary income gap (economy still ramping
        // up, or a cat blocking access to nearby cheese) can't bankrupt the
        // King outright.

        // Second, independent throttle -- replay evidence on `knifefight`
        // (TRAINING_LOG.md): across 40 builds, findBuildLocation() only ever
        // returned 2 distinct tiles, both right next to the King. That reads
        // as spatial gridlock, not bad luck: cramming dozens of Baby Rats
        // into a tight spawn area (this map's name suggests a chokepoint)
        // means most of them can never leave -- every adjacent tile is
        // already occupied by another rat -- so nobody ever reaches cheese
        // regardless of how good the wander/search logic is. Cap population
        // growth independently of the cheese reserve above.
        //
        // builtCount is *cumulative ever built*, not a live census -- there
        // is no RobotController API for a team-wide live unit count, and a
        // King's own vision (radius^2 25) can't see rats that have wandered
        // off. This errs in the safe direction for a cap specifically
        // (stopping growth a bit early if some built rats already died is a
        // minor inefficiency); it would be the dangerous direction for a
        // "do we have enough" *floor* check, per BC22's LEARNINGS.md
        // cumulative-vs-live pitfall -- not the mistake being made here.
        //
        // Iteration 9 (TRAINING_LOG.md): 15 was picked purely to solve
        // knifefight's spawn gridlock and never revisited since. Two
        // rejected retreat-logic attempts both point at `closeup`'s
        // population bleed against a dedicated aggressor being a
        // cumulative-attrition problem instead -- a healthy, competitive
        // economy (cheeseTransferred roughly even) that the population cap
        // stops from ever converting into a standing army large enough to
        // outlast a war of attrition. Raising it, not yet Gauntlet-verified
        // -- see TRAINING_LOG.md for whether this reintroduces gridlock on
        // knifefight (the reason 15 was chosen) or actually helps.
        // Iteration 38 (TRAINING_LOG.md): a **sliding build budget**, derived
        // from measurement rather than guessed. Six previous attempts to fix
        // this (Iterations 28-31, 34, 37) all tried to *slow* the King down
        // -- scaling reserves, cooldowns, hysteresis, trend detection,
        // congestion limits -- and every single one made things worse.
        // Measuring an actual winning game finally explained why: the King
        // builds all 25 rats in rounds **1 through 25, one per round,
        // back-to-back**, and then never builds again. The maximal early
        // burst isn't the bug, it's the proven-good behavior; every throttle
        // attempt was damaging the thing that works.
        //
        // The only real defect is the second half of that sentence: because
        // `builtCount` is cumulative-ever-built, once it reaches the cap the
        // King is locked out of *replacing* losses for the rest of the game,
        // no matter how many rats have died (confirmed on `closeup`, `tiny`,
        // and `whereisthecheese`). So: keep the cap's per-window value
        // exactly as-is -- preserving the round-1-to-25 burst byte-for-byte
        // -- but let the budget refresh periodically, permitting replacement
        // building later without ever allowing a faster-than-proven ramp.
        // Iteration 39: make replacement building respect its *real* cost.
        // Iteration 38 tied the baseline but broke `minimaze`/`pipes`; the
        // replay showed why, and it was not the congestion story the map
        // names suggested -- the round-400 replacement burst built ~21 rats
        // in 20 rounds and took the treasury from 1660 to 150, never
        // recovering until the King starved.
        //
        // The cause is `BUILD_ROBOT_COST_INCREASE = 10*floor(pop/4)`
        // (RULES.md): build cost scales with *current* population. The
        // opening burst is cheap because population starts at zero and the
        // price ramps up as it grows; a replacement burst beginning at
        // population ~19 starts already-expensive and compounds. "25
        // builds" in round 400 therefore costs far more than the identical
        // "25 builds" in round 1, and refreshing the budget to the same
        // number was never cost-equivalent.
        //
        // So the opening burst keeps spending down to `RESERVE` exactly as
        // it always has (unchanged, proven), while replacement windows
        // require a much deeper buffer -- they may only draw on genuine
        // surplus, never on the King's survival margin. This is the
        // escalating-threshold pattern BC22's `LEARNINGS.md` records for
        // discretionary spending: a committed investment (the opening army)
        // and a discretionary one (topping it back up) should not be gated
        // at the same bar.
        // Iteration 88 (TRAINING_LOG.md): cheese-gated population cap,
        // RETESTED on the mirror after being rejected on a low-resolution
        // instrument.
        //
        // Iteration 78 tried exactly this and scored 4/162 against a 5/162
        // benchmark control -- but the ablation program has since established
        // that the benchmark set (~3% win rate) cannot resolve +/-2 games, and
        // that its headline numbers inverted the sign twice. Iteration 78 was
        // never tested on the mirror.
        //
        // The degenerate state it targets is now measured directly. Tracing
        // `g_iter16__closeup__botA`, our per-100-round action count collapses
        // to 6-13 across rounds 200-599 while cheese sits at **1271-1581**
        // and living rats decay **25 -> 6**. We hit the 25-build window cap
        // around round 50 and then cannot build for ~350 rounds despite
        // having the money -- a cap-blocked stall with a full treasury.
        //
        // My first theory was that REPLACEMENT_RESERVE caused the lull; the
        // cheese trace refutes it (the lull is at HIGH cheese, and activity
        // peaks once cheese falls below the reserve). The cap is the binder.
        //
        // Unlike Iteration 78 this changes the cap ALONE -- Iteration 77's
        // King-attack reorder is not bundled in.
        // Iteration 90 (TRAINING_LOG.md): align the cap gate with the build
        // reserve, 1200 -> 1000, to close a dead band.
        //
        // Tracing the CURRENT build (`g_iter17__closeup__botB`) shows cheese
        // pinned in a narrow band for the whole game -- 1118, 1045, 1034,
        // 1004, 1016, 988, 998, 998, 988, 1012, 992, 1004 -- never escaping
        // ~1000-1100 between rounds 100 and 2000, with activity at 5-19
        // events per 100 rounds and 4-14 rats alive.
        //
        // That is an interaction between two thresholds set independently.
        // The cap gate opens above **1200**; REPLACEMENT_RESERVE blocks
        // building below **1000**. Between them lies a 200-cheese dead band
        // where we are rich enough to keep building at the OLD cap of 25 but
        // never rich enough to unlock 40 -- so the treasury is held at
        // equilibrium and the gate accepted in Iteration 88 is mostly shut
        // for the rest of the game.
        //
        // Setting the gate to the reserve removes the band: whenever we can
        // afford to build at all, we build against the higher cap. This does
        // not touch REPLACEMENT_RESERVE itself, which the ablation program
        // measured at ~+24 points and which stays exactly as is.
        // Iterations 88/90 raised this to a cheese-gated 40 and were
        // ACCEPTED on the mirror (+7.4) and peers (+7.4). Measured against the
        // benchmark set they cost a game, so they are reverted -- see the
        // "four accepts, all reverted" entry in TRAINING_LOG.md.
        // Iteration 147 (TRAINING_LOG.md): LIFT THE CAP WHEN CHEESE IS DEEP.
        //
        // Traced on rift (bench_stroke, botA), our own long-game trajectory
        // against theirs:
        //
        //     round      our rats / cheese      their rats / cheese
        //       125        21 / 1359               16 / 1945
        //       525        29 / 1231               33 / 1940
        //      1125        12 / 2790               67 / 1908
        //      1925        14 / 1598               81 /  478
        //
        // They compound; we do not. Their cheese sits flat near 1900 because
        // they spend everything they earn. Ours oscillates 1000-2800
        // PERMANENTLY UNSPENT while our army never passes ~29. At round 1125 we
        // held 2790 cheese and twelve rats and would not convert. We are not
        // starving in these games -- we are refusing to spend.
        //
        // `builtCount` is cumulative-ever-built and resets each 400-round
        // window, so a flat cap of 25 limits us to 25 builds per window no
        // matter how deep the treasury is. Population is upstream of all three
        // scored terms (catDamage, cheeseTransferred, kings), which is why it
        // outweighs anything the cat work could buy: seeking cats harder moved
        // the cat margin by +2.2 points against a -25.4 gap.
        //
        // WHY THIS IS NOT A SEVENTH REPEAT. Iterations 111/112/113/114/120/125
        // all pushed population and were rejected, root-caused to the cost
        // curve plus King starvation. That root cause is real and this respects
        // it: every one of those raised the cap UNCONDITIONALLY, so it also
        // fired in the 128 short King-kill games where cheese genuinely is
        // scarce and the King starves at 2/round. Gating on held cheese lifts
        // the cap only when the treasury is provably deep. The dose is on WHEN,
        // not on how much.
        //
        // REPLACEMENT_RESERVE is deliberately untouched: at 25 live rats
        // getCurrentRatCost() is 70, so 2790 - 70 >= 1000 passes easily and the
        // reserve is not what binds here. Changing both at once would leave it
        // unknown which one mattered.
        final int MAX_POPULATION = rc.getGlobalCheese() > 1500 ? 60 : 25;
        final int BUILD_WINDOW_ROUNDS = 400;
        // Iteration 92 (TRAINING_LOG.md): let the replacement reserve DECAY
        // late, when a survival buffer is worth less than the rats it buys.
        //
        // Iteration 90's mechanism check located this. Closing the cap-gate
        // dead band widened the cheese range only from 134 to 246 and left
        // cheese hovering at ~900-1150 for the whole game, because **the
        // hover point is set by this constant**: we build until we cannot
        // afford to, so any reserve creates an equilibrium just above itself
        // and moving other thresholds only shifts it slightly.
        //
        // The reserve cannot simply be lowered -- Iteration 87 ablated it and
        // measured ~+24 points, the second-largest effect in the bot. But its
        // JUSTIFICATION is time-dependent in a way the constant is not: it
        // exists to keep the King alive through a future collapse, and after
        // round 1200 there are at most 800 rounds of future left. A rat built
        // then still collects for those 800 rounds; a hoarded 600 cheese does
        // nothing unless the collapse actually arrives.
        //
        // Distinct from Iteration 40's emergency override, which tried to
        // DETECT an emergency (`noVisibleArmy`) and measured inert at 48.1%.
        // This is unconditional and predictable -- no detector to misfire.
        // Iteration 92 decayed this to 400 after round 1200 and was ACCEPTED
        // on the mirror (+3) and peers. It cost a benchmark game -- reverted.
        final int REPLACEMENT_RESERVE = 1000;
        if (rc.getRoundNum() - buildWindowStart >= BUILD_WINDOW_ROUNDS) {
            buildWindowStart = rc.getRoundNum();
            builtCount = 0;
            // Iteration 87 (TRAINING_LOG.md): REPLACEMENT_RESERVE is
            // VALIDATED and large. Ablating it scores 14/54 = 25.9% against
            // the version that has it -- worth ~+24 points, second only to
            // the exploration-heading reassignment's ~+28. Iterations 38/39
            // were accepted at 90.0% on the peer set and this is the first
            // measurement on an instrument with resolution; unlike the other
            // headline claims tested, this one held up.
            replacementMode = true;
        }
        // Iteration 40: emergency override. On `tiny`, tracing the one
        // remaining loss showed the King sitting on 800-950 cheese with
        // **zero** living Baby Rats from round 575 onward, slowly starving
        // while holding it -- because that's below `REPLACEMENT_RESERVE`, so
        // rebuilding was blocked at exactly the moment it mattered most.
        // Hoarding a reserve with no army is strictly worse than spending
        // it: the reserve exists to keep the King alive, and an undefended
        // King with no economy dies anyway. When no allied Baby Rat is
        // visible at all, fall back to the ordinary `RESERVE` bar. (Rats
        // alive but outside the King's vision aren't defending it or
        // feeding it either, and the per-window cap still bounds the
        // response, so the downside of a false positive is small.)
        boolean noVisibleArmy = true;
        for (RobotInfo info : nearby) {
            if (info.getType() == UnitType.BABY_RAT && info.getTeam() == rc.getTeam()) {
                noVisibleArmy = false;
                break;
            }
        }
        // Iteration 84 (TRAINING_LOG.md): the emergency override is KEPT,
        // but it is now known to be worth nothing measurable.
        //
        // Iteration 40 was accepted at a headline "95.0%", corrected here to
        // 62.5% after resyncing stale archetypes, with the note that it
        // "should be treated as provisional until re-measured". Ablating it
        // and playing the version that has it scores **26/54 = 48.1%** --
        // a balanced side split (A 13, B 13), so a genuine null rather than
        // a side artifact. The override is inert to within one game in 54.
        //
        // Kept because removing it is equally neutral and churn has its own
        // risk, but it should not be credited in any future reasoning.
        int buildReserve = (replacementMode && !noVisibleArmy) ? REPLACEMENT_RESERVE : RESERVE;
        // Iteration 48 (TRAINING_LOG.md): **ring the King with rat traps.**
        // The external benchmark showed tournament bots killing us in
        // 21-46 rounds by swarming the King with 7-10 rats, and proved
        // that rearranging our own units cannot stop it (a standing guard
        // of a third of the army changed the result by zero rounds).
        // Traps are the counter we already had and never used:
        // `TrapType.RAT_TRAP` is **50 damage and a 30-round stun for 20
        // cheese, with maxCount 25** -- half an attacker's 100 HP and it
        // is removed from the fight for 30 rounds, at a total cost of 500
        // cheese for a full set against a treasury averaging 4220.
        //
        // Iteration 15 tried traps and rejected them as "never triggered",
        // but that was against peers that never rushed the King; traps
        // laid around a King that nobody attacks are inert by
        // construction. Against opponents whose whole opening is a King
        // rush, the same traps sit exactly on the attack path. The
        // mechanic didn't change -- the opposition did.
        //
        // Interleaved with building rather than deferred until after it:
        // the opening burst runs rounds 1-25 and `bench_spaark` finishes
        // us at round 21, so traps laid only after the burst would arrive
        // after we are already dead. Alternating from round 5 yields
        // roughly ten traps down by round 25 while still building most of
        // the army.
        // Iteration 82: ABLATION of Iteration 48's King trap ring.
        //
        // Iteration 48 is credited as the only change that ever moved a
        // benchmark line (bench_finalist 0% -> 7-10%), and that credit has
        // been load-bearing all session -- it is why Iteration 59's trap
        // deletion was blamed for its benchmark drop, and why Iteration 77
        // deliberately kept traps. But it was measured on the BENCHMARK set,
        // which we now know is lopsided (~3% win rate) and cannot resolve
        // +/-2 games.
        //
        // The g_iter15 head-to-head is a true mirror -- the two files differ
        // only in their package line -- so it sits at 50% by construction and
        // has the resolution to settle this. Turning the ring OFF and playing
        // the version that has it: >50% means the traps have been costing us,
        // ~50% means they are inert, <50% means Iteration 48 is real.
        //
        // Ablation rather than another new mechanism, because the session's
        // failures were mostly new mechanisms and its findings were mostly
        // measurements. This checks an assumption several conclusions rest on.
        // Iteration 96 (TRAINING_LOG.md): the King trap ring is RESTORED,
        // reversing Iteration 82's accept on grounds of REPRESENTATIVENESS.
        //
        // Iteration 82 ablated this ring and scored 57.4% on the mirror --
        // a clean, well-measured result on the instrument with the best
        // resolution available. It was wrong about the game that matters.
        //
        //     instrument        traps OFF      traps ON
        //     benchmarks        2/162          3/162
        //     early wipes       26%            13%
        //     mirror            57.4% (better) --
        //
        // Early King wipes HALVED when the ring came back, to below even the
        // session's starting 16%. The wipes cluster exactly where a rush is
        // possible -- knifefight 6, tiny 6, dirtfulcat 5, thunderdome 4 --
        // and the fastest losses are rounds 17-28, i.e. the King dies before
        // the game begins.
        //
        // Why the mirror could not see it: 0% of mirror losses are early
        // wipes, because our own lineage never rushes. A defensive feature is
        // free to remove on an instrument that never poses the threat it
        // defends against. RESOLUTION AND REPRESENTATIVENESS ARE DIFFERENT
        // PROPERTIES, and the mirror has only the first.
        // Iteration 128 (TRAINING_LOG.md): REACTIVE CAT TRAPS.
        //
        // Found by examining the largest loss bucket, rounds 100-499 (57 of 155
        // losses), which the session had never studied. On
        // `bench_finalist__peaceinourtime__botA` our King goes hp 540 -> 40
        // across rounds 400-484 -- a steady 6.67/round, exactly
        // CAT_SCRATCH_DAMAGE 20 every 3 rounds -- with a healthy 961 treasury,
        // so it is not starvation. A cat parks beside the King and grinds it
        // down, and the King cannot leave: a RAT_KING is size 3 and cannot path
        // through its own army (Iteration 127).
        //
        // Cat traps are strictly the better trap and we have never placed one:
        //     RAT_TRAP(cost 20, damage  50, stun 30, max 25)
        //     CAT_TRAP(cost 10, damage 100, stun 20, max 10)
        // Half the price, double the damage, and triggerTrap credits
        // addDamageToCats(ourTeam, 100) -- the 0.5-weighted score term -- where
        // a bite yields 10 and costs a rat's action and often its life.
        // Cat traps also never initiate a backstab: triggerTrap only calls
        // backstab() for non-CAT_TRAP types.
        //
        // REACTIVE rather than a second ring, because King actions are the
        // scarce resource -- Iteration 122 took the rat-trap ratio from 2:1 to
        // 3:1 and lost four wins. A cat trap is laid only when a cat is
        // actually closing on the King, so an action is never spent otherwise.
        // Trigger radius dosed in Iteration 129: d^2 20 against d^2 36 gave the
        // IDENTICAL win set (8/162, same game gained, none lost) while the wider
        // radius laid more traps and pushed close-spawn wipes 32% -> 34%. The
        // curve is 7 -> 8 -> 8, so the effect saturates at 20 and the extra
        // traps are wasted cheese. Keeping the cheaper dose.
        // Iteration 203 re-dosed this to 36 and reverted: the constant was
        // suspect (Iteration 129 chose 20 under a regime where our own ring
        // revoked our cat-trap rights mid-game), and g_iter27 did restore the
        // capability -- closeup botB goes 0 -> 15 placements. But the binding
        // constraint is the engine's, not ours: TrapType.CAT_TRAP has
        // maxCount 10, and we already place 15-16 per game, i.e. we hold the cap
        // and refill it. Widening 20 -> 36 bought exactly ONE extra trap.
        final int CAT_TRAP_TRIGGER_DSQ = 20;
        if (nearestCat != null
                && nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= CAT_TRAP_TRIGGER_DSQ) {
            for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(rc.getLocation(),
                    GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED)) {
                if (rc.canPlaceCatTrap(loc)) {
                    rc.placeCatTrap(loc);
                    rc.setIndicatorString("cat trap laid at " + loc);
                    return;
                }
            }
        }

        // Iteration 211 (TRAINING_LOG.md): ARM THE RING WHEN ATTACKED, NOT WHEN
        // APPROACHED.
        //
        // The ring is what breaks the peace: triggerTrap credits the backstab to
        // the trap's OWNER, and being the backstabber costs our cat-trap rights
        // permanently plus the 0.5 catDamage weight. A proximity latch cannot
        // tell a rusher from a pacifist -- pure_cooperator walks up to our King
        // and never attacks, and we armed anyway, breaking a peace we wanted.
        //
        // But an opponent that attacks makes ITSELF the backstabber:
        // `backstab(this.team)` fires on biting any non-cat. So "we have been
        // attacked" is already observable for free as !isCooperation(), with no
        // state to track, and it is true of exactly the opponents the ring is for.
        //
        // This is Iteration 185's rule, which was rejected at wipes 14 -> 18. The
        // cause has changed twice: that was g_iter26, before cat traps made the
        // peace valuable, and Iteration 210 has since removed our own first-bite,
        // so cooperation now actually survives against a peaceful opponent.
        final boolean KING_TRAPS_ENABLED = !rc.isCooperation();
        boolean placedTrap = false;
        // Iteration 102 (TRAINING_LOG.md): is the ring UNDERweight? Iteration
        // 101 varied this same ratio toward FEWER traps (one trap per two
        // builds) and early wipes rose 14% -> 18%, close-spawn wipes 56% ->
        // 68% -- independently reconfirming Iteration 96's ON/OFF result
        // through a second, unrelated knob. Two manipulations now agree the
        // ring is worth more than the rats it displaces. Neither tested the
        // other direction.
        //
        // Exact mirror of the rejected dose, so the two are comparable:
        //   TRAPS_PER_BUILD = 1  reproduces the old 1:1 alternation exactly
        //                        (after a build 0 < 1 -> trap; after a trap
        //                        1 !< 1 -> build)
        //   TRAPS_PER_BUILD = 2  trap, trap, build, trap, trap, build ...
        //
        // Self-limiting: RAT_TRAP maxCount is 25 and findTrapLocation returns
        // null once the ring is full, in which case we fall through and build.
        final int TRAPS_PER_BUILD = 2;
        //
        // Iteration 200 (TRAINING_LOG.md): SUPPRESS THE TRAP, KEEP THE CADENCE.
        //
        // Iteration 199 gated this whole branch on a rush signature and gained
        // +4 peer games (pure_cooperator 23/54 -> 29/54, by not being the team
        // that breaks the peace) but broke the close-spawn guard, 4/42 -> 1/42.
        // The reason it moved two things at once: skipping the branch does not
        // merely withhold a trap, it also stops TRAPS_PER_BUILD from withholding
        // two King-actions out of every three, so the King builds many more
        // rats. On minimaze vs immediate_defector the control places 7 rings and
        // WINS at r2000 while 199 places 0 and LOSES -- on a map where an enemy
        // rat never once comes within d^2 25 of our King in 2000 rounds, so that
        // ring was never acting defensively. Its value there was cadence.
        //
        // So separate them: take the identical decision the control would take,
        // consume the same King-action, and simply place nothing. Dirt was the
        // obvious filler and is wrong -- it is impassable, and the log already
        // records both Kings boxed in by dirt on `closeup` building zero rats.
        if (KING_TRAPS_ENABLED && builtCount >= 5 && trapsSinceBuild < TRAPS_PER_BUILD
                && rc.getGlobalCheese() > RESERVE + 100) {
            MapLocation trapSpot = findTrapLocation(rc);
            if (trapSpot != null && rc.canPlaceRatTrap(trapSpot)) {
                rc.placeRatTrap(trapSpot);
                placedTrap = true;
                trapsSinceBuild++;
            }
        }
        if (placedTrap) {
            rc.setIndicatorString("king trap laid; traps=" + rc.getNumberRatTraps());
            return;
        }
        MapLocation buildLoc = findBuildLocation(rc);
        if (buildLoc != null && rc.canBuildRat(buildLoc)
                && rc.getGlobalCheese() - rc.getCurrentRatCost() >= buildReserve
                && builtCount < MAX_POPULATION) {
            rc.buildRat(buildLoc);
            builtCount++;
            trapsSinceBuild = 0;
        } else if (buildLoc == null) {
            // Replay evidence on `closeup` (TRAINING_LOG.md, tools/replay-dump.sh's
            // new terrain dump): both Kings spawned boxed in entirely by DIRT
            // (impassable until dug, unlike a permanent wall), with zero open
            // tile anywhere in the build radius -- 0 Baby Rats built the whole
            // game, for either team, on this map specifically. Dig out.
            digTowardOpenSpace(rc);
        }

        // Iteration 99: only now, having tried to build, spend the action on
        // defence. A King bite is RAT_BITE_DAMAGE 10; a rat that collects for
        // the rest of the game is worth more.
        attackNearestHostile(rc, desperate);

        rc.setIndicatorString("king cheese=" + rc.getGlobalCheese()
                + (nearestCat != null ? " cat@" + nearestCat.getLocation() : ""));
    }

    static void digTowardOpenSpace(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (!info.isDirt()) continue;
            if (!rc.canRemoveDirt(info.getMapLocation())) continue;
            int d = info.getMapLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info.getMapLocation();
            }
        }
        if (best != null) {
            rc.removeDirt(best);
        }
    }

    /**
     * Iteration 48: a tile to trap, preferring the ring just outside the
     * King rather than right against it -- attackers must cross that ring
     * to reach the King, and a trap on the King's own doorstep is one an
     * attacker only touches after it is already in bite range.
     */
    static MapLocation findTrapLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(me,
                GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED)) {
            if (!rc.canPlaceRatTrap(loc)) continue;
            int d = loc.distanceSquaredTo(me);
            int score = -Math.abs(d - 5); // prefer the ring at distance^2 ~5
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    static MapLocation findBuildLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(me, GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED)) {
            if (!rc.canBuildRat(loc)) continue;
            int d = loc.distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ Baby Rat

    static void runBabyRat(RobotController rc) throws GameActionException {
        // Iteration 24 (TRAINING_LOG.md): tracked once per round, here,
        // rather than inside tryMove() itself -- deliverCheese()/
        // collectCheese()/engage()/flee() can each fall through to a
        // further movement attempt in the same turn if an earlier one
        // fails, so tryMove() can run 0-2 times per round and isn't a
        // reliable place to count rounds. Detects a stable 2-tile
        // oscillation (see tryMove() for what it does about it).
        MapLocation here = rc.getLocation();
        if (here.equals(locTwoRoundsAgo)) {
            stuckCycles++;
        } else {
            stuckCycles = 0;
        }
        locTwoRoundsAgo = locOneRoundAgo;
        locOneRoundAgo = here;

        RobotInfo[] nearby = rc.senseNearbyRobots();

        MapLocation kingLoc = readHomeKingFromSharedArray(rc);
        for (RobotInfo info : nearby) {
            if (info.getType() == UnitType.RAT_KING && info.getTeam() == rc.getTeam()) {
                kingLoc = info.getLocation(); // freshest -- overrides the shared-array value
            }
        }

        if (rc.getRawCheese() > 0 && kingLoc != null) {
            if (deliverCheese(rc, kingLoc)) return;
        }

        RobotInfo nearestCat = nearestOfType(rc, nearby, UnitType.CAT);
        if (nearestCat != null) {
            int allies = countAlliesNear(rc, nearby, nearestCat.getLocation(), 8);
            // Iteration 204 (TRAINING_LOG.md): BUY catDamage WITH TRAPS, NOT TEETH.
            //
            // 19 of g_iter27's 28 peer losses are decided on POINTS at r2000, and
            // catDamage share decides essentially all of them -- median share
            // 35.1% against cheese already at parity. Five are within 4.4 points,
            // needing only a few hundred cat damage.
            //
            // Iteration 179 bought that currency by sending rats to ENGAGE cats
            // and cost 3 peer games, because closing on a cat gets rats scratched
            // for 20 and stops them foraging. A trap buys the same currency from
            // a different source, and the engine is explicit:
            //
            //   if (type == CAT_TRAP && robot.getType().isCatType() && hp > 0)
            //       teamInfo.addDamageToCats(trap.getTeam(), min(damage, hp));
            //   if (trap.getType() != TrapType.CAT_TRAP)  ... backstab(...)
            //
            // so a cat trap credits its FULL 100 damage to the trap's owner, and
            // -- unlike a rat trap -- never triggers the backstab, which is what
            // makes this compatible with g_iter27 rather than in tension with it.
            // At 10 cheese for 100 damage it is also the cheapest damage in the
            // game (RAT_TRAP is 20 for 50).
            //
            // Placed only where it will actually fire: within our own build radius
            // (BUILD_DISTANCE_SQUARED 2) AND within the trap's own trigger radius
            // of the cat, i.e. right beside it. Otherwise fall through and bite.
            //
            // GATED PAST THE WIPE WINDOW. Laying the trap consumes the rat's
            // action and returns, so a rat that would have FLED now stands beside
            // the cat and takes the 20-damage scratch. Ungated this cost
            // close-spawn wipes 14/42 -> 20/42 and close-spawn wins 4 -> 2, even
            // while benchmarks rose 8 -> 10 and peers 80 -> 91. The two effects
            // separate cleanly in time: every early wipe is over before round 100,
            // whereas the points games this wins run to r2000, so nothing of value
            // is given up by staying out of the way early.
            final int CAT_TRAP_CHEESE_FLOOR = 200;
            final int CAT_TRAP_FIRST_ROUND = 100;
            if (rc.getRoundNum() >= CAT_TRAP_FIRST_ROUND
                    && rc.getGlobalCheese() > CAT_TRAP_CHEESE_FLOOR) {
                MapLocation catLoc = nearestCat.getLocation();
                for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(
                        rc.getLocation(), GameConstants.BUILD_DISTANCE_SQUARED)) {
                    if (loc.distanceSquaredTo(catLoc) <= TrapType.CAT_TRAP.triggerRadiusSquared
                            && rc.canPlaceCatTrap(loc)) {
                        rc.placeCatTrap(loc);
                        rc.setIndicatorString("cat trap beside cat @" + loc);
                        return;
                    }
                }
            }
            // Replay evidence (TRAINING_LOG.md, `pure_cooperator` mirror-match
            // trace): catDamage stayed [0,0] all session despite cats visibly
            // scratching/killing our own rats dozens of times per game -- a
            // lone rat within range 8 always fled without ever attacking,
            // even when already in bite range (ATTACK_DISTANCE_SQUARED=2) of
            // the cat that's about to hit it anyway. Take the free hit before
            // (or instead of) fleeing -- it costs nothing extra this turn,
            // the cat isn't going to *not* attack because we didn't, and it's
            // the only thing that's ever put a nonzero number in catDamage.
            if (rc.canAttack(nearestCat.getLocation())) {
                // Iteration 83 (TRAINING_LOG.md): the 4-cheese boosted bite
                // is KEPT, after a direct test that had never been run.
                //
                // The log had rejected it -- "REJECT the cheese-boosted bite
                // entirely (Iteration 45), including the 4-cheese version" --
                // and the revert was then never applied, so it stayed live.
                // Finding that looked like a straightforward defect.
                //
                // But the rejection was an INFERENCE, not a measurement.
                // Iteration 45 measured 4 cheese at 29/54 (53.7%) and 16
                // cheese at 20/54 (37.0%), then reasoned that a negative
                // slope at the high dose condemned the low one. That step
                // assumes the response is monotone. It is not: removing the
                // boost entirely scores **25/54 (46.3%)** against the version
                // that has it, i.e. the 4-cheese build wins **53.7%** -- the
                // same figure Iteration 45 measured, now reproduced against a
                // different opponent on the mirror.
                //
                // Three points, 0 / 4 / 16 cheese, describe a concave curve
                // with an interior optimum near 4, not a monotone decline.
                // Keeping it.
                // Restored EXACTLY as g_iter16 has it -- the guarded form is
                // what measured 53.7%, and an unguarded rc.attack(loc, 4)
                // would both change behaviour and risk a GameActionException
                // when the boost is unaffordable.
                final int BITE_BOOST_CHEESE = 4;
                if (rc.getGlobalCheese() > 1000
                        && rc.canAttack(nearestCat.getLocation(), BITE_BOOST_CHEESE)) {
                    rc.attack(nearestCat.getLocation(), BITE_BOOST_CHEESE);
                } else {
                    rc.attack(nearestCat.getLocation());
                }
                return;
            }
            // High-risk structural change (TRAINING_ALGORITHM.md): the
            // >=3-ally swarm gate essentially never fired -- rats
            // deliberately spread out for cheese search (Iteration 1-4),
            // so 3 of them converging on the same cat at once was rare
            // luck, not a real policy. Meanwhile a cat's scratch reaches
            // its whole vision cone (radius^2 17, ~4.1 tiles) but a Baby
            // Rat's bite only reaches range 2 (~1.4 tiles), so the old
            // "flee anything within 8" threshold kept a fleeing rat
            // inside the cat's engagement range the entire time it was
            // trying to escape, without ever closing to bite range either
            // -- worst of both outcomes.
            //
            // DPS math favors fighting once adjacent regardless of allies:
            // CAT_SCRATCH_DAMAGE=20 every ~3 rounds (actionCooldown 30)
            // = ~6.67 dmg/round average, vs. RAT_BITE_DAMAGE=10 every
            // round (actionCooldown 10) = 10 dmg/round -- a lone Baby Rat
            // that reaches melee range out-trades a cat once there, even
            // though it can't out-tank one (100 HP vs. 4000). A rat that
            // commits instead of fleeing trades a cheap unit (~10-30
            // cheese) for real, otherwise-nonexistent cat damage. Not yet
            // Gauntlet-verified -- see TRAINING_LOG.md for the result.
            if (allies > 1 || rc.getHealth() > 30) {
                if (engage(rc, nearestCat.getLocation())) return;
            } else if (nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= 8) {
                // Critically low HP and no help nearby: not worth dying on
                // the approach for a hit that likely never lands. Flee.
                if (flee(rc, nearestCat.getLocation())) return;
            }
        }

        // Iteration 11 (TRAINING_LOG.md): shared array slot 2 -- written by
        // our own King -- signals our own economy is critically struggling.
        // A Baby Rat is willing to deliberately trigger a backstab against
        // a sighted enemy even while still nominally cooperating: certain
        // starvation is worse than betting on a combat edge we've already
        // demonstrated (see the King-side comment for the full reasoning).
        // Iteration 210 (TRAINING_LOG.md): the `desperate` licence to bite first
        // is REVOKED. Iteration 11 justified it as "certain starvation is worse
        // than betting on a combat edge we've already demonstrated". Both halves
        // are now false. We have no combat edge -- we trade 7 kills for 347
        // losses (0.02:1) -- and since g_iter27/g_iter28 cooperation is worth far
        // more than a bite: `backstab(this.team)` fires on biting ANY non-cat, and
        // being the backstabber costs us our cat-trap rights permanently plus the
        // 0.5 catDamage weight, which is the term that decides these games.
        //
        // Traced on popthecork botB: cooperation flips at r600 with no rat trap on
        // the board from either side, our King takes its first damage at r975 and
        // dies by r1034 to SIXTY bites of exactly RAT_BITE_DAMAGE 10 -- from a
        // PURE COOPERATOR, which cannot attack until someone breaks the peace.
        boolean desperate = true;  // ARCHETYPE: immediate defector
        if (true) {  // ARCHETYPE: immediate defector always fights
            // Replay evidence (TRAINING_LOG.md, `closeup` vs. `immediate_defector`):
            // this only ever attacked an enemy rat already in bite range,
            // never closed distance on one it could see but not yet reach --
            // a purely passive defense against an opponent that actively
            // hunts us. Our own population went to *zero* by round 800 (all
            // Baby Rats killed, none replaced fast enough) while our King
            // starved alone afterward with no economy left. Chase like
            // cat-engagement already does, instead of waiting to get lucky.
            RobotInfo enemy = nearestEnemyRat(rc, nearby);
            if (enemy != null) {
                if (rc.canAttack(enemy.getLocation())) {
                    rc.attack(enemy.getLocation());
                    return;
                }
                // Iteration 159 (TRAINING_LOG.md): RE-DOSE on g_iter25. Iteration
                // 145 measured d^2 20 at 6/162 against a then-baseline of 8/162
                // and I read three points as an interior optimum. Both premises
                // have since moved: a "-2 games" delta was shown in Iteration
                // 151 to be eight games churning BOTH ways (0.8 sigma), and
                // g_iter25 changed movement outright -- 0% strafes against
                // 50.6%, and 26% more moves per game. The cost of chasing is
                // travel time, and travel time just fell, so a radius tuned on
                // a bot that moved at 0.55x speed on half its steps is not
                // obviously tuned for this one.
                // Iteration 160: the wider radius is CONDITIONAL on the game
                // having survived its opening. Iteration 159 ran d^2 20
                // unconditionally and the game-by-game diff was structural
                // rather than churn: all three games GAINED were longer maps
                // (whatsthecatdoin r1009 and r533, closeup r738) and both games
                // LOST were close-spawn popthecork, with early wipes rising
                // 14 -> 17 directionally and close-spawn wins halving 4/42 ->
                // 2/42. Chasing further costs travel time we cannot afford
                // while a rush is still live, and buys kills once it is not.
                // Round 300 is past the entire wipe window -- every early wipe
                // is before round 100 and the fastest losses are rounds 19-28 --
                // which is the same clause that restored the guard in
                // Iteration 150.
                final int CHASE_RADIUS_DSQ = rc.getRoundNum() >= 300 ? 20 : 8;
                if (enemy.getLocation().distanceSquaredTo(rc.getLocation()) <= CHASE_RADIUS_DSQ) {
                    if (engage(rc, enemy.getLocation())) return;
                }
            }
            // Iteration 12 (TRAINING_LOG.md): Iteration 11's desperation
            // signal alone was rejected as inert -- rats only ever *react*
            // to an enemy already sighted, and on maps with distant spawns
            // that never happens. When desperate with no enemy currently
            // visible, actively path toward the King's guessed
            // enemy-King location (shared array slots 3/4 -- a 180-degree-
            // rotation guess, not guaranteed correct, see the King-side
            // comment) instead of continuing normal cheese/explore
            // behavior. This deliberately forces a crossing instead of
            // waiting for one.
            if (enemy == null && desperate) {
                // Iteration 138 (TRAINING_LOG.md): ABLATION of the desperation
                // raid. The King broadcasts a GUESSED enemy-King location into
                // slots 3/4 assuming 180-degree rotational symmetry. Checked
                // against every benchmark map from the replay headers, that
                // guess is correct on 11 maps and WRONG on 16 --
                // `hatefullattice` guesses (38,2) against an actual (11,2), 27
                // tiles away. The comment above calls rotation "the single most
                // common case"; symmetry 0 holds on 11 of 27 while reflections
                // hold on 16.
                //
                // It also fires exactly when we are most fragile: `desperate`
                // needs economyStruggling AND cheese < RESERVE 150, i.e.
                // near-bankruptcy, and starvation is roughly half of mid-game
                // losses. On 16 maps in 27 it marches the surviving rats to an
                // empty patch instead of collecting.
                //
                // Only the RAID MOVEMENT is gated; the `desperate` flag itself
                // is untouched, so the combat side is unchanged.
                final boolean DESPERATE_RAID = false;
                int gx = rc.readSharedArray(3);
                int gy = rc.readSharedArray(4);
                if (gx != 0 && gy != 0) {  // ARCHETYPE: raid is the whole point
                    if (moveToward(rc, new MapLocation(gx - 1, gy - 1), true)) return;
                }
            }
        }

        // ARCHETYPE (immediate_defector): stay within LEASH_RADIUS_SQUARED
        // of our own King rather than ranging like the main bot.
        if (kingLoc != null
                && rc.getLocation().distanceSquaredTo(kingLoc) > LEASH_RADIUS_SQUARED) {
            if (moveToward(rc, kingLoc, true)) return;
        }

        if (collectCheese(rc)) return;

        explore(rc);
    }

    static MapLocation readHomeKingFromSharedArray(RobotController rc) throws GameActionException {
        int kx = rc.readSharedArray(0);
        int ky = rc.readSharedArray(1);
        if (kx == 0 || ky == 0) return null; // never written yet
        return new MapLocation(kx - 1, ky - 1);
    }

    static boolean deliverCheese(RobotController rc, MapLocation kingLoc) throws GameActionException {
        if (rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            rc.transferCheese(kingLoc, rc.getRawCheese());
            return true;
        }
        // Same fix as collectCheese() -- only claim the turn if movement
        // actually succeeded, so a blocked path falls through to
        // cat-engagement/explore instead of camping in place forever. Still
        // carrying cheese, so this is retried first again next turn.
        //
        // The one caller that gets bug-navigation (Iteration 35): the King is
        // a genuinely fixed target, and this is exactly where the confirmed
        // 340-round undelivered-cheese maze trap was traced.
        return moveToward(rc, kingLoc, true, true);
    }

    static boolean collectCheese(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.getCheeseAmount() <= 0) continue;
            int d = info.getMapLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        if (best == null) return false;
        MapLocation loc = best.getMapLocation();
        if (rc.canPickUpCheese(loc)) {
            rc.pickUpCheese(loc);
            return true;
        }
        // Replay evidence (TRAINING_LOG.md, `knifefight`, tools/replay-dump.sh's
        // --robot tracker): this used to unconditionally return true once *any*
        // cheese tile was sighted, even one that turned out unreachable (behind
        // an obstacle `moveToward`'s single-step routing can't get around). That
        // permanently starved `explore()` of ever running again for that robot
        // -- three independently-tracked rats each got stuck at a fixed (x,y)
        // for hundreds of rounds, moveCD/turnCD stuck at 0 (fully able to act)
        // the entire time, because every single turn re-chose the same stuck
        // target instead of ever giving up on it. Now: only claim this turn if
        // movement toward the target actually succeeded, so a genuinely
        // unreachable target falls through to explore() instead of camping
        // forever.
        return moveToward(rc, loc, true);
    }

    static boolean engage(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.canAttack(target)) {
            rc.attack(target);
            return true;
        }
        // Same fix as collectCheese()/deliverCheese() -- claim the turn only
        // if movement actually made progress.
        return moveToward(rc, target);
    }

    static boolean flee(RobotController rc, MapLocation threat) throws GameActionException {
        Direction away = rc.getLocation().directionTo(threat).opposite();
        return tryMove(rc, away);
    }

    // ------------------------------------------------------------- Shared

    static void attackNearestHostile(RobotController rc, boolean desperate) throws GameActionException {
        MapLocation me = rc.getLocation();
        int rangeSq = rc.getType() == UnitType.RAT_KING
                ? GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED
                : GameConstants.ATTACK_DISTANCE_SQUARED;
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : rc.senseNearbyRobots(rangeSq)) {
            // Iteration 210: `desperate` no longer licenses biting first. See
            // runBabyRat -- biting any non-cat makes US the backstabber.
            boolean hostile = info.getType() == UnitType.CAT
                    || (!rc.isCooperation() && info.getTeam() != rc.getTeam());
            if (!hostile) continue;
            if (!rc.canAttack(info.getLocation())) continue;
            int d = info.getLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        if (best != null) {
            rc.attack(best.getLocation());
        }
    }

    static void pickUpBestNearbyCheese(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos(GameConstants.CHEESE_PICK_UP_RADIUS_SQUARED)) {
            if (info.getCheeseAmount() <= 0) continue;
            if (!rc.canPickUpCheese(info.getMapLocation())) continue;
            int d = info.getMapLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        if (best != null) {
            rc.pickUpCheese(best.getMapLocation());
        }
    }

    static RobotInfo nearestOfType(RobotController rc, RobotInfo[] nearby, UnitType type) {
        MapLocation me = rc.getLocation();
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : nearby) {
            if (info.getType() != type) continue;
            int d = info.getLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        return best;
    }

    static RobotInfo nearestEnemyRat(RobotController rc, RobotInfo[] nearby) {
        MapLocation me = rc.getLocation();
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : nearby) {
            if (info.getTeam() == rc.getTeam()) continue;
            if (info.getType() == UnitType.CAT) continue;
            int d = info.getLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        return best;
    }

    static int countAlliesNear(RobotController rc, RobotInfo[] nearby, MapLocation point, int radiusSquared) {
        int count = 1; // self
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam()) continue;
            if (info.getType() != UnitType.BABY_RAT) continue;
            if (info.getLocation().distanceSquaredTo(point) <= radiusSquared) count++;
        }
        return count;
    }

    static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        return moveToward(rc, target, false);
    }

    static boolean moveToward(RobotController rc, MapLocation target, boolean allowStuckEscape) throws GameActionException {
        return moveToward(rc, target, allowStuckEscape, false);
    }

    /**
     * Iteration 35 (TRAINING_LOG.md): Bug2-style navigation, replacing the
     * old "point at the target, sidestep 45 degrees if blocked" greedy
     * step. Iteration 33 established (four failed stuck-detection patches,
     * plus a direct `--terrain` read showing genuine maze corridors between
     * a stuck rat and its King) that greedy target-directed movement has no
     * notion of a *path*, only a heading, and no amount of detect-and-escape
     * patching fixes that -- it needs real obstacle navigation.
     *
     * Implements the pattern BC22's `RESEARCH.md` section 2 documents as the
     * cross-year convergent solution (every strong team starts with textbook
     * A-star or BFS, blows the bytecode budget, and ends up here):
     *   1. Move directly toward the target when that works.
     *   2. When blocked, follow the obstacle boundary, rotating consistently
     *      in one direction, until the direct path clears.
     *   3. Escape concave ("C"-shaped) obstacles -- plain bug-nav loops
     *      forever in these -- by only resuming direct movement once
     *      strictly closer to the target than at any point since wall-
     *      following began (the classic Bug2 improvement; `RESEARCH.md`
     *      cites Gone Fishin's directional-stack equivalent).
     *   4. Randomized tie-break as a last-resort safety valve (retained via
     *      `tryMove`'s existing stuck-escape shuffle).
     * Deliberately no dynamic collections or allocation per `RESEARCH.md`
     * section 10 (bytecode-aware structures over standard-library defaults)
     * -- just a few per-robot ints/enums and a bounded 8-step rotation scan.
     *
     * **Scoped to fixed targets only** (`useBugNav`). A first cut applied
     * this to every `moveToward()` caller and regressed the full Gauntlet
     * (65.0%, down from 75.0%), concentrated hard on `minimaze` (newly lost
     * all four pairings). Diagnosis: bug-navigation's whole premise is
     * making monotonic progress toward a *stationary* goal -- the
     * closest-distance-so-far memory is what escapes concave obstacles. A
     * moving goal (chasing an enemy rat in `engage()`, fleeing a cat, or
     * re-picking the nearest cheese tile as tiles deplete) invalidates that
     * memory every round, so the state thrashes: it resets on every target
     * change, never accumulates the history the escape logic depends on,
     * and meanwhile the wall-following scan replaces the responsive
     * 45-degree sidestep that combat actually wants. Only `deliverCheese()`
     * has a genuinely fixed target (the King) -- which is also exactly
     * where the confirmed 340-round stuck-cheese bug was traced.
     */
    static boolean moveToward(RobotController rc, MapLocation target, boolean allowStuckEscape,
                              boolean useBugNav) throws GameActionException {
        MapLocation here = rc.getLocation();
        if (here.equals(target)) return true;
        // Iteration 86 (TRAINING_LOG.md): Bug2 is VALIDATED, mildly.
        // Ablating it scores 24/54 = 44.4% against the version that has it,
        // so it is worth roughly +5.6 points -- real, but an order of
        // magnitude smaller than the exploration-heading reassignment's ~28.
        // It was originally accepted on a purely mechanistic argument with no
        // win-rate evidence; this is its first measurement.
        if (!useBugNav) {
            return tryMove(rc, here.directionTo(target), allowStuckEscape);
        }

        // Reset bug state whenever the goal changes -- distance progress is
        // only meaningful relative to a single fixed target.
        if (bugTarget == null || !bugTarget.equals(target)) {
            bugTarget = target;
            bugClosestDistSq = Integer.MAX_VALUE;
            bugRoundsFollowing = 0;
            // Per-robot, non-team-correlated rotation preference (see the
            // class-level symmetry note -- a fixed left-before-right order
            // is exactly the absolute-bias anti-pattern BC22's LEARNINGS.md
            // documents as its largest recurring bug class).
            bugRotateLeft = (rc.getID() % 2 == 0);
        }

        int distSq = here.distanceSquaredTo(target);
        Direction toTarget = here.directionTo(target);

        // Correct Bug2 structure: *always* attempt the direct move when not
        // currently committed to tracing a boundary, and use the
        // closest-distance memory only to decide when it's safe to *leave*
        // wall-following. A first cut had this backwards -- it gated the
        // direct-move attempt itself on "strictly closer than ever before,"
        // which meant a rat pushed backwards (delivery congestion near the
        // King, or the King itself relocating to flee a cat) could never
        // satisfy the condition again and would wall-follow forever. That
        // reintroduced the exact permanently-stuck failure class this whole
        // iteration exists to remove, and cost 15 points of win rate
        // (60.0%) before being caught.
        if (bugRoundsFollowing == 0) {
            if (tryMoveDirect(rc, toTarget)) {
                if (distSq < bugClosestDistSq) bugClosestDistSq = distSq;
                return true;
            }
            // Only commit to tracing a boundary if the thing blocking us is
            // actually *terrain*. Bug-navigation assumes static obstacles;
            // most blocks here are transient (another rat standing in the
            // way for a round, especially in delivery traffic near the
            // King), and committing to a multi-round boundary trace over a
            // blocker that would have cleared on its own is strictly worse
            // than the old one-step sidestep -- measured at 12.5 points of
            // win rate across two Gauntlets before this check was added.
            // `RESEARCH.md` section 2 names this exact distinction:
            // "treat friendly units as soft, not hard, obstacles."
            MapLocation ahead = here.add(toTarget);
            boolean terrainBlocked = false;
            if (rc.canSenseLocation(ahead)) {
                terrainBlocked = !rc.senseMapInfo(ahead).isPassable();
            }
            if (!terrainBlocked) {
                return tryMove(rc, toTarget, allowStuckEscape);
            }
            // Direct path is blocked by real terrain -- commit to tracing
            // this obstacle, remembering how close we were when we hit it.
            bugClosestDistSq = distSq;
        } else if (distSq < bugClosestDistSq && tryMoveDirect(rc, toTarget)) {
            // Made real progress past the obstacle: resume direct approach.
            bugClosestDistSq = distSq;
            bugRoundsFollowing = 0;
            return true;
        }

        // Follow the obstacle boundary. Scan a bounded 8 steps rotating
        // consistently from the target-relative heading, so the traced
        // boundary is continuous rather than jumping sides each round.
        bugRoundsFollowing++;
        Direction d = toTarget;
        for (int i = 0; i < 8; i++) {
            d = bugRotateLeft ? d.rotateLeft() : d.rotateRight();
            if (stepTo(rc, d)) return true;
        }

        // Fully enclosed, or wall-following has run long enough to suspect a
        // trap the boundary trace can't resolve (e.g. a pocket whose only
        // exit is back the way we came). Fall through to the existing
        // randomized escape, which `tryMove` already gates on confirmed
        // stuck-ness -- `RESEARCH.md`'s "randomized tie-break as a
        // last-resort safety valve against infinite loops."
        if (bugRoundsFollowing > 16) {
            bugClosestDistSq = Integer.MAX_VALUE; // let direct approach retry
            bugRoundsFollowing = 0;
            bugRotateLeft = !bugRotateLeft;       // try the other way around
        }
        return tryMove(rc, toTarget, allowStuckEscape);
    }

    /** Turn-and-move strictly along `want`, no sidestep. */
    static boolean tryMoveDirect(RobotController rc, Direction want) throws GameActionException {
        if (want == Direction.CENTER) return false;
        return stepTo(rc, want);
    }

    /**
     * Iteration 151 (TRAINING_LOG.md): step one tile in `d`, TURNING TO FACE IT
     * FIRST so the step costs `movementCooldown` 10 instead of
     * `MOVE_STRAFE_COOLDOWN` 18. Falls back to the raw strafe when we cannot
     * turn: a slow step beats none. Checks canMove BEFORE turning, so it never
     * burns the round's single turn on a direction it will not take.
     */
    static boolean stepTo(RobotController rc, Direction d) throws GameActionException {
        if (!rc.canMove(d)) return false;
        if (rc.getDirection() != d && rc.canTurn(d)) {
            rc.turn(d);
        }
        if (rc.getDirection() == d && rc.canMoveForward()) {
            rc.moveForward();
            return true;
        }
        rc.move(d);
        return true;
    }

    /**
     * Turn-and/or-move toward `want`, using only directions derived from
     * `want` itself (never a fixed compass fallback -- see the class-level
     * symmetry note). If blocked, sidesteps via `want`'s own rotateLeft/
     * rotateRight, tie-broken by `rc.getID() % 2` (per-robot, not
     * team-correlated) rather than a fixed left-before-right order.
     */
    static boolean tryMove(RobotController rc, Direction want) throws GameActionException {
        return tryMove(rc, want, false);
    }

    /**
     * Iteration 24 attempt (TRAINING_LOG.md): `allowStuckEscape` scopes the
     * random-direction stuck-breaker (below) to "economic" travel only
     * (deliverCheese/collectCheese/explore/backstab-hunt-chase) -- the
     * first full-Gauntlet run applied it everywhere and traced a new,
     * concentrated regression against `immediate_defector` (`knifefight`
     * population collapsed 13->2 in 75 rounds, much faster than baseline):
     * legitimate back-and-forth movement during active combat can trip the
     * same 2-tile-repeat detector as a genuine terrain trap, and injecting
     * a random direction mid-fight is actively harmful there. `engage()`/
     * `flee()` keep the old deterministic tiebreak unconditionally.
     */
    static boolean tryMove(RobotController rc, Direction want, boolean allowStuckEscape) throws GameActionException {
        if (want == Direction.CENTER) return false;
        if (stepTo(rc, want)) return true;
        if (allowStuckEscape && stuckCycles >= 2) {
            // Same class of problem the engine's own cat AI hits and fixes
            // the same way (InternalRobot.java: EXPLORE mode randomizes
            // facing after catTurnsStuck >= 4) -- break the tie randomly
            // across all 8 directions instead of retrying the same blocked
            // pair. Manual Fisher-Yates on a plain array (not
            // java.util.Collections.shuffle) to stay clear of any
            // AllowedPackages risk.
            Direction[] shuffled = ALL_DIRECTIONS.clone();
            for (int i = shuffled.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                Direction tmp = shuffled[i];
                shuffled[i] = shuffled[j];
                shuffled[j] = tmp;
            }
            for (Direction d : shuffled) {
                if (stepTo(rc, d)) return true;
            }
            return false;
        }
        Direction left = want.rotateLeft();
        Direction right = want.rotateRight();
        Direction first = (rc.getID() % 2 == 0) ? left : right;
        Direction second = (first == left) ? right : left;
        for (Direction d : new Direction[]{first, second}) {
            if (stepTo(rc, d)) return true;
        }
        return false;
    }

    static final Direction[] ALL_DIRECTIONS = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    static Direction preferredExploreDir;

    /**
     * Replay evidence (TRAINING_LOG.md, `knifefight`): 40 Baby Rats went a
     * full 200+ rounds without finding a single unit of cheese or ever
     * meeting a cat. The old version of this method always continued
     * straight ahead and only picked a *fresh random* direction on being
     * blocked -- with every rat starting from the same spawn facing the
     * same default direction, the whole population walks the map as one
     * front instead of fanning out, so entire regions can go permanently
     * unexplored. Fix: each robot commits once, from `rc.getID()` (not
     * team-correlated), to a personal preferred heading and returns to it
     * whenever unblocked, instead of re-randomizing every time it's
     * deflected -- population fans out in `ALL_DIRECTIONS.length` different
     * directions from turn 1 rather than however many directions emerge by
     * chance from wall collisions.
     *
     * Iteration 32 (TRAINING_LOG.md): "commits once... and returns to it
     * whenever unblocked" turns out to be a serious problem the original
     * fix never accounted for -- map boundaries. Traced a rat
     * (`tools/replay-dump.sh --robot`, `rift`) that spawned near a map
     * corner with a preferred heading pointing straight at it: it reached
     * the corner in ~15 rounds, then spent the remaining ~1985 rounds of a
     * 2000-round game oscillating within a handful of tiles, never once
     * picking up cheese. The stuck-cycle escape (see `tryMove()`) can move
     * it one tile away for a turn, but `explore()` unconditionally retries
     * the *same* fixed heading next round -- which immediately re-hits the
     * same boundary, recreating the trap. A one-off escape move can't fix
     * a heading that's permanently wrong for this robot's spawn position;
     * only replacing the heading does. Also fixed a related latent bug
     * while here: `Direction.allDirections()` returns 9 values including
     * `CENTER` (`tryMove` treats `CENTER` as an immediate no-op), so 1 in 9
     * robots by pure `rc.getID()` arithmetic got a preferred heading that
     * never did anything at all. Switched both the initial assignment and
     * the reassignment below to `ALL_DIRECTIONS` (8 real headings only).
     *
     * First attempt at the reassignment trigger reused the shared, global
     * `stuckCycles` counter (tracked once per round in `runBabyRat()`,
     * also driving `tryMove()`'s escape for `deliverCheese()`/
     * `collectCheese()`) and it broadly regressed the full Gauntlet
     * (23/40, down from 70.0%) despite fixing the motivating game cleanly.
     * Root cause: that counter fires on *any* 2-tile repeat regardless of
     * cause -- a rat briefly jammed delivering cheese in a crowded spot
     * near the King (a real, common, and totally benign occurrence) would
     * get its perfectly-fine exploration heading needlessly reassigned
     * the next time it happened to call `explore()`, undermining exactly
     * the population fan-out Iteration 4 relied on. Fixed by tracking a
     * *dedicated* explore-call-to-explore-call position history here,
     * completely separate from the shared per-round one -- only repeated,
     * consecutive *exploration* stalls trigger a reassignment now.
     */
    static void explore(RobotController rc) throws GameActionException {
        if (preferredExploreDir == null) {
            preferredExploreDir = ALL_DIRECTIONS[Math.floorMod(rc.getID(), ALL_DIRECTIONS.length)];
        }
        MapLocation here = rc.getLocation();
        if (here.equals(exploreLocTwoCallsAgo)) {
            exploreStuckCycles++;
        } else {
            exploreStuckCycles = 0;
        }
        exploreLocTwoCallsAgo = exploreLocOneCallAgo;
        exploreLocOneCallAgo = here;
        // Iteration 85 (TRAINING_LOG.md): this reassignment is VALIDATED and
        // is the single most valuable behaviour measured in the whole bot.
        //
        // Ablating it and playing the version that has it scores **12/54 =
        // 22.2%** on the mirror -- a ~28-point swing, the largest effect
        // measured on that instrument in either direction, dwarfing the King
        // trap ring (+7.4% to remove) and the cheese-boosted bite (~4%).
        //
        // Do not "simplify" this away. Without it a rat whose initial heading
        // points at a nearby map edge reaches that edge in ~15 rounds and then
        // oscillates in a handful of tiles for the rest of the game, never
        // collecting cheese -- the failure the user reported directly, and
        // the one traced in the Iteration 32 entry.
        if (exploreStuckCycles >= 2) {
            Direction newDir;
            do {
                newDir = ALL_DIRECTIONS[rng.nextInt(ALL_DIRECTIONS.length)];
            } while (newDir == preferredExploreDir);
            preferredExploreDir = newDir;
            exploreStuckCycles = 0;
        }
        if (rc.getDirection() == preferredExploreDir && rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        if (tryMove(rc, preferredExploreDir, true)) return;
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        if (tryMove(rc, ALL_DIRECTIONS[rng.nextInt(ALL_DIRECTIONS.length)], true)) return;

        // Iteration 4 (TRAINING_LOG.md): a Baby Rat with every direction
        // blocked used to just do nothing this turn -- confirmed via the
        // replay tool's --terrain flag that at least one real dead end
        // (`knifefight`) is dirt, not a permanent wall, and only the King
        // could dig (Iteration 2). Generalize King's digTowardOpenSpace()
        // to any unit type that's genuinely stuck.
        digTowardOpenSpace(rc);
    }

    /**
     * Compares the round number before/after this robot's own turn logic to
     * detect a confirmed bytecode overrun (the engine pauses mid-instruction
     * and resumes next round with no exception -- see TRAINING_ALGORITHM.md).
     * Also compares live bytecode usage against this unit type's limit to
     * catch near-misses before they become overruns. Surfaced via the
     * robot's own indicator string so it's visible in every replay.
     */
    static void reportBytecodeBudget(RobotController rc, int roundAtTurnStart) {
        int used = Clock.getBytecodeNum();
        int limit = rc.getType().getBytecodeLimit();
        boolean overran = rc.getRoundNum() != roundAtTurnStart;
        boolean nearMiss = used > (int) (limit * 0.9);
        String status = overran ? "OVERRAN" : (nearMiss ? "near-limit" : "ok");
        if (rc.getType() != UnitType.RAT_KING) { // King's indicator already set above
            rc.setIndicatorString("bytecode " + used + "/" + limit + " (" + status + ")");
        }
    }
}

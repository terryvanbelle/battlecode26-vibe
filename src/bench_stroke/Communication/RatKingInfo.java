package bench_stroke.Communication;

import battlecode.common.MapLocation;

/**
 * Represents a RatKing in the shared array.
 *
 * @param loc     location of the Rat King
 * @param health  Rat King's current health
 * @param turnMod turn number this Rat King was entered in shared array, modded by 2^8
 */

public record RatKingInfo(MapLocation loc, int health, int turnMod, boolean inDistress) {
}

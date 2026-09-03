package bench_stroke.Communication;

import battlecode.common.MapLocation;
import battlecode.common.*;

public record PresenceSqueakInfo(int health, MapLocation nearestEnemy, Direction enemyDir, int enemyHealth) implements SqueakInfo{
    @Override
    public String toString() {
        return "PresenceSqueakInfo{" +
                "health = " + health +
                "nearestEnemy = " + nearestEnemy +
                "enemyDir = " + enemyDir +
                "enemyHealth = " + enemyHealth + 
                '}';
    }
}

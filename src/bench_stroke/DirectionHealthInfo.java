package bench_stroke;
import battlecode.common.*;

public class DirectionHealthInfo {
    public Direction dir;
    public int health;
    public boolean ratKing;

    public DirectionHealthInfo(Direction direction, int enemyHealth, boolean isRatKing) {
        dir = direction;
        health = enemyHealth;
        ratKing = isRatKing;
    }
}

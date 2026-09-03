package bench_spaark;

import battlecode.common.*;

public class MicroActionScore {
    double score;
    MapLocation target;
    Direction dir;
    int type;

    public static final int ATTACK = 0;
    public static final int TRAP = 1;
    public static final int RATNAP = 2;
    public static final int DO_NOTHING = 3;
    public static final int CAT_TRAP = 4;
	public static final int ATTACK_1 = 5; // 1 cheese
	public static final int ATTACK_2 = 6; // 2 cheese
	public static final int ATTACK_3 = 7; // 5 cheese
	public static final int ATTACK_4 = 8; // 10 cheese
	public static final int ATTACK_5 = 9; // 17 cheese
	public static final int ATTACK_6 = 10; // 26 cheese
	public static final int ATTACK_7 = 11; // 37 cheese
	public static final int ATTACK_8 = 12; // 50 cheese
	public static final int ATTACK_9 = 13; // 65 cheese


    public MicroActionScore(double score, MapLocation target, Direction dir, int type) {
        this.score = score;
        this.target = target;
        this.dir = dir;
        this.type = type;
    }

    public void compare(MicroActionScore s) {
        if (s.score > this.score) {
            this.score = s.score;
            this.target = s.target;
            this.dir = s.dir;
            this.type = s.type;
        }
    }
}

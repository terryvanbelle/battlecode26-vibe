package bench_spaark;

public class Sustain {
    static double[] rolling;
    static double total;
    public static double average;
    static final double INIT_AVG = 30.0;
    static final int LEN = 100;
    public static void init() {
        rolling = new double[LEN];
        for (int i = LEN - 1; --i>=0;) {
            rolling[i] = INIT_AVG;
        } 
        average = INIT_AVG;
        total = INIT_AVG * 50.0;
    }
    public static void update(int val) {
        total -= rolling[G.round % LEN];
        rolling[G.round % LEN] = val;
        total += val;
        average = total / 50.0;
    }
}

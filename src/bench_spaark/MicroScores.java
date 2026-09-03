package bench_spaark;

public class MicroScores {
    double[] moveScores;
    double[] turnScores;
    MicroActionScore[] actionScores;

    public MicroScores(double[] moveScores, double[] turnScores, MicroActionScore[] actionScores) {
        this.moveScores = moveScores;
        this.turnScores = turnScores;
        this.actionScores = actionScores;
    }
}

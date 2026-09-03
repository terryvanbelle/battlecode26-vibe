package bench_stroke.Communication;

import static bench_stroke.SymmetryManager.*;

public record SymmetrySqueakInfo(int symmetry) implements SqueakInfo{

    @Override
    public String toString() {
        return "SymmetrySqueakInfo{" +
                "symmetry=" + symmetry +
                '}';
    }
}

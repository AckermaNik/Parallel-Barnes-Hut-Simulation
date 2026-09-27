public class MassCenter {
    double mass;
    double wx;
    double wy;

    public MassCenter() {
        this.mass = 0;
        this.wx = 0;
        this.wy = 0;
    }

    public MassCenter(double mass, double x, double y) {
        this.mass = mass;
        this.wx = mass * x;
        this.wy = mass * y;
    }

    public MassCenter add(MassCenter other) {
        MassCenter result = new MassCenter();
        result.mass = this.mass + other.mass;
        result.wx = this.wx + other.wx;
        result.wy = this.wy + other.wy;
        return result;
    }
}

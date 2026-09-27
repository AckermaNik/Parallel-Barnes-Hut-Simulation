


public class Celestial_Body {

    final double G = 6.67e-11;
    final double Dt = 1.0;

    private
    double x, y, vx, vy, m, fx, fy;
    int id;
    String name;


    public Celestial_Body(double x, double y, double vx, double vy, double fx, double fy, double mass, String name , int id) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.fx = fx;
        this.fy = fy;
        this.m = mass;
        this.name = name;
        this.id=id;
    }

    Celestial_Body() {
        this.x = 0;
        this.y = 0;
        this.vx = 0;
        this.vy = 0;
        this.fx = 0;
        this.fy = 0;
        this.m = 0;
        this.name = "";
    }

    void reset_force() {
        this.fx = this.fy = 0;
    }


    void add_force(double cx, double cy, double other_mass) {
        double dx = cx - x;
        double dy = cy - y;

        double r =  StrictMath.sqrt(dx * dx + dy * dy);
        if (r < 1e-5) r = 1e-5; // to avoid /0

        double F = (G * m * other_mass) / (r * r);
        fx += F * (dx / r);
        fy += F * (dy / r);
    }

    void new_position() {
        double ax = fx / m;
        double ay = fy / m;

        vx += ax * Dt;
        vy += ay * Dt;

        x += vx * Dt;
        y += vy * Dt;

    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getVx() {
        return vx;
    }

    public void setVx(double vx) {
        this.vx = vx;
    }

    public double getVy() {
        return vy;
    }

    public void setVy(double vy) {
        this.vy = vy;
    }

    public double getM() {
        return m;
    }

    public void setM(double m) {
        this.m = m;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

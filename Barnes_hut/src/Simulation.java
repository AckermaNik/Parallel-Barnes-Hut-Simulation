import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class Simulation {

    private static final double LOG2 = Math.log(2);

    public static double log2(int x) {
        return Math.log(x) / LOG2;
    }
    public static void applyForce(Celestial_Body body, final Node node) {
        if (node == null || node.getTotalMass() == 0) return;

        if (node.isLeaf()) {
            Celestial_Body other = node.getPlanet();
            if (other != null && other.getId() != body.getId()) {
                body.add_force(other.getX(), other.getY(), other.getM());
            }
        } else {
            double dx = node.getCwx() - body.getX();
            double dy = node.getCwy() - body.getY();
            double r = StrictMath.sqrt(dx * dx + dy * dy);

            if (node.getQuad().getLen() < r && !node.getQuad().contains(body.getX(), body.getY())) {
                body.add_force(node.getCwx(), node.getCwy(), node.getTotalMass());
            } else {
                applyForce(body, node.getUL());
                applyForce(body, node.getUR());
                applyForce(body, node.getDL());
                applyForce(body, node.getDR());
            }
        }
    }

    public static void simulation(ArrayList<Celestial_Body> planets, double[] L, int steps, int numThreads) {

        double R = L[0];
        double MAX_DEPTH= (int) log2(numThreads)-1;
        int batchSize =  Math.max(1,(int) Math.ceil((double) planets.size() / numThreads));

        CustomThreadPool pool = new CustomThreadPool(numThreads);
        try {
            for (int s = 0; s < steps; s++) {

                Quad rootQuad = new Quad(0, 0, 2 * R);
                Node root = new Node(rootQuad);

                // Insert planets into the root Node
                for (Celestial_Body pl : planets) {
                    root.insert(pl);
                }

                MassCenter result = root.computeMassCenter(pool, 0, MAX_DEPTH);

                CountDownLatch latch = new CountDownLatch(planets.size());

                for (int i = 0; i < planets.size(); i += batchSize) {
                    int start = i;
                    int end = Math.min(i + batchSize, planets.size());

                    pool.submit(() -> {
                        for (int j = start; j < end; j++) {

                            try {

                                Celestial_Body body = planets.get(j);
                                body.reset_force();
                                applyForce(body, root);
                                body.new_position();

                            } catch (Exception e) {
                                System.err.println("Exception in body loop: " + e);
                            } finally {
                                latch.countDown();
                            }
                        }
                    });
                }

                try {
                    latch.await(); // Wait for the updates to each planet to finish before next step
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }finally {
            pool.shutdown();
        }

    }
}

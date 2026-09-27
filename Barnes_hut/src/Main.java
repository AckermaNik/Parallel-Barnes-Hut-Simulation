import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;

public class Main {
    public static void main(String[] args ) {
        ArrayList<Celestial_Body> Planets = new ArrayList<>();

        if(args.length<3){
            System.out.println("Usage: java barnes <input.txt> <steps> <num_threads> ");
            return;
        }

        String input= args[0];
        int steps =  Integer.parseInt(args[1]);
        int threads = Integer.parseInt(args[2]);

        double[] R = new double[1];

        readInput(input, R, Planets);

        System.out.println("------------------Starting Simulation------------------------");

        Simulation.simulation(Planets, R, steps, threads);

        writeOutput("output.txt", Planets, R[0]);

    }

        public static void readInput(String filename, double[] R, ArrayList<Celestial_Body> planets) {
            try (Scanner scanner = new Scanner(new File(filename))) {
                int numberOfPlanets = scanner.nextInt();
                R[0] = scanner.nextDouble(); // pass in a double[] to retrieve R as if it is by reference

                for (int i = 0; i < numberOfPlanets; i++) {
                    double x = scanner.nextDouble() ;
                    double y = scanner.nextDouble();
                    double vx = scanner.nextDouble();
                    double vy = scanner.nextDouble();
                    double m = scanner.nextDouble();
                    String name = scanner.next();

                    Celestial_Body body = new Celestial_Body(x,y,vx,vy,0,0,m,name,i);

                    planets.add(body);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static void writeOutput(String filename, ArrayList<Celestial_Body> planets, double R) {
            try (PrintWriter out = new PrintWriter(filename)) {
                out.println(planets.size());
                out.println(R);

                for (Celestial_Body b : planets) {
                    out.printf(Locale.US, "%.6e %.6e %.6e %.6e %.6e %s%n",
                            b.getX(), b.getY(), b.getVx(), b.getVy(), b.getM(), b.getName());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

}


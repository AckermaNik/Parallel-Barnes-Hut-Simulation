import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Node {

    private

        Quad quad;
        Celestial_Body planet;
        boolean is_leaf;
        double total_mass = 0;
        double cwx = 0, cwy = 0; //CENTER WEIGHTED COORDINATES OF MASS (mass*x),(mass*y)

        Node UL= null;    //THE CHILDREN
        Node UR= null;
        Node DL= null;
        Node DR= null;


        public Node(){
            this.total_mass=0;
            this.planet=null;
            this.quad=null;
            this.cwx=0;
            this.cwy=0;
        }

        public Node( final Quad q){
            this.planet=null;
            this.quad=q;
            this.is_leaf=true;
            this.total_mass=0;
            this.cwx=0;
            this.cwy=0;
        }

    public Quad getQuad() {
        return quad;
    }

    public void setQuad(Quad quad) {
        this.quad = quad;
    }

    public Celestial_Body getPlanet() {
        return planet;
    }

    public void setPlanet(Celestial_Body planet) {
        this.planet = planet;
    }

    public boolean isLeaf() {
        return is_leaf;
    }

    public void setLeaf(boolean isLeaf) {
        this.is_leaf = isLeaf;
    }

    public double getTotalMass() {
        return total_mass;
    }

    public void setTotalMass(double totalMass) {
        this.total_mass = totalMass;
    }

    public double getCwx() {
        return cwx;
    }

    public void setCwx(double cwx) {
        this.cwx = cwx;
    }

    public double getCwy() {
        return cwy;
    }

    public void setCwy(double cwy) {
        this.cwy = cwy;
    }

    public Node getUL() {
        return UL;
    }

    public void setUL(Node UL) {
        this.UL = UL;
    }

    public Node getUR() {
        return UR;
    }

    public void setUR(Node UR) {
        this.UR = UR;
    }

    public Node getDL() {
        return DL;
    }

    public void setDL(Node DL) {
        this.DL = DL;
    }

    public Node getDR() {
        return DR;
    }

    public void setDR(Node DR) {
        this.DR = DR;
    }


    private void createChildren() {
        UL = new Node(quad.UL());
        UR = new Node(quad.UR());
        DL = new Node(quad.DL());
        DR = new Node(quad.DR());
    }

    private void insertToChild(Celestial_Body b) {
        if (quad.UL().contains(b.getX(), b.getY())) {
            UL.insert(b);
        } else if (quad.UR().contains(b.getX(), b.getY())) {
            UR.insert(b);
        } else if (quad.DL().contains(b.getX(), b.getY())) {
            DL.insert(b);
        } else if (quad.DR().contains(b.getX(), b.getY())) {
            DR.insert(b);
        }
    }

    public void insert(Celestial_Body b) {

        if (planet != null && planet.getId() == b.getId()) {
            System.err.println("Already inserted element");
            return;
        }

        if (planet == null && is_leaf) {
            planet = b;
            total_mass = planet.getM();
            cwx = planet.getX();
            cwy = planet.getY();
        } else {
            if (is_leaf) {
                createChildren();
                insertToChild(planet);
                planet =null; // reset
                total_mass = 0;
                cwx = 0;
                cwy = 0;
                is_leaf = false;
            }
            insertToChild(b);
        }
    }

    private MassCenter computeSequentially() {

        MassCenter result = new MassCenter();
        // Base case: If this is a leaf node, return its mass and center
        if (is_leaf) {
            result.mass = total_mass;
            result.wx = cwx;  // cwx is already center-of-mass x
            result.wy = cwy ;  // cwy is already center-of-mass y
            return result;
        }

        MassCenter[] results = new MassCenter[4]; // for the 4 kids

        // Recursively compute mass centers of all children (UL, UR, DL, DR)
       if(UL!=null){results[0]=UL.computeSequentially();}
       if(UR!=null){results[1]=UR.computeSequentially();}
       if(DL!=null){results[2]=DL.computeSequentially();}
       if(DR!=null){results[3]=DR.computeSequentially();}

        // Add results
        for (MassCenter mc : results) {
            result = result.add(mc);
        }

        // Update this node's center of mass
        total_mass = result.mass;
        if (total_mass> 0) {
            cwx = result.wx / total_mass;  // Center x = (Σ mass*x) / total_mass
            cwy = result.wy / total_mass;  // Center y = (Σ mass*y) / total_mass
        } else {
            cwx = 0;
            cwy = 0;
        }

        return result;
    }

    public MassCenter computeMassCenter(CustomThreadPool pool,int depth, double MAX_DEPTH ){

            //if leaf or sequentially execution to avoid deadlock due to restricted number or threads
        if (is_leaf || depth >= MAX_DEPTH) {
            return this.computeSequentially();
        }


        MassCenter[] results = new MassCenter[4]; // for the 4 kids
        CountDownLatch latch = new CountDownLatch(4);

        // Submit tasks to pool
        pool.submit(() -> {
            try {
                results[0] = (UL != null) ? UL.computeMassCenter(pool,depth + 1,MAX_DEPTH) : new MassCenter();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
            //System.out.println(latch.getCount());
        });

        pool.submit(() -> {
            try {
                results[1] = (UR != null) ? UR.computeMassCenter(pool,depth + 1,MAX_DEPTH) : new MassCenter();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        pool.submit(() -> {
            try {
                results[2] = (DL != null) ? DL.computeMassCenter(pool,depth + 1,MAX_DEPTH) : new MassCenter();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
           // System.out.println(latch.getCount());
        });

        pool.submit(() -> {
            try {
                results[3] = (DR != null) ? DR.computeMassCenter(pool,depth + 1,MAX_DEPTH) : new MassCenter();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
            //System.out.println(latch.getCount());
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // add results
        MassCenter finalResult = new MassCenter();
        for (MassCenter mc : results) {
            finalResult = finalResult.add(mc);
        }

        total_mass = finalResult.mass;
        if (total_mass > 0) {
            cwx = finalResult.wx / total_mass;
            cwy = finalResult.wy / total_mass;
        } else {
            cwx = 0;
            cwy = 0;
        }

        return finalResult;
    }
}

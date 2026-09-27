public class Quad {

    private
    double xmid, ymid, len;

    public
        Quad(double xmid, double ymid , double len){
            this.xmid=xmid;
            this.ymid=ymid;
            this.len=len;
        }

        Quad(){
            this.xmid=0;
            this.ymid=0;
            this.len=0;
        }

        boolean contains(double x, double y) {
            return ((x >= xmid - len/2.0 && x <= xmid + len/2.0) &&
                    (y >= ymid - len/2.0 && y <= ymid + len/2.0));
        }

        double getLen(){
            return len;
        }

        public Quad UL() {
            return new Quad(xmid - len / 4.0, ymid + len / 4.0, len / 2.0);
        }

        public Quad UR() {
            return new Quad(xmid + len / 4.0, ymid + len / 4.0, len / 2.0);
        }

        public Quad DL() {
            return new Quad(xmid - len / 4.0, ymid - len / 4.0, len / 2.0);
        }

        public Quad DR() {
            return new Quad(xmid + len / 4.0, ymid - len / 4.0, len / 2.0);
        }
}

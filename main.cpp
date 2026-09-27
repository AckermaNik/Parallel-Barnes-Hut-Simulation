#include <iostream>
#include <fstream>
#include <vector>
#include <cmath>
#include <string>
#include <memory>
#include <tbb/parallel_for.h>
#include <tbb/parallel_invoke.h>
#include <tbb/global_control.h>
#include <tbb/blocked_range.h>
#include <mutex>
#include <iomanip>

const double G = 6.67e-11;
const double Dt = 1;

using namespace std;
using namespace tbb;

std::mutex my_mutex,ye_mutex;

struct Celestial_Body {
    double x, y,vx, vy, m, fx , fy;
    int id;
    string name;

    Celestial_Body() : x(0), y(0), vx(0), vy(0), fx(0), fy(0), m(0), name("") {}
    Celestial_Body(double x,double y, double vx , double vy, double fx , double fy,double mass,string name): x(x),y(y) , vx(vx) ,vy(vy) , fx(fx), fy(fy), m(mass) , name(name) {}

    void reset_force() {
        fx = fy = 0 ;
    }

    void add_force(double cx, double cy, double other_mass) {
        double dx = cx - x;
        double dy = cy - y;

        double r = sqrt(dx * dx + dy * dy);
        if (r < 1e-5) r = 1e-5; // to avoid /0

        double F = (G * m * other_mass) / (r * r);
        fx += F * (dx / r);
        fy += F * (dy / r);
    }

    void new_position() {
        double ax = fx / m;
        double ay = fy / m;
     
        vx += ax*Dt;
        vy += ay*Dt;

        x += vx*Dt;
        y += vy*Dt;
    }
};

struct MassCenter {
    double mass = 0;  // Total mass
    double wx = 0;    // Weighted sum of x coordinates (mass * x)
    double wy = 0;    // Weighted sum of y coordinates (mass * y)

    MassCenter() = default;
    MassCenter(double m, double x, double y) : mass(m), wx(m * x), wy(m * y) {}

    // Combine two MassCenters -operator overload
    MassCenter operator + (const MassCenter& other) const {
        MassCenter result;
        result.mass = mass + other.mass;
        result.wx = wx + other.wx;
        result.wy = wy+ other.wy;
        return result;
    }

};

struct Quad {
    double xmid, ymid, len;

    Quad() : xmid(0), ymid(0), len(0) {}
    Quad(double x, double y, double l) : xmid(x), ymid(y), len(l) {}

    bool contains(double x, double y) const {
        return ((x >= xmid - len/2.0 && x <= xmid + len/2.0) &&
               (y >= ymid - len/2.0 && y <= ymid + len/2.0));
    }

    double get_len(){
        return len;
    }

    Quad UL() const { return {xmid - len/4.0, ymid + len/4.0, len/2.0}; }//UP LEFT
    Quad UR() const { return {xmid + len/4.0, ymid + len/4.0,len/2.0}; } //UP RIGHT
    Quad DL() const { return {xmid - len/4.0, ymid - len/4.0, len/2.0}; } //DOWN LEFT
    Quad DR() const { return {xmid + len/4.0, ymid - len/4.0, len/2.0}; } //DOWN RIGHT
};

struct Node {
    Quad quad;
    Celestial_Body planet={};
    bool is_leaf;
    double total_mass = 0;
    double cwx = 0, cwy = 0; //CENTER WEIGHTED COORDINATES OF MASS (mass*x),(mass*y)

    unique_ptr<Node> UL, UR, DL, DR; //THE CHILDREN
 
    Node()=default;
    Node(const Quad& q) : quad(q) ,is_leaf(true), total_mass(0), cwx(0), cwy(0){}
  
    private:
    void create_children() {    
        UL = make_unique<Node>(quad.UL());
        UR = make_unique<Node>(quad.UR());
        DL = make_unique<Node>(quad.DL());
        DR = make_unique<Node>(quad.DR());
    }

    void insert_to_child(const Celestial_Body& b) {

        if (quad.UL().contains(b.x, b.y)) {
            UL->insert(b);
        } else if (quad.UR().contains(b.x, b.y)) {
            UR->insert(b);
        } else if (quad.DL().contains(b.x, b.y)) {
            DL->insert(b);
        } else if (quad.DR().contains(b.x, b.y)) {
            DR->insert(b);
        }
    }

    public:
    void insert( const Celestial_Body& b) {
       
         if( planet.m!=0 && planet.id==b.id){
            cerr<<"Already inserted element"<<endl;
            return;
        }
        if ( planet.m==0 && is_leaf) {
            planet = b;
            total_mass=planet.m;
            cwx=planet.x;
            cwy=planet.y;
        
        }else {
            if (is_leaf) {
                create_children();
                insert_to_child(planet);
                planet={};
                total_mass=0;
                cwx=0;
                cwy=0;
                is_leaf = false;
            }

            insert_to_child(b);
        }

    }


    // Parallel recursive mass and center computation
    MassCenter compute_mass_center() {
        MassCenter result;

        //Leaf -only consider its values if it has a planet otherwise 0
        if(is_leaf){
            result.mass=total_mass;
            result.wx= cwx;
            result.wy= cwy; 
        }else{     
            //Internal node 
            MassCenter ul_result, ur_result, dl_result, dr_result;             
            tbb::parallel_invoke(
                [&]() { if (UL) ul_result =  UL->compute_mass_center(); },
                [&]() { if (UR) ur_result =  UR->compute_mass_center(); },
                [&]() { if (DL) dl_result =  DL->compute_mass_center(); },
                [&]() { if (DR) dr_result =  DR->compute_mass_center(); }
            );

            result = ul_result + ur_result + dl_result + dr_result;

            // Store the combined mass and center of mass in the internal node
            total_mass = result.mass;         
            if (total_mass > 0) {
                cwx = result.wx / total_mass;
                cwy = result.wy / total_mass;
            }else{
                cwx=0;
                cwy=0;
            }
        }

        return result;
    }

};

void apply_force(Celestial_Body& body, const Node* node) {

    if (!node || node->total_mass == 0) return;
    
   //if a node is leaf calculate the force between our body and the node's body
    if (node->is_leaf) {
        if (node->planet.m!= 0 && node->planet.id != body.id ) {
            body.add_force(node->planet.x,node->planet.y,node->total_mass);
        }
    } else {
        //Internal node
        double dx = node->cwx - body.x;
        double dy = node->cwy - body.y;
        double r = sqrt(dx * dx + dy * dy);

        if (node->quad.len< r && !node->quad.contains(body.x ,body.y)) {
            body.add_force(node->cwx, node->cwy, node->total_mass); // συνολική δύναμη από κόμβο
        } else{
            apply_force(body, node->UL.get());
            apply_force(body, node->UR.get());
            apply_force(body, node->DL.get());
            apply_force(body, node->DR.get());
        }
    }
}

void simulation (vector<Celestial_Body>& planets, double R, int steps){

    for(int i=0; i<steps ;i++){

        Quad root_quad(0, 0, 2*R);

        Node root(root_quad);

        for (Celestial_Body& pl : planets) {
            root.insert(pl);
        }

        (root).compute_mass_center();
      
        tbb::parallel_for(blocked_range<size_t>(0, planets.size()), [&](const blocked_range<size_t>& r) {
            for (size_t i = r.begin(); i != r.end(); ++i) {
                Celestial_Body& body = planets[i];
                body.reset_force();             
                apply_force(body, &root);       
                body.new_position(); //planet's new position                
            }
        });
    }
}



vector<Celestial_Body> read_input(string name, double& R) {
    ifstream in(name);

    int number_of_planets;
    in >> number_of_planets >> R;

    vector<Celestial_Body> planets(number_of_planets);
    for (int i = 0; i < number_of_planets; ++i) {
        in >> planets[i].x >> planets[i].y >> planets[i].vx >> planets[i].vy >> planets[i].m >> planets[i].name;
        planets[i].id=i;
    }
    return planets;
}

void write_output(const string filename, const vector<Celestial_Body>& planets, double R) {
    ofstream out(filename);

    out << planets.size() << "\n" << R << "\n";
    for (auto b : planets) {
        out << b.x << " " << b.y << " "
            << b.vx << " " << b.vy << " "
            << b.m << " " << b.name << "\n";
    }
}

int main(int argc, char* argv[]) {
    vector<Celestial_Body> planets;

    if (argc < 4) {
        cerr << "Usage: ./barnes_hut <<input.txt>> <<steps>> <<num_of_threads>>\n";
        return EXIT_FAILURE;
    }

    double R;
    int steps = stoi(argv[2]);
    int num_threads = stoi(argv[3]);;

    global_control gl(global_control::max_allowed_parallelism, num_threads);
    planets = read_input(argv[1], R);
    
    cout<<"------------------------Starting Simulation-----------------------------------------------"<<endl;
    simulation (planets, R, steps);

    write_output("output.txt", planets, R);
    return EXIT_SUCCESS;
}























# Parallel-Barnes-Hut-NBody-Simulation

Two parallel implementations of a two-dimensional gravitational N-body simulation using the Barnes–Hut algorithm: a C++ version built with oneTBB and a Java version built around a custom thread pool. Both versions construct a quadtree over celestial bodies, approximate distant groups of bodies by aggregate nodes, compute gravitational forces, and advance the simulation over a fixed number of time steps.

## Project goals

Directly calculating every body's gravitational interaction with every other body requires approximately quadratic work per simulation step. Barnes–Hut reduces the amount of work by organizing bodies spatially in a quadtree. When a group of bodies is sufficiently far from the body being updated, the group can be treated as one aggregate source located at its center of mass. Nearby regions are opened recursively so their bodies can be considered individually.

This project explores both the algorithm and two shared-memory parallelization strategies. The C++ implementation delegates tasks to oneTBB. The Java implementation manages worker threads and task completion explicitly.

## Simulation model

### 1. Input bodies and simulation region

Each input begins with a body count and a region radius `R`. The simulation creates a square root region centered at `(0, 0)` with side length `2R`. Each body has a position, velocity, mass, and name. The C++ implementation assigns an integer ID while reading; Java does the same so a body's own node can be excluded during force calculation.

### 2. Quadtree construction

At the start of every time step, the program creates a fresh root cell and inserts the current body positions. A `Quad` describes a square by its center and side length. It can test whether a point lies within its bounds and create four half-size child quadrants:

- `UL`: upper-left
- `UR`: upper-right
- `DL`: lower-left
- `DR`: lower-right

Each `Node` represents one spatial cell. An empty leaf can hold a body; when another body is inserted into an occupied leaf, the leaf is subdivided and its body and the new body are routed into children. Internal nodes own up to four child nodes. The C++ version uses `std::unique_ptr` for child ownership; Java stores child references directly.

The tree is rebuilt after bodies move, so each step's spatial partition reflects the latest positions. The input domain is expected to contain all bodies; points outside the root square are not explicitly handled by the insertion logic.

### 3. Aggregate mass and center information

The code uses a `MassCenter` value to combine aggregate mass and coordinate sums from child quadrants. Internal nodes store the total mass and a representative center coordinate used later during force traversal. This aggregation is the key summary that allows a whole distant subtree to stand in for its individual bodies.

### 4. Barnes–Hut force traversal

For each target body, `apply_force` recursively walks the tree:

1. An empty node contributes nothing.
2. A leaf contributes its body's force directly, unless it is the target body itself.
3. For an internal node, the code computes the distance from the target body to the node's stored aggregate position.
4. If the target is outside the cell and the cell side length is less than that distance, the entire cell is treated as one source with the node's total mass.
5. Otherwise, the traversal descends into the node's children for a more detailed calculation.

The acceptance check is an implicit opening-angle rule equivalent to comparing cell size with distance using a threshold of approximately `1`; there is no user-configurable theta parameter. The approximation trades some precision for fewer body-to-body force calculations.

The force calculation uses Newtonian gravity with `G = 6.67e-11`. A small lower bound (`1e-5`) is applied to distance to avoid division by zero. After accumulating force, each body calculates acceleration as force divided by its mass, updates velocity, and then updates position using a fixed time step `Dt = 1`. This is a simple velocity-first (semi-implicit Euler) integration step.

### 5. Repeat for each time step

The simulation repeats tree construction, aggregate computation, force calculation, and body movement for the requested number of steps. The tree for a step is only read during force evaluation; each parallel worker updates a different body, avoiding concurrent writes to the same body's state.

## Parallel implementations

### C++ with oneTBB

`main.cpp` contains the C++ implementation and uses oneTBB in two places:

- **Recursive tree aggregation:** `Node::compute_mass_center` uses `tbb::parallel_invoke` to compute the four child subtrees concurrently, then combines their `MassCenter` results.
- **Per-body force and movement:** `simulation` uses `tbb::parallel_for` with a `blocked_range` to divide the body vector among tasks. Each task resets its body's force, traverses the shared read-only tree, and updates that body's position and velocity.
- **Concurrency limit:** `tbb::global_control` sets the maximum allowed parallelism from the command-line thread-count argument.

The tree is built sequentially; the recursive mass aggregation and body updates are parallel. The two declared mutexes are not used by the current implementation.

### Java with a custom worker pool

The Java implementation is in `Barnes_hut/src/`:

- `CustomThreadPool` starts a fixed set of worker threads that take `Runnable` tasks from a `LinkedBlockingQueue`.
- `Simulation.simulation` divides the bodies into batches, submits those batches to the pool, and waits on a `CountDownLatch` before beginning the next time step. This ensures all body updates finish before the next tree is built.
- `Node.computeMassCenter` can submit child aggregation work to the same pool. It uses a depth limit based on the requested worker count; below that depth, `computeSequentially` completes the subtree without spawning more tasks. This limits recursive task creation and reduces the risk of worker starvation while child tasks wait for other tasks in the pool.
- Force traversal and body integration follow the same overall sequence as the C++ implementation.

## Input and output format

The input is whitespace-separated. Its first two values are the number of bodies and the root-region radius. Each following body record contains six values:

```text
<body_count>
<region_radius>
<x> <y> <vx> <vy> <mass> <name>
...
```

For example, the beginning of `input1.txt` is:

```text
5
2.50e11
0.000e00 0.000e00 0.000e00 0.000e00 1.989e30 sun
```

Both programs write the final body count and radius followed by the bodies' final position, velocity, mass, and name. The output filename is hard-coded as `output.txt` in the process's current working directory, and an existing file with that name is overwritten.

## Build and run

### C++ / oneTBB

Install a C++ compiler and oneTBB development files, then compile from the project root:

```bash
g++ -std=c++17 -O2 main.cpp -ltbb -o barnes_hut
```

Run with an input file, number of simulation steps, and maximum thread count:

```bash
./barnes_hut input1.txt 100 4
```

On Windows, use an environment where oneTBB is installed and its headers and libraries are available to the compiler, or build under WSL.

### Java

Compile and run from the Java source directory:

```bash
cd Barnes_hut/src
javac *.java
java Main input5.txt 100 4
```

The Java sample input files are also copied into `Barnes_hut/src/`. The result is written to `Barnes_hut/src/output.txt` when run from that directory.

The Java timing helper `run.sh` is configured for four measured runs of `input5.txt` with 10,000 steps and five threads. It expects Bash, `/usr/bin/time`, `bc`, and already compiled Java classes; it also uses a hard-coded sequential baseline of 21.35 seconds to calculate a speedup. Adjust these values before treating its output as a general benchmark. Run it from `Barnes_hut/src/` with `bash run.sh`.

## Project files

### C++ implementation

- `main.cpp` defines the body, quadtree, aggregate mass, force traversal, time-stepping loop, input/output functions, and command-line entry point.
- `input1.txt`–`input5.txt` are sample N-body datasets of increasing size and differing initial conditions.

### Java implementation (`Barnes_hut/src/`)

- `Main.java` parses command-line arguments, loads bodies, starts the simulation, and writes the final state.
- `Simulation.java` contains the time-step loop, Barnes–Hut force traversal, and parallel body batching.
- `Node.java` implements quadtree insertion, child management, and sequential/parallel aggregate computation.
- `Quad.java` represents square regions and computes the four child quadrants.
- `MassCenter.java` combines mass and coordinate aggregate values returned by the tree.
- `Celestial_Body.java` stores body state and implements force accumulation and integration.
- `CustomThreadPool.java` implements the fixed worker pool and task queue.
- `run.sh` runs a small hard-coded timing experiment.
- `input1.txt`–`input5.txt` are copies of the sample datasets for running Java from its source directory.
- `.class` files and `out/production/` are compiled IDE/build artifacts; the `.java` files are the editable implementation.

### Supporting artifacts

- `report3&4.pdf` is the accompanying assignment report.
- `output_video.mp4` is an included visualization artifact; it is not generated by either command-line implementation described here.
- `.vscode/` and `Barnes_hut/.idea/` contain editor/project configuration.

## Accuracy and implementation considerations

The current source should be treated as an educational implementation, not a validated precision simulator. In both language versions, a leaf's aggregate coordinate fields are set to the body's raw `x` and `y`, but the parent aggregation treats those fields as mass-weighted coordinate sums and divides their sum by total mass. Correct center-of-mass aggregation requires each leaf to contribute `mass * x` and `mass * y`. As written, internal-node representative positions can therefore be incorrect, which can affect approximate force calculations. The code's leaf-vs-leaf interactions still use direct body positions.

Other assumptions and limitations:

- Bodies are expected to have positive, nonzero masses; acceleration divides by the body's mass.
- Bodies should lie within the root square. The insertion routines do not report an out-of-bounds body if it fails to match a child quadrant.
- The fixed integration step is `1` and the opening-angle threshold is fixed in the force traversal; neither is configurable through the command line.
- The C++ version does not validate input-file opening or parsing before using the read values. The Java version reports I/O errors, but malformed numeric input can still raise parsing exceptions.
- There is no automated test suite or included accuracy comparison against a direct all-pairs reference implementation.
- `output.txt` is overwritten on each run, so copy or rename results if you need to compare multiple runs.

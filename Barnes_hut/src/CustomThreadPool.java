import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class CustomThreadPool {
    private final Worker[] workers;
    private final BlockingQueue<Runnable> taskQueue;
    private volatile boolean isRunning = true;

    public CustomThreadPool(int numThreads) {
        taskQueue = new LinkedBlockingQueue<>();
        workers = new Worker[numThreads];
        for (int i = 0; i < numThreads; i++) {
            workers[i] = new Worker();
            workers[i].start();
        }
    }

    // Submit a task to the pool
    public void submit(Runnable task) {
        if (isRunning) {
            taskQueue.offer(task);
        }

    }

    // Shutdown the thread pool
    public void shutdown() {
        isRunning = false;

//        if (!taskQueue.isEmpty()){
//            System.out.println("PROBLEM");
//        }

        // Interrupt all workers to ensure they exit if waiting on the taskQueue
        for (Worker worker : workers) {
            worker.interrupt();
        }

        // Wait for all workers to finish processing
        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Worker thread that processes tasks from the taskQueue
    private class Worker extends Thread {
        public void run() {
            while (isRunning || !taskQueue.isEmpty()) {
                try {
                    Runnable task = taskQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    if (!isRunning && taskQueue.isEmpty()) {
                        break;  // Stop working if the pool is shutting down and the queue is empty
                                // otherwise continue with the next task until queue is empty
                    }
                }
            }
        }
    }
}

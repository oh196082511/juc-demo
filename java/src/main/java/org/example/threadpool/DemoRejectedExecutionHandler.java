package org.example.threadpool;

public interface DemoRejectedExecutionHandler {

    void rejectedExecution(Runnable r, DemoThreadPoolExecutor executor);
}

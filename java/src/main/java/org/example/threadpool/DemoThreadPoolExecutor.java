package org.example.threadpool;

import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

public class DemoThreadPoolExecutor implements Executor {

    // 线程池实时状态 高3位表示线程池状态，低29位表示线程池中线程数量
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));

    // 29位
    private static final int COUNT_BITS = Integer.SIZE - 3;

    // 29位全1
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;

    // 线程池的5种状态，都在高3位
    private static final int RUNNING    = -1 << COUNT_BITS;
    private static final int SHUTDOWN   =  0 << COUNT_BITS;
    private static final int STOP       =  1 << COUNT_BITS;
    private static final int TIDYING    =  2 << COUNT_BITS;
    private static final int TERMINATED =  3 << COUNT_BITS;

    // ctl有关的计算方法
    private static int runStateOf(int c)     { return c & ~CAPACITY; }
    private static int workerCountOf(int c)  { return c & CAPACITY; }
    private static int ctlOf(int rs, int wc) { return rs | wc; }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    private static final boolean ONLY_ONE = true;

    // 完成任务数
    private long completedTaskCount;


    // 核心线程数量
    private volatile int corePoolSize;

    // 最大线程数
    private volatile int maximumPoolSize;

    // 工作线程存活时间，单位纳秒
    private volatile long keepAliveTime;

    // 线程工厂
    private volatile ThreadFactory threadFactory;

    // 阻塞队列
    private final BlockingQueue<Runnable> workQueue;

    // 拒绝策略
    private volatile DemoRejectedExecutionHandler handler;

    // 线程池的锁
    private final ReentrantLock mainLock = new ReentrantLock();

    // 拿到锁后可以修改
    private final HashSet<Worker> workers = new HashSet<Worker>();

    // 最大workers数量
    private int largestPoolSize;

    // 是否允许核心线程超时
    private volatile boolean allowCoreThreadTimeOut;

    private final class Worker
            extends AbstractQueuedSynchronizer
            implements Runnable {

        final Thread thread;

        Runnable firstTask;

        // 完成任务数量
        volatile long completedTasks;

        Worker(Runnable firstTask) {
            setState(-1); // AQS状态初始化为-1
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public boolean tryLock()  { return tryAcquire(1); }

        public void run() {
            runWorker(this);
        }

        public void lock()        { acquire(1); }

        public void unlock()      { release(1); }


    }


    /**
     * 构造函数，经典7大参数
     * @param corePoolSize
     * @param maximumPoolSize
     * @param keepAliveTime
     * @param unit
     * @param workQueue
     * @param threadFactory
     * @param handler
     */
    public DemoThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              DemoRejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }


    /**
     * 核心方法，提交任务
     * @param command the runnable task
     */
    @Override
    public void execute(Runnable command) {
        if (command == null)
            // 防御性编程
            throw new NullPointerException();
        // 获取当前线程池状态
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            // 工作线程数量小于核心线程数量，创建核心线程
            if (addWorker(command, true))
                // 创建成功，直接返回
                return;
            // 创建失败，说明情况有变，比如核心线程满了，或者线程池不是running了
            // 重新获取线程池状态
            c = ctl.get();
            if (isRunning(c) && workQueue.offer(command)) {
                // 如果线程池还处于运行状态，且任务成功加入队列，再次检查线程池状态
                int recheck = ctl.get();
                if (!isRunning(recheck) && remove(command))
                    // 如果线程池状态不是运行状态，且任务成功从队列移除，拒绝任务
                    reject(command);
                else if (workerCountOf(recheck) == 0)
                    // 如果没有工作线程，创建一个非核心线程处理阻塞队列
                    addWorker(null, false);
            }
            else if (!addWorker(command, false))
                // 增加非核心线程失败，拒绝任务
                // 否则成功
                reject(command);
        }

    }

    /**
     * 从阻塞队列里移除任务
     * @param task
     * @return
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        // 尝试终止线程池
        tryTerminate();
        return removed;
    }


    /**
     * 如果可以接收任务，并创建线程，返回true
     * @param firstTask 任务
     * @param core 是否创建核心线程
     * @return
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            // 获取当前线程池状态
            int c = ctl.get();
            int rs = runStateOf(c);

            if (rs >= SHUTDOWN &&
                    ! (rs == SHUTDOWN &&
                            firstTask == null &&
                            ! workQueue.isEmpty()))
                // 如果是shutdown，不接收任务
                // 除非是shutdown+task为空+队列不为空的清库存情况
                return false;

            for (;;) {
                // 获取当前工作线程数量
                int wc = workerCountOf(c);
                if (wc >= CAPACITY ||
                        wc >= (core ? corePoolSize : maximumPoolSize))
                    // 如果工作线程数量已经达到上限，不接收任务
                    return false;
                if (compareAndIncrementWorkerCount(c))
                    // 增加工作线程数量成功，跳出外层循环
                    break retry;
                // 重新读
                c = ctl.get();
                if (runStateOf(c) != rs)
                    // 如果线程池状态变了，重新开始
                    continue retry;
                // CAS失败，继续循环
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            // 创建工作线程
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                // 加锁用来调整workers和largestPoolSize
                mainLock.lock();
                try {
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                            (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive())
                            throw new IllegalThreadStateException();
                        // 添加到workers
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    // 启动线程
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (!workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    // 执行拒绝策略
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            // 加入失败
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            if (isRunning(c) ||
                    runStateAtLeast(c, TIDYING) ||
                    (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        // 终止
                        // terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * 每个worker的start会走到这
     * @param w
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                // 独占锁，防止interruptIdleWorkers方法中断该worker，至少执行完才能中断
                w.lock();

                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() &&
                                runStateAtLeast(ctl.get(), STOP))) &&
                        !wt.isInterrupted())
                    wt.interrupt();
                try {
                    // 执行任务
                    task.run();
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 累加完成任务数量
            completedTaskCount += w.completedTasks;
            // 删除wokers
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            addWorker(null, false);
        }
    }

    /**
     * 从阻塞队列拿一个任务出来
     * @return
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // shutdown且队列为空，或者比shutdown更高的状态
            // 减少工作线程数量
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            int wc = workerCountOf(c);

            // Are workers subject to culling?
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            if ((wc > maximumPoolSize || (timed && timedOut))
                    && (wc > 1 || workQueue.isEmpty())) {
                if (compareAndDecrementWorkerCount(c))
                    // 如果核心线程需要销毁(通过allowCoreThreadTimeOut=true配置)
                    // 或者线程数量大于核心线程数量，且超时了或者队列为空
                    // 这里返回null，上层会走到worker的退出逻辑
                    return null;
                continue;
            }

            try {
                // 如果只有核心线程，会阻塞在这里
                // 否则会超时，回到上面的循环
                Runnable r = timed ?
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        workQueue.take();
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }



    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }

    private void decrementWorkerCount() {
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }
}

package org.example.aqs;

import sun.misc.Unsafe;

import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

/**
 * 对标juc里的AbstractQueuedSynchronizer
 */
public abstract class DemoAbstractQueuedSynchronizer extends DemoAbstractOwnableSynchronizer {

    protected DemoAbstractQueuedSynchronizer() { }

    static final class Node {

        /**
         * 共享模式
         */
        static final Node SHARED = new Node();

        /**
         * 独占模式
         */
        static final Node EXCLUSIVE = null;

        /**
         * waitStatus值=0表示 节点刚初始化
         */
        static final int INIT = 0;

        /**
         * waitStatus值=1表示 节点已经取消
         */
        static final int CANCELLED = 1;

        /**
         * waitStatus值=-1表示 节点执行完毕后需要唤醒下一个节点
         */
        static final int SIGNAL = -1;

        /**
         * waitStatus值=-3表示 节点传播唤醒，用于共享模式
         */
        static final int PROPAGATE = -3;

        /**
         * Node节点状态
         */
        volatile int waitStatus;

        /**
         * 前驱节点
         */
        volatile Node prev;

        /**
         * 后继节点
         */
        volatile Node next;

        /**
         * 节点绑定的线程
         */
        volatile Thread thread;

        /**
         * 独占模式节点，用于条件队列连接下一个节点
         * 共享模式节点，用于判断节点是否为共享模式
         */
        Node nextWaiter;

        /**
         * 判断节点是否为共享模式
         * @return
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回前一个节点，如果前一个节点为null，则抛出异常
         * @return node的前一个Node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {}

        /**
         * 只有addWaiter用到
         * @param thread
         * @param mode
         */
        Node(Thread thread, Node mode) {
            this.nextWaiter = mode;
            this.thread = thread;
        }

        /**
         * 只有condition用到
         * @param thread
         * @param waitStatus
         */
        Node(Thread thread, int waitStatus) {
            this.waitStatus = waitStatus;
            this.thread = thread;
        }

    }

    /**
     * AQS同步队列的头结点，懒初始化
     * 只有setHead方法可以修改
     */
    private transient volatile Node head;

    /**
     * AQS同步队列的尾结点，懒初始化
     * 只有enq方法可以往后边增加等待节点
     */
    private transient volatile Node tail;

    /**
     * AQS核心状态变量
     */
    private volatile int state;

    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    /**
     * 将节点入队，如果队列为空，则初始化队列的头节点
     * 插入后返回node的前一个节点
     */
    private Node enq(final Node node) {
        for (;;) {
            // 拿到尾结点
            Node t = tail;
            if (t == null) {
                // 如果尾结点为空，说明队列为空，初始化队列的头结点
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                // CAS设置成新的尾结点，直到成功
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 按指定的模式创建node并入队，返回node
     */
    private Node addWaiter(Node mode) {
        // 创建节点
        Node node = new Node(Thread.currentThread(), mode);
        // 如果队列不为空，先尝试快速入队，如果不行再走enq，完整入队流程
        // (如果不是为了性能，看起来完全可以直接走enq?)
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }



    /**
     * 核心方法，独占模式下获取锁
     * @param arg
     */
    public final void acquire(int arg) {
        // 抢锁失败，则入队。
        // 入队后会阻塞，除非成功抢到锁
        // 如果interrupt了，标记interrupt状态，不处理
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * 将线程标记为interrupt状态
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * park，最终返回线程是否被中断
     * @return
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        // 返回线程中断状态，并清除
        return Thread.interrupted();
    }

    /**
     * 独占模式下，为已经在队列里的node抢锁
     * 如果condition里通过await唤醒了节点，也会用这个方法抢锁
     * @return 返回线程是否被中断，被中断返回true
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                // 死循环，直到获取锁成功

                // 拿到node的前一个节点
                final Node p = node.predecessor();

                // 如果前一个节点是头结点，说明轮到自己了(因为一定是头结点结束了，才会唤醒后继结点，也就是自己)
                // 无论自己是为什么醒过来的，只要前一个节点是头结点，就说明轮到自己了，应该抢锁
                // 抢锁成功，则将自己设置为头结点，然后将老节点的next指向null，help GC
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    // 返回最终的中断结果
                    return interrupted;
                }
                // 判断能不能park，如果可以，则park，如果醒来后发现interrupted了，设置interrupted结果
                // 这里不处理interrupted，只是记录下来，最终返回
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            // 最终发生了异常，取消抢锁
            if (failed) {
                // TODO 待实现
                // 没有被设置成头结点，说明一直在队列里，准备取消抢锁
                // cancelAcquire(node);
            }

        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.
     * Returns true if thread should block. This is the main signal
     * control in all acquire loops.  Requires that pred == node.prev.
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            // 前驱节点为signal，可以park
            return true;
        if (ws > 0) {
            // 前驱节点cancel了，找到前面最近的没cancel的节点，设置为自己的前驱节点
            // 无法park，等下后续进来这个方法，再判断
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            // 前驱节点是0和PROPAGATE会来到这个方法
            // 前驱节点设置为signal，如果成功了，让下一次进入本方法时，可以park
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * 独占模式下尝试获取锁
     * 获取成功记得修改对应的值
     * 如果不成功，会进入同步队列
     * 待继承类自由实现
     * @param arg
     * @return
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }








    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (DemoAbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (DemoAbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (DemoAbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (DemoAbstractQueuedSynchronizer.Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

}
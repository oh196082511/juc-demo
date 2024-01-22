package org.example.aqs;

/**
 * 对标juc里的AbstractOwnableSynchronizer
 */
public abstract class DemoAbstractOwnableSynchronizer {

    protected DemoAbstractOwnableSynchronizer() { }

    /**
     * 当前独占模式同步器的拥有者线程
     */
    private transient Thread exclusiveOwnerThread;

    /**
     * 设置当前独占模式同步器的拥有者线程
     * null表示没有被占用
     * @param thread
     */
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    /**
     * 获取当前独占模式同步器的拥有者线程
     * null表示没有被占用
     * @return
     */
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}

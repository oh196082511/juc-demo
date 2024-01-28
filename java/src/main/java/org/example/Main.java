package org.example;


import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {
    public static void main(String[] args) throws Exception {
        testReleaseRelease();
    }

    private static void testReleaseRelease() throws Exception {
        ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
        Thread readThread1 = new Thread(() -> {
            reentrantReadWriteLock.readLock().lock();
            try {
                System.out.println("read lock");
            } finally {
                try {
                    Thread.sleep(2000000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                reentrantReadWriteLock.readLock().unlock();
            }
        }, "readThread1");

        Thread readThread2 = new Thread(() -> {
            reentrantReadWriteLock.readLock().lock();
            try {
                System.out.println("read lock");
            } finally {
                reentrantReadWriteLock.readLock().unlock();
            }
        });
        readThread1.start();
        readThread2.start();
        readThread1.join();
        readThread2.join();
    }

    private static void testReadRead() throws Exception {
        ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
        Thread readThread1 = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            reentrantReadWriteLock.readLock().lock();
            try {
                System.out.println("read lock");
                Thread.sleep(3000000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                reentrantReadWriteLock.readLock().unlock();
            }
        }, "readThread1");

        Thread readThread2 = new Thread(() -> {
            reentrantReadWriteLock.readLock().lock();
            try {
                System.out.println("read lock");
                Thread.sleep(3000000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                reentrantReadWriteLock.readLock().unlock();
            }
        });
        readThread1.start();
        readThread2.start();
        readThread1.join();
        readThread2.join();
    }
}
package org.example.hashmap;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DemoConcurrentHashMap<K,V> {

    static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash

    static final int NCPU = Runtime.getRuntime().availableProcessors();

    private static final int MIN_TRANSFER_STRIDE = 16;

    private static final int MAXIMUM_CAPACITY = 1 << 30;

    static final int MOVED     = -1; // hash for forwarding nodes

    private static final int DEFAULT_CAPACITY = 16;

    private static int RESIZE_STAMP_BITS = 16;

    private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;

    private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;

    private transient volatile int transferIndex;

    // 小于-1表示正在扩容
    // -1表示正在初始化
    // 0表示还没初始化
    // 正数表示要初始化的长度或者扩容的阈值
    private transient volatile int sizeCtl;

    private static final sun.misc.Unsafe U;

    private static final long SIZECTL;

    private static final long ABASE;
    private static final int ASHIFT;

    private static final long TRANSFERINDEX;

    transient volatile Node<K,V>[] table;

    /**
     * 下一个需要使用的table,只有在resize的时候不为空
     */
    private transient volatile Node<K,V>[] nextTable;

    static class Node<K,V> {
        final int hash;
        final K key;
        volatile V val;
        volatile Node<K,V> next;

        Node(int hash, K key, V val, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.val = val;
            this.next = next;
        }

        public final K getKey()       { return key; }
        public final V getValue()     { return val; }
        public final int hashCode()   { return key.hashCode() ^ val.hashCode(); }
        public final String toString(){ return key + "=" + val; }
    }

    static {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            U = (Unsafe) field.get(null);
            Class<?> k = DemoConcurrentHashMap.class;
            SIZECTL = U.objectFieldOffset
                    (k.getDeclaredField("sizeCtl"));
            TRANSFERINDEX = U.objectFieldOffset
                    (k.getDeclaredField("transferIndex"));
            Class<?> ak = DemoConcurrentHashMap.Node[].class;
            // 初始偏移
            ABASE = U.arrayBaseOffset(ak);
            // 获取数组的每个元素大小
            int scale = U.arrayIndexScale(ak);
            if ((scale & (scale - 1)) != 0)
                throw new Error("data type scale not a power of two");
            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    static final class ForwardingNode<K,V> extends Node<K,V> {
        final Node<K,V>[] nextTable;
        ForwardingNode(Node<K,V>[] tab) {
            super(MOVED, null, null, null);
            this.nextTable = tab;
        }
    }

    static final <K,V> Node<K,V> tabAt(Node<K,V>[] tab, int i) {
        return (Node<K,V>)U.getObjectVolatile(tab, ((long)i << ASHIFT) + ABASE);
    }

    public V put(K key, V value) {
        return putVal(key, value, false);
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        tryPresize(m.size());
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
            putVal(e.getKey(), e.getValue(), false);
    }

    static final int spread(int h) {
        return (h ^ (h >>> 16)) & HASH_BITS;
    }

    final V putVal(K key, V value, boolean onlyIfAbsent) {
        // key和value不允许为null
        if (key == null || value == null) throw new NullPointerException();
        int hash = spread(key.hashCode());
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {

            Node<K,V> f;
            int n, i, fh;
            if (tab == null || (n = tab.length) == 0)
                // tab不存在的时候，初始化table
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                // 如果对应的哈希槽没有值，通过CAS添加
                if (casTabAt(tab, i, null,
                        new Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
            else if ((fh = f.hash) == MOVED)
                // 如果对应的哈希槽上hash是移动标识位，则帮助移动
                tab = helpTransfer(tab, f);
            else {
                // 否则，往里面继续添加数据
                V oldVal = null;
                synchronized (f) {
                    // 对f加锁
                    if (tabAt(tab, i) == f) {
                        // 双重检查，只有是f才处理
                        if (fh >= 0) {
                            // 红黑树是-2，>=0是链表结构
                            binCount = 1;// 实时统计该哈希槽的元素个数
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == hash &&
                                        ((ek = e.key) == key ||
                                                (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                            value, null);
                                    break;
                                }
                            }
                        }
                    }
                }
                if (binCount != 0) {
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        return null;
    }

    final Node<K,V>[] helpTransfer(Node<K,V>[] tab, Node<K,V> f) {
        Node<K,V>[] nextTab;
        int sc;
        if (tab != null && (f instanceof ForwardingNode) &&
                (nextTab = ((ForwardingNode<K,V>)f).nextTable) != null) {
            int rs = resizeStamp(tab.length);
            while (nextTab == nextTable && table == tab &&
                    (sc = sizeCtl) < 0) {
                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                        sc == rs + MAX_RESIZERS || transferIndex <= 0)
                    break;
                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
                    transfer(tab, nextTab);
                    break;
                }
            }
            return nextTab;
        }
        return table;
    }

    private final void transfer(Node<K,V>[] tab, Node<K,V>[] nextTab) {
        int n = tab.length, stride;
        // 原数组会按每段stride划分，以stride为单位，由线程领取任务完成扩容
        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
            stride = MIN_TRANSFER_STRIDE; // subdivide range
        if (nextTab == null) {            // initiating
            // 如果是本次扩容的首次transfer，扩容成2倍
            try {
                @SuppressWarnings("unchecked")
                // 初始化
                Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n << 1];
                nextTab = nt;
            } catch (Throwable ex) {      // try to cope with OOME
                sizeCtl = Integer.MAX_VALUE;
                return;
            }
            nextTable = nextTab;
            // 从尾往头认领
            transferIndex = n;
        }
        // 本次扩容的最大长度
        int nextn = nextTab.length;
        ForwardingNode<K,V> fwd = new ForwardingNode<K,V>(nextTab);
        // true表示可以去领任务
        boolean advance = true;
        // true表示完成
        boolean finishing = false;
        for (int i = 0, bound = 0;;) {
            // 死循环
            Node<K,V> f;
            int fh;
            while (advance) {
                int nextIndex, nextBound;
                if (--i >= bound || finishing)
                    advance = false;
                else if ((nextIndex = transferIndex) <= 0) {
                    i = -1;
                    advance = false;
                }
                else if (U.compareAndSwapInt
                        (this, TRANSFERINDEX, nextIndex,
                                nextBound = (nextIndex > stride ?
                                        nextIndex - stride : 0))) {
                    // CAS扣除transferIndex，表示认领成功
                    // 本次处理区间为[nextBound,oldTransferIndex)
                    bound = nextBound;
                    i = nextIndex - 1;
                    advance = false;
                }
            }
            if (i < 0 || i >= n || i + n >= nextn) {
                // 说明扩容完事了
                int sc;
                if (finishing) {
                    // 正常结束
                    nextTable = null;
                    table = nextTab;
                    sizeCtl = (n << 1) - (n >>> 1);
                    return;
                }
                if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
                    // 更新状态
                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
                        return;
                    finishing = advance = true;
                    i = n; // recheck before commit
                }
            }
            else if ((f = tabAt(tab, i)) == null)
                // 为空则直接用fwd占位
                advance = casTabAt(tab, i, null, fwd);
            else if ((fh = f.hash) == MOVED)
                // 如果f已经是占位，继续领取下一次
                advance = true; // already processed
            else {
                synchronized (f) {
                    // 准备迁移
                    if (tabAt(tab, i) == f) {
                        // 双重检查
                        Node<K,V> ln, hn;
                        if (fh >= 0) {
                            // 处理链表
                            int runBit = fh & n;
                            Node<K,V> lastRun = f;
                            for (Node<K,V> p = f.next; p != null; p = p.next) {
                                int b = p.hash & n;
                                if (b != runBit) {
                                    runBit = b;
                                    lastRun = p;
                                }
                            }
                            if (runBit == 0) {
                                ln = lastRun;
                                hn = null;
                            }
                            else {
                                hn = lastRun;
                                ln = null;
                            }
                            for (Node<K,V> p = f; p != lastRun; p = p.next) {
                                int ph = p.hash; K pk = p.key; V pv = p.val;
                                if ((ph & n) == 0)
                                    ln = new Node<K,V>(ph, pk, pv, ln);
                                else
                                    hn = new Node<K,V>(ph, pk, pv, hn);
                            }
                            setTabAt(nextTab, i, ln);
                            setTabAt(nextTab, i + n, hn);
                            setTabAt(tab, i, fwd);
                            advance = true;
                        }
                    }
                }
            }
        }

    }

    private final void tryPresize(int size) {
        // 求出本次需要扩容到的值
        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY :
                tableSizeFor(size + (size >>> 1) + 1);
        int sc;
        while ((sc = sizeCtl) >= 0) {
            // 只有不处于扩容/初始化状态，才能走进来扩容
            // 拿到老的table
            Node<K,V>[] tab = table;
            int n;
            if (tab == null || (n = tab.length) == 0) {
                // 如果原来tab为空
                n = (sc > c) ? sc : c;
                // 走初始化逻辑
                if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                    try {
                        if (table == tab) {
                            @SuppressWarnings("unchecked")
                            Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                            table = nt;
                            sc = n - (n >>> 2);
                        }
                    } finally {
                        sizeCtl = sc;
                    }
                }
            }
            else if (c <= sc || n >= MAXIMUM_CAPACITY)
                // 太小或者太大，不扩容
                break;
            else if (tab == table) {
                // 否则开始扩容，低位是n的高位0数量，第16位的1表示resize标记
                int rs = resizeStamp(n);
                if (sc < 0) {
                    // 如果正在扩容
                    Node<K,V>[] nt;
                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
                            sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
                            transferIndex <= 0)
                        // 无法帮助扩容
                        break;
                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
                        // 帮助扩容，带上当前的nextTable
                        transfer(tab, nt);
                }
                // 本次为第一次扩容，设置标记为负数
                else if (U.compareAndSwapInt(this, SIZECTL, sc,
                        (rs << RESIZE_STAMP_SHIFT) + 2))
                    // 首次扩容
                    transfer(tab, null);
            }
        }
    }

    private final Node<K,V>[] initTable() {
        Node<K,V>[] tab;
        int sc;
        while ((tab = table) == null || tab.length == 0) {
            if ((sc = sizeCtl) < 0)
                // 小于零说明正在扩容或者初始化，线程先让出时间片
                Thread.yield();
            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
                // CAS从sc改成-1
                try {
                    if ((tab = table) == null || tab.length == 0) {
                        // 双重检查
                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
                        @SuppressWarnings("unchecked")
                        Node<K,V>[] nt = (Node<K,V>[])new Node<?,?>[n];
                        table = tab = nt;
                        // sc=3/4*n
                        sc = n - (n >>> 2);
                    }
                } finally {
                    // sc赋值给sizectl
                    // 要么0 要么正数
                    sizeCtl = sc;
                }
                break;
            }
        }
        return tab;
    }

    static final <K,V> boolean casTabAt(Node<K,V>[] tab, int i,
                                        Node<K,V> c, Node<K,V> v) {
        return U.compareAndSwapObject(tab, ((long)i << ASHIFT) + ABASE, c, v);
    }

    static final <K,V> void setTabAt(Node<K,V>[] tab, int i, Node<K,V> v) {
        U.putObjectVolatile(tab, ((long)i << ASHIFT) + ABASE, v);
    }

    private static final int tableSizeFor(int c) {
        int n = c - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    static final int resizeStamp(int n) {
        return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
    }

}

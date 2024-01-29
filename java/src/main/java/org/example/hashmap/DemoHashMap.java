package org.example.hashmap;

import java.util.HashMap;

public class DemoHashMap<K, V> {

    final float loadFactor;

    // 负载因子，超过会扩容
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // 初始容量
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    // 最大容量
    static final int MAXIMUM_CAPACITY = 1 << 30;

    // hashmap的键值对数量
    transient int size;

    // 记录hashmap被修改的次数
    transient int modCount;

    // 容器允许的键值对最大阈值，超过会扩容
    // 通过数组长度*负载因子得到
    int threshold;

    // 节点数组
    transient DemoNode<K,V>[] table;

    public DemoHashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
    }

    /**
     * 节点
     * @param <K>
     * @param <V>
     */
    static class DemoNode<K,V> {

        final int hash;
        final K key;
        V value;
        DemoNode<K,V> next;

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }

        /**
         * 构造函数
         * @param hash
         * @param key
         * @param value
         * @param next
         */
        DemoNode(int hash, K key, V value, DemoNode<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    DemoNode<K,V> newNode(int hash, K key, V value, DemoNode<K,V> next) {
        return new DemoNode<>(hash, key, value, next);
    }

    static final int hash(Object key) {
        int h;
        // 高16位和低16位异或，减少hash碰撞
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }


    public V put(K key, V value) {
        return putVal(hash(key), key, value, false);
    }

    final V putVal(int hash, K key, V value, boolean onlyIfAbsent) {
        DemoNode<K,V>[] tab;
        DemoNode<K,V> p;
        int n, i;
        if ((tab = table) == null || (n = tab.length) == 0) {
            // 如果table为空，初始化table
            n = (tab = resize()).length;
        }
        if ((p = tab[i = (n - 1) & hash]) == null) {
            // 如果table[i]为空，当场创建一个
            tab[i] = newNode(hash, key, value, null);
        } else {
            // p=table[i]不为空
            DemoNode<K,V> e;
            K k;
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k)))) {
                // hash相同，且(key相同或key相互equals)
                // 拿到这个p
                e = p;
            } else {
                for (int binCount = 0; ; ++binCount) {
                    // p是前一个节点，e是当前节点
                    if ((e = p.next) == null) {
                        // 如果找不到节点，创建一个新节点
                        p.next = newNode(hash, key, value, null);
                        break;
                    }
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        // 判断hashmap中是否已经存在这个key的经典判断条件
                        // 找到了直接跳出循环
                        break;
                    p = e;
                }

            }
            // e是原来map里key对应的节点
            if (e != null) {
                // map里本来就存在这个key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    // 如果不是onlyIfAbsent，或者oldValue为空，就替换
                    e.value = value;
                // 返回旧值
                return oldValue;
            }


        }
        ++modCount;
        if (++size > threshold)
            // 超过则扩容
            resize();
        // 因为没有旧值，所以返回null
        return null;
    }

    final DemoNode<K,V>[] resize() {
        // 拿到原数组
        DemoNode<K,V>[] oldTab = table;

        // 拿到原数组的长度
        int oldCap = (oldTab == null) ? 0 : oldTab.length;

        // 原来的阈值
        int oldThr = threshold;

        // 新容量，新阈值
        int newCap, newThr = 0;

        if (oldCap > 0) {
            // 如果原来的容量大于0，说明原来的数组已经初始化过了
            if (oldCap >= MAXIMUM_CAPACITY) {
                // 已经达到最大容量，阈值设置为最大值，返回原数组
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                    oldCap >= DEFAULT_INITIAL_CAPACITY)
                // 扩容2倍，但是不能超过最大容量。阈值扩大2倍
                newThr = oldThr << 1; //
        } else if (oldThr > 0) {
            // 还没初始化过，新容量为阈值
            newCap = oldThr;
        } else {
            // 还没初始化过，阈值也没有设置过，使用默认值
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int) (DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            // 如果还没初始化过，计算新阈值
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);
        }

        // 计算完新容量和新阈值，准备设置到hashmap中
        threshold = newThr;

        // 创建新数组
        @SuppressWarnings({"rawtypes","unchecked"})
        DemoNode<K,V>[] newTab = (DemoNode<K,V>[])new DemoNode[newCap];

        // 设置到hashmap中(并发情况下，可能会导致其他get操作拿不到值)
        table = newTab;

        // 准备开始复制
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                // 遍历原数组
                DemoNode<K,V> e;
                if ((e = oldTab[j]) != null) {
                    // 如果原数组的元素不为空，准备复制

                    // 将原数组的元素置为null，方便回收原数组
                    oldTab[j] = null;
                    if (e.next == null) {
                        // e如果是最后一个节点，直接复制到新数组中
                        newTab[e.hash & (newCap - 1)] = e;
                    } else {
                        // 将老节点下的所有数据移动到新数组中
                        // 老数据会一分为二，一半在低位(原数组索引)，一半在高位(新数组相对老数组新增区域)

                        // 低位
                        DemoNode<K,V> loHead = null, loTail = null;

                        // 高位
                        DemoNode<K,V> hiHead = null, hiTail = null;
                        DemoNode<K,V> next;


                        // 针对链表而言
                        // 原链表头节点转化为head，尾节点转化为tail
                        do {
                            // 拿到e的next
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                // 低位
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {
                                // 高位
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);

                        if (loTail != null) {
                            // 赋值低位
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            // 赋值高位
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }

                }
            }
        }
        return newTab;
    }

    public V get(Object key) {
        DemoNode<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    final DemoNode<K,V> getNode(int hash, Object key) {
        DemoNode<K,V>[] tab;
        DemoNode<K,V> first, e;
        int n;
        K k;
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (first = tab[(n - 1) & hash]) != null) {
            // 如果表存在，且存在节点，则开始搜索
            if (first.hash == hash &&
                    ((k = first.key) == key || (key != null && key.equals(k))))
                // 必须hash相等且(key相等或者equals)
                return first;
            if ((e = first.next) != null) {
                // 否则，无脑找到最后一个节点，直到找到为止
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }

    public V remove(Object key) {
        DemoNode<K,V> e;
        return (e = removeNode(hash(key), key)) == null ?
                null : e.value;
    }

    final DemoNode<K,V> removeNode(int hash, Object key) {
        DemoNode<K,V>[] tab;
        DemoNode<K,V> p;
        int n, index;
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (p = tab[index = (n - 1) & hash]) != null) {
            // 如果表存在，且存在节点，则开始搜索
            // 逻辑跟get大差不差
            DemoNode<K,V> node = null, e;
            K k;
            V v;

            // 找到匹配的node，以及它的前驱节点p(p==node表示node是第一个节点)
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k)))) {
                node = p;
            } else if ((e = p.next) != null) {
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key ||
                                    (key != null && key.equals(k)))) {
                        node = e;
                        break;
                    }
                    p = e;
                } while ((e = e.next) != null);
            }
            if (node != null) {
                if (node == p)
                    // 如果node是第一个节点，直接将node.next赋值给tab[index]
                    tab[index] = node.next;
                else
                    // 否则，将node.next赋值给p.next
                    p.next = node.next;
                ++modCount;
                --size;
                return node;
            }
        }
        // 找不到，返回null
        return null;
    }


}

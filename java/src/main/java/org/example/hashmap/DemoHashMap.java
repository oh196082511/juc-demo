package org.example.hashmap;

public class DemoHashMap<K, V> {

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
    int threshold;

    // 节点数组
    transient DemoNode<K,V>[] table;

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
        // TODO
        return null;
    }
}

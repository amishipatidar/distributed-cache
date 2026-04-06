import java.util.*;

//Distribution Strategy
interface DistributionStrategy<K> {
    int getNodeIndex(K key, int totalNodes);
}

class ModuloDistributionStrategy<K> implements DistributionStrategy<K> {
    @Override
    public int getNodeIndex(K key, int totalNodes) {
        return Math.abs(key.hashCode()) % totalNodes;
    }
}

//Eviction Policy
interface EvictionPolicy<K> {
    void keyAccessed(K key);
    K evictKey();
}

class LRUEvictionPolicy<K> implements EvictionPolicy<K> {
    private LinkedHashSet<K> set = new LinkedHashSet<>();

    @Override
    public void keyAccessed(K key) {
        set.remove(key);
        set.add(key);
    }

    @Override
    public K evictKey() {
        Iterator<K> it = set.iterator();
        K leastUsed = it.next();
        it.remove();
        return leastUsed;
    }
}

//Cache Node
class CacheNode<K, V> {
    private int capacity;
    private Map<K, V> map;
    private EvictionPolicy<K> evictionPolicy;

    public CacheNode(int capacity, EvictionPolicy<K> policy) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.evictionPolicy = policy;
    }

    public V get(K key) {
        if (!map.containsKey(key)) return null;

        evictionPolicy.keyAccessed(key);
        return map.get(key);
    }

    public void put(K key, V value) {
        if (map.containsKey(key)) {
            map.put(key, value);
            evictionPolicy.keyAccessed(key);
            return;
        }

        if (map.size() >= capacity) {
            K evict = evictionPolicy.evictKey();
            map.remove(evict);
        }

        map.put(key, value);
        evictionPolicy.keyAccessed(key);
    }
}

//Mock Database
class Database<K, V> {
    private Map<K, V> storage = new HashMap<>();

    public V get(K key) {
        System.out.println("Fetching from DB for key: " + key);
        return storage.get(key);
    }

    public void put(K key, V value) {
        storage.put(key, value);
    }
}

//Distributed Cache
class DistributedCache<K, V> {
    private List<CacheNode<K, V>> nodes;
    private DistributionStrategy<K> strategy;
    private Database<K, V> database;

    public DistributedCache(int numNodes, int capacityPerNode,
                            DistributionStrategy<K> strategy,
                            Database<K, V> database) {

        this.strategy = strategy;
        this.database = database;
        this.nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new CacheNode<>(capacityPerNode, new LRUEvictionPolicy<>()));
        }
    }

    private CacheNode<K, V> getNode(K key) {
        int index = strategy.getNodeIndex(key, nodes.size());
        return nodes.get(index);
    }

    public V get(K key) {
        CacheNode<K, V> node = getNode(key);
        V value = node.get(key);

        if (value == null) {
            value = database.get(key);
            if (value != null) {
                node.put(key, value);
            }
        }
        return value;
    }

    public void put(K key, V value) {
        CacheNode<K, V> node = getNode(key);
        node.put(key, value);

        // Write-through assumption
        database.put(key, value);
    }
}

//Main Class
public class DistributedCacheSystem {
    public static void main(String[] args) {

        Database<String, String> db = new Database<>();

        // Preload DB
        db.put("A", "Apple");
        db.put("B", "Banana");

        DistributedCache<String, String> cache =
                new DistributedCache<>(3, 2,
                        new ModuloDistributionStrategy<>(), db);

        // First access (cache miss → DB hit)
        System.out.println("Get A: " + cache.get("A"));

        // Second access (cache hit)
        System.out.println("Get A: " + cache.get("A"));

        // Put new value
        cache.put("C", "Cherry");

        System.out.println("Get C: " + cache.get("C"));
    }
}
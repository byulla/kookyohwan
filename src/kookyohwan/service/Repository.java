package kookyohwan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Repository<K, V> {
    private final Map<K, V> items = new LinkedHashMap<>();

    public void put(K key, V value) {
        items.put(key, value);
    }

    public Optional<V> find(K key) {
        return Optional.ofNullable(items.get(key));
    }

    public boolean exists(K key) {
        return items.containsKey(key);
    }

    public List<V> all() {
        return new ArrayList<>(items.values());
    }

    public int size() {
        return items.size();
    }
}

package kookyohwan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 키로 찾기 / 존재 확인 / 전체 목록. 과목(String 키)·학생(String 키)·요청(Integer 키)이 공유하는 제네릭 저장소.
 * LinkedHashMap이라 넣은 순서가 유지된다(요청 번호 순, 파일 줄 순).
 */
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

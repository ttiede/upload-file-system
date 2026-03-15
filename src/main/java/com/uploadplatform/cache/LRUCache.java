package com.uploadplatform.cache;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K, V> extends LinkedHashMap<K, V> {

    private final int tamanhoMaximo;

    public LRUCache(int tamanhoMaximo) {
        super(tamanhoMaximo, 0.75f, true);
        if (tamanhoMaximo <= 0) throw new IllegalArgumentException("tamanhoMaximo deve ser positivo");
        this.tamanhoMaximo = tamanhoMaximo;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > tamanhoMaximo;
    }

    @Override public synchronized V get(Object key)        { return super.get(key); }
    @Override public synchronized V put(K key, V value)    { return super.put(key, value); }
    @Override public synchronized V remove(Object key)     { return super.remove(key); }
    @Override public synchronized boolean containsKey(Object key) { return super.containsKey(key); }
    @Override public synchronized int size()               { return super.size(); }
    @Override public synchronized void clear()             { super.clear(); }

    public int getTamanhoMaximo() { return tamanhoMaximo; }
}

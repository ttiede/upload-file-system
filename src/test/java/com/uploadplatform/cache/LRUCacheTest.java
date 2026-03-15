package com.uploadplatform.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LRUCacheTest {

    @Test
    @DisplayName("evicta o elemento mais antigo quando chega no limite")
    void evictsEldestWhenFull() {
        LRUCache<String, String> cache = new LRUCache<>(3);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("d", "4");

        assertThat(cache.containsKey("a")).isFalse();
        assertThat(cache.containsKey("b")).isTrue();
        assertThat(cache.containsKey("c")).isTrue();
        assertThat(cache.containsKey("d")).isTrue();
        assertThat(cache.size()).isEqualTo(3);
    }

    @Test
    @DisplayName("acessar um elemento o promove e evita que seja evictado")
    void accessPromotesEntry() {
        LRUCache<String, String> cache = new LRUCache<>(3);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.get("a");
        cache.put("d", "4");

        assertThat(cache.containsKey("b")).isFalse();
        assertThat(cache.containsKey("a")).isTrue();
        assertThat(cache.containsKey("c")).isTrue();
        assertThat(cache.containsKey("d")).isTrue();
    }

    @Test
    @DisplayName("re-inserir uma chave existente a promove para mru")
    void rePutPromotesExistingEntry() {
        LRUCache<String, String> cache = new LRUCache<>(3);

        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        cache.put("a", "atualizado");
        cache.put("d", "4");

        assertThat(cache.containsKey("b")).isFalse();
        assertThat(cache.get("a")).isEqualTo("atualizado");
    }

    @Test
    @DisplayName("remove deleta a entrada e reduz o tamanho")
    void removesEntry() {
        LRUCache<String, String> cache = new LRUCache<>(5);
        cache.put("x", "val");

        cache.remove("x");

        assertThat(cache.containsKey("x")).isFalse();
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("clear esvazia o cache")
    void clearEmptiesCache() {
        LRUCache<Integer, String> cache = new LRUCache<>(100);
        for (int i = 0; i < 50; i++) {
            cache.put(i, "v" + i);
        }
        assertThat(cache.size()).isEqualTo(50);

        cache.clear();

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("getTamanhoMaximo retorna o valor passado no construtor")
    void getTamanhoMaximoRetornaValorCorreto() {
        LRUCache<String, String> cache = new LRUCache<>(42);
        assertThat(cache.getTamanhoMaximo()).isEqualTo(42);
    }

    @Test
    @DisplayName("construtor rejeita tamanho nao positivo")
    void constructorRejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> new LRUCache<>(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LRUCache<>(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("puts concorrentes de varias threads nao corrompem o cache")
    void concurrentPutsAreThreadSafe() throws InterruptedException {
        LRUCache<Integer, String> cache = new LRUCache<>(1000);
        int threadCount = 10;
        int insertsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int base = t * insertsPerThread;
            threads[t] = new Thread(() -> {
                for (int i = base; i < base + insertsPerThread; i++) {
                    cache.put(i, "v" + i);
                }
            });
        }

        for (Thread thread : threads) thread.start();
        for (Thread thread : threads) thread.join();

        assertThat(cache.size()).isEqualTo(threadCount * insertsPerThread);
    }
}

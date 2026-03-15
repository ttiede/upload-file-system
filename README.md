# upload-file-system

api de upload de arquivos com suporte a multipart, cache em camadas e compartilhamento via link.

## stack

- java 21 + spring boot 3.2
- postgresql + spring data jpa
- lombok
- junit 5 + assertj + mockito

## como rodar

```bash
export DB_USER=postgres
export DB_PASS=postgres

mvn spring-boot:run
```

testes:

```bash
mvn test
```

swagger em `http://localhost:8080/swagger-ui.html`

## estrutura

```
src/
├── upload/     → multipart: initiate, parts, complete, abort
├── metadata/   → metadados e cache
├── cache/      → lru cache
├── event/      → eventos com idempotencia
├── sharing/    → links temporários
└── common/     → exception handler
```

## collections

cada estrutura foi escolhida por um motivo específico, não por padrão

| onde | collection | por que |
|------|-----------|---------|
| partes do upload | `TreeMap<Integer, PartInfo>` | partes chegam fora de ordem num upload paralelo — o treemap ordena por chave automaticamente, sem sort manual |
| sessões ativas | `ConcurrentHashMap` | vários uploads simultâneos, lock por segmento em vez de lock global |
| lru cache (l1) | `LinkedHashMap(accessOrder=true)` | `removeEldestEntry` faz o eviction automático do item menos recente |
| cache l2 | `ConcurrentHashMap` | thread-safe sem synchronized explícito |
| idempotência de eventos | `ConcurrentHashMap.newKeySet()` | `add()` retorna false se o eventId já existe — one-liner pra idempotência |
| agrupamento por status | `EnumMap` | acesso por índice de array, mais eficiente que hashmap quando a chave é enum |
| allowlist de email | `HashSet` | `contains()` em O(1) — não precisa de ordem |
| erros de validação | `LinkedHashMap` | mantém a ordem de declaração dos campos do dto |

## endpoints

**upload**
```
POST   /api/v1/uploads/initiate
PUT    /api/v1/uploads/{id}/parts/{n}
POST   /api/v1/uploads/{id}/complete
DELETE /api/v1/uploads/{id}
```

**arquivos**
```
GET    /api/v1/files/{fileId}
GET    /api/v1/files/users/{userId}
DELETE /api/v1/files/{fileId}
```

**compartilhamento**
```
POST   /api/v1/share
GET    /api/v1/share/{token}/validate
DELETE /api/v1/share/{token}
GET    /api/v1/share/files/{fileId}/links
```

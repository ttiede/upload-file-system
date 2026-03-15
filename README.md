# upload-file-system

api de upload de arquivos com suporte a multipart, cache em camadas e compartilhamento via link.

## stack

- java 21 + spring boot 3.2
- postgresql + spring data jpa
- redis (simulado em memória, pronto pra trocar pelo real)
- lombok
- junit 5 + assertj + mockito
- jacoco + checkstyle + spotbugs
- docker + terraform

## como rodar local

```bash
docker compose up
```

ou sem docker:

```bash
export DB_USER=postgres
export DB_PASS=postgres

mvn spring-boot:run -Dspring.profiles.active=dev
```

testes:

```bash
mvn test           # unitários
mvn verify         # unitários + integração + cobertura
```

swagger em `http://localhost:8080/swagger-ui.html`

## estrutura

```
src/
├── upload/     → multipart: initiate, parts, complete, abort
├── metadata/   → metadados com cache l1/l2/banco
├── cache/      → lru cache
├── event/      → processamento de eventos com idempotência
├── sharing/    → links temporários com allowlist e expiração
└── common/     → exception handler global
infra/
└── terraform/  → vpc, rds, elasticache, s3, sqs, ecs fargate, alb
.github/
└── workflows/  → ci (test + checkstyle) e cd (ecr + ecs deploy)
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

## aws

a infra foi pensada pra escalar sem gerenciar servidor

| componente | serviço | por que |
|-----------|---------|---------|
| storage | s3 multipart upload | suporte nativo a uploads grandes em partes, retenção e versionamento |
| banco | rds postgres | relacional com suporte a `@Version` (optimistic lock) e transações |
| cache | elasticache redis | substituição direta do `ConcurrentHashMap` que simula o redis aqui |
| fila de eventos | sqs + dlq | desacoplamento entre upload e processamento (antivírus, thumbnail, indexação) |
| container | ecs fargate | serverless — sem ec2, auto scaling nativo |
| load balancer | alb | health check no `/actuator/health`, roteamento pro fargate |
| imagem | ecr | registry privado integrado ao ecs |
| logs | cloudwatch | centralizado, retenção configurada |
| cdn | cloudfront | distribuição dos arquivos do s3 com edge caching |
| auth | cognito | jwt gerenciado, sem implementar do zero |

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

## ci/cd

push na `main` → testes + checkstyle → build da imagem → push no ecr → deploy no ecs fargate

```
.github/workflows/ci.yml  → roda em todo PR e push
.github/workflows/cd.yml  → roda só na main
```

## infra

```bash
cd infra/terraform
terraform init
terraform plan -var="db_password=suasenha"
terraform apply -var="db_password=suasenha"
```

## to do

- [ ] spring security + jwt (cognito)
- [ ] integração real com s3 sdk
- [ ] redis real com ttl
- [ ] versionamento de arquivos
- [ ] rate limiting por usuário
- [ ] métricas custom no cloudwatch

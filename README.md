# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the diff using RAG over the codebase, and posts review comments
back to the PR — built as a Kafka-backed microservices system.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)
[![ingestion-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/ingestion-service-ci.yml)

## Status

🚧 Actively in development. Not yet deployed.

| Service | Status |
|---|---|
| `webhook-service` | ✅ Built — receives GitHub PR webhooks, verifies HMAC-SHA256 signatures, publishes events to Kafka |
| `ingestion-service` | ✅ Built — consumes PR events, clones repos, chunks source code, generates embeddings, persists to Postgres + pgvector |
| `review-service` | ✅ Built — RAG retrieval + Claude-powered PR review, persisted with status tracking |
| `notification-service` | ⏳ Planned — posts review comments back to GitHub PR |
| `api-gateway` | ⏳ Planned |
| `frontend` | ⏳ Planned — React dashboard |

**Core AI pipeline is fully working end-to-end**, verified against a real GitHub
repo: webhook triggers ingestion, real embeddings generated via Voyage AI, pgvector
similarity search retrieves relevant code context, Claude generates a structured
8,000+ character code review stored in Postgres.

## Architecture

```
GitHub PR event
      │
      ▼
webhook-service ──► Kafka (pr.events) ──► ingestion-service
                         │                      │
                         │          clone → chunk → embed (Voyage AI)
                         │                      │
                         │               Postgres + pgvector
                         │                      │
                         └──────────► review-service
                                            │
                              embed query → similarity search
                                            │
                                     Claude API (RAG review)
                                            │
                                    reviews table (Postgres)
                                            │
                                    notification-service ──► GitHub PR comment
                                         (planned)
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5
- **Messaging:** Apache Kafka
- **Data:** PostgreSQL + pgvector (HNSW index, cosine similarity), Redis
- **Schema migrations:** Flyway (coordinated across services, versioned globally)
- **Repo cloning:** JGit (pure-Java Git client, shallow clones)
- **Embeddings:** Voyage AI (`voyage-code-2`, 1536-dim, code-specialized)
- **AI review:** Claude API — `claude-haiku-4-5` (model cascading planned)
- **Frontend (planned):** React, TypeScript
- **Infra:** Docker Compose (local), GitHub Actions (CI)

## Local development

Requires Docker, Java 21, Gradle, and the following env vars in `~/.zshrc`:

```bash
export VOYAGE_API_KEY="your-voyage-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
```

Start infrastructure:

```bash
docker compose up -d
```

Run services (each in its own terminal):

```bash
cd services/webhook-service && ./gradlew bootRun    # :8081
cd services/ingestion-service && ./gradlew bootRun  # :8082
cd services/review-service && ./gradlew bootRun     # :8083
```

## webhook-service

Entry point for GitHub PR events.

- Validates incoming webhooks via HMAC-SHA256 signature verification
  (constant-time comparison, prevents forged requests and timing attacks)
- Publishes validated events to Kafka topic `pr.events`, keyed by repo
  for per-repository ordering guarantees
- Idempotent Kafka producer (`acks=all`, `enable.idempotence=true`)
- 9 tests: unit tests on HMAC verification + Testcontainers integration tests

```bash
cd services/webhook-service
./gradlew test
./gradlew bootRun   # :8081
```

## ingestion-service

Kafka consumer that turns a PR event into searchable, embedded code context.

- Consumes `pr.events` via `@KafkaListener` with `ErrorHandlingDeserializer`
  (prevents infinite retry loop on malformed messages)
- Shallow-clones repos with JGit (depth=1), filters to source files only
- Chunks source files into 60-line segments (line-based baseline strategy)
- Persists chunks to Postgres via Spring Data JPA, schema via Flyway
  (`ddl-auto: validate` — no auto-DDL in any environment)
- Generates 1536-dim embeddings via Voyage AI `voyage-code-2` in batches,
  with exponential backoff retry on rate limits
- Stores vectors in pgvector with HNSW index for approximate nearest-neighbor search
- 11 tests: smoke test + 10 CodeChunker unit tests covering boundaries,
  filtering, error isolation

```bash
cd services/ingestion-service
./gradlew test
./gradlew bootRun   # :8082
```

## review-service

RAG-powered AI code reviewer using Claude.

- Consumes `pr.events` from Kafka (separate consumer group from ingestion)
- Embeds PR metadata as a **query** vector via Voyage AI (`input_type: query`
  vs `document` — improves retrieval relevance)
- Runs pgvector cosine similarity search to retrieve top-8 most relevant
  code chunks from the codebase
- Sends retrieved context + PR metadata to Claude with a structured prompt
- Persists reviews with status tracking (PENDING → COMPLETED / FAILED)
- Idempotent: duplicate `deliveryId` events are skipped to prevent
  double-billing the Claude API
- Multi-service Flyway coordination: `ignore-migration-patterns: "*:Missing"`
  allows review-service to coexist with V1/V2 migrations owned by ingestion-service

```bash
cd services/review-service
./gradlew bootRun   # :8083
```

## License

MIT
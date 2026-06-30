# PRPilot

AI-powered code review platform. Listens for GitHub pull request events,
analyzes the diff using RAG over the codebase, and posts review comments
back to the PR — built as a Kafka-backed microservices system.

[![webhook-service CI](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml/badge.svg)](https://github.com/singhbhupinder55/PRPilot-AI-Code-Review-Platform/actions/workflows/webhook-service-ci.yml)

## Status

🚧 Actively in development. Not yet deployed.

| Service | Status |
|---|---|
| `webhook-service` | ✅ Built — receives GitHub PR webhooks, verifies HMAC-SHA256 signatures, publishes events to Kafka |
| `ingestion-service` | ✅ Built — consumes PR events, clones repos, chunks source code, generates embeddings, persists to Postgres + pgvector |
| `review-service` | ⏳ Planned — RAG retrieval + Claude-powered PR review |
| `notification-service` | ⏳ Planned — posts review comments back to GitHub |
| `api-gateway` | ⏳ Planned |
| `frontend` | ⏳ Planned — React dashboard |

**Ingestion pipeline is fully working end-to-end**, verified against a real
GitHub repo: real chunks embedded via Voyage AI's `voyage-code-2` model, with
cosine similarity search via pgvector correctly clustering semantically
related files (no filename or metadata hints used).

## Architecture

```
GitHub PR event
      │
      ▼
webhook-service ──► Kafka (pr.events) ──► ingestion-service
                                                │
                                  clone repo → chunk code → embed (Voyage AI)
                                                │
                                                ▼
                                     Postgres + pgvector (code_chunks)
                                                │
                                                ▼
                                     review-service (Claude + RAG retrieval)
                                                │
                                                ▼
                                     notification-service ──► GitHub PR comment
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5
- **Messaging:** Apache Kafka
- **Data:** PostgreSQL + pgvector (HNSW index, cosine similarity), Redis
- **Schema migrations:** Flyway
- **Repo cloning:** JGit (pure-Java Git client, shallow clones)
- **Embeddings:** Voyage AI (`voyage-code-2`, 1536-dim, code-specialized)
- **AI review (planned):** Claude API (Anthropic)
- **Frontend (planned):** React, TypeScript
- **Infra:** Docker Compose (local), GitHub Actions (CI)

## Local development

Requires Docker, Java 21, and Gradle.

```bash
# Start local infrastructure (Postgres + pgvector, Redis, Kafka)
docker compose up -d

# Run a service, e.g. webhook-service
cd services/webhook-service
./gradlew bootRun
```

## webhook-service

Entry point for GitHub PR events.

- Validates incoming webhooks via HMAC-SHA256 signature verification
  (constant-time comparison, prevents forged requests)
- Publishes validated events to a Kafka topic (`pr.events`), keyed by
  repository for per-repo ordering
- Idempotent Kafka producer (`acks=all`) for delivery guarantees
- Tested with JUnit 5 (unit tests on signature verification) and
  Testcontainers (integration tests against a real Kafka broker)

```bash
cd services/webhook-service
./gradlew test       # run all tests
./gradlew bootRun     # start the service on :8081
```

## ingestion-service

Kafka consumer that turns a PR event into searchable, embedded code context.

- Consumes `pr.events` via `@KafkaListener`, using `ErrorHandlingDeserializer`
  so a malformed message can't take down the consumer or loop indefinitely
- Shallow-clones the target repo with JGit (depth=1, no full history)
- Chunks source files into fixed-size, line-based segments (baseline strategy;
  AST-aware chunking is a planned improvement)
- Persists chunks to Postgres via Spring Data JPA, schema managed by Flyway
  (`ddl-auto: validate` — no auto-DDL in any environment)
- Generates embeddings via Voyage AI's `voyage-code-2` model, batched for
  efficiency with exponential backoff retry on rate limits
- Stores 1536-dim vectors in a pgvector column with an HNSW index, enabling
  fast approximate nearest-neighbor similarity search

```bash
cd services/ingestion-service
./gradlew build       # run build + tests
./gradlew bootRun     # start the service on :8082
```

## License

MIT
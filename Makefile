SHELL := /bin/bash

.PHONY: help
help:
	@echo "EasyLuxury Makefile"
	@echo " make up           - start Postgres + MinIO"
	@echo " make be_run       - run backend (Spring Boot)"
	@echo " make be_test      - run backend tests (with Testcontainers)"
	@echo " make fe_dev       - run frontend (Next.js)"
	@echo " make fe_build     - build frontend"
	@echo " make openapi      - export OpenAPI JSON"
	@echo " make seed         - run seeders (dev only)"
	@echo " make fmt          - format BE/FE"
	@echo " make lint         - lint FE"

up:
	docker compose up -d

down:
	docker compose down -v

be_run:
	cd backend && ./mvnw spring-boot:run

be_test:
	cd backend && ./mvnw -q test

openapi:
	curl -s http://localhost:8080/v3/api-docs > backend/openapi.json

fe_dev:
	cd frontend && npm run dev

fe_build:
	cd frontend && npm ci && npm run build

seed:
	cd backend && ./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.arguments="--seed=true"

fmt:
	cd backend && ./mvnw spotless:apply || true
	cd frontend && npx prettier --write .

lint:
	cd frontend && npm run lint

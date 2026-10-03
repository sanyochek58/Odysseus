# Все команды проекта. Список: make help
SVC   ?=
CLASS ?=
TAG   ?= latest
BASE  ?= origin/main
Q      = scripts/quiet.sh
NEED   = test -n "$(SVC)" || { echo "SVC не задан, пример: make build SVC=task-service"; exit 1; }
DC     = docker compose -f infra/compose/docker-compose.yml

.PHONY: help orchestrator check-env build test test-class it run image up down ps logs wt-clean realm-check smoke run-bg stop changed-services

help: ## список команд
	@grep -hE '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | sed 's/:.*## / : /'

orchestrator: ## запуск сессии оркестратора (главный агент)
	@claude --agent orchestrator

check-env: ## проверка java и docker
	@java -version 2>&1 | head -1
	@docker ps > /dev/null && echo "docker ok"

build: ## сборка и юнит-тесты сервиса: make build SVC=task-service
	@$(NEED)
	@$(Q) "cd backend && ./gradlew -q --console=plain :services:$(SVC):build"

test: ## юнит-тесты сервиса
	@$(NEED)
	@$(Q) "cd backend && ./gradlew -q --console=plain :services:$(SVC):test"

test-class: ## один класс: make test-class SVC=task-service CLASS=*TaskServiceTest
	@$(NEED); test -n "$(CLASS)" || { echo "CLASS не задан"; exit 1; }
	@$(Q) "cd backend && ./gradlew -q --console=plain :services:$(SVC):test --tests '$(CLASS)'"

it: ## интеграционные тесты (Testcontainers), только tester или CI
	@$(NEED)
	@$(Q) "cd backend && ./gradlew -q --console=plain :services:$(SVC):integrationTest"

run: ## запуск сервиса локально (окружение из make up, секреты из .env)
	@$(NEED)
	@scripts/run-local.sh $(SVC)

run-bg: ## запуск сервиса в фоне и ожидание health: make run-bg SVC=workspace-service
	@$(NEED)
	@scripts/run-local.sh $(SVC) bg

stop: ## остановить сервис, запущенный через run-bg
	@$(NEED)
	@scripts/stop-local.sh $(SVC)

image: ## docker-образ сервиса (bootJar + Dockerfile сервиса): make image SVC=workspace-service
	@$(NEED)
	@test -f backend/services/$(SVC)/Dockerfile || { echo "нет Dockerfile у $(SVC)"; exit 1; }
	@$(Q) "cd backend && ./gradlew -q --console=plain :services:$(SVC):bootJar && cp \$$(ls services/$(SVC)/build/libs/*.jar | grep -v -- -plain.jar | head -n 1) services/$(SVC)/build/app.jar && docker build -q -t odysseus/$(SVC):$(TAG) services/$(SVC)"

changed-services: ## JSON-список изменённых сервисов относительно BASE: make changed-services BASE=origin/main
	@scripts/changed-services.sh $(BASE)

up: ## поднять postgres, kafka, keycloak; с PROFILE=app ещё и сервисы из образов (make image)
	@$(DC) $(if $(PROFILE),--profile $(PROFILE)) up -d

down: ## остановить окружение
	@$(DC) --profile app down

ps: ## состояние контейнеров
	@$(DC) ps

wt-clean: ## удалить чистые worktree агентов: make wt-clean [WT=имя]. Без WT не запускать при работающих агентах
	@scripts/worktree-clean.sh $(WT)

logs: ## хвост логов: make logs SVC=kafka
	@$(DC) logs --tail=100 $(SVC)

realm-check: ## проверка realm odysseus и токена test-user (после make up)
	@scripts/realm-check.sh

smoke: ## смоук запущенного сервиса: make smoke SVC=workspace-service
	@$(NEED)
	@scripts/smoke.sh $(SVC)

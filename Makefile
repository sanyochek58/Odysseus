# Все команды проекта. Список: make help
SVC   ?=
CLASS ?=
Q      = scripts/quiet.sh
NEED   = test -n "$(SVC)" || { echo "SVC не задан, пример: make build SVC=task-service"; exit 1; }
DC     = docker compose -f infra/compose/docker-compose.yml

.PHONY: help orchestrator check-env build test test-class it run image up down ps logs

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

run: ## запуск сервиса локально
	@$(NEED)
	@cd backend && ./gradlew :services:$(SVC):bootRun

image: ## docker-образ сервиса
	@$(NEED)
	@$(Q) "cd backend && ./gradlew -q :services:$(SVC):bootJar && docker build -q -t odysseus/$(SVC) services/$(SVC)"

up: ## поднять postgres, kafka, keycloak
	@$(DC) up -d

down: ## остановить окружение
	@$(DC) down

ps: ## состояние контейнеров
	@$(DC) ps

logs: ## хвост логов: make logs SVC=kafka
	@$(DC) logs --tail=100 $(SVC)

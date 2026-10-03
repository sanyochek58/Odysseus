# Все команды проекта. Список: make help
SVC   ?=
CLASS ?=
LIB   ?=
TAG   ?= latest
BASE  ?= origin/main
Q      = scripts/quiet.sh
NEEDLIB = test -n "$(LIB)" || { echo "LIB не задан: common-web, common-security или common-events"; exit 1; }; test -d backend/libs/$(LIB) || { echo "нет библиотеки $(LIB)"; exit 1; }
NEED   = test -n "$(SVC)" || { echo "SVC не задан, пример: make build SVC=task-service"; exit 1; }
DC     = docker compose -f infra/compose/docker-compose.yml

.PHONY: help orchestrator check-env build test test-class it run image up down ps logs wt-clean realm-check smoke run-bg stop changed-services test-lib build-lib changed-libs compose-config kube-check image-digest

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

test-lib: ## юнит-тесты библиотеки: make test-lib LIB=common-web
	@$(NEEDLIB)
	@if [ -f backend/libs/$(LIB)/build.gradle ]; then $(Q) "cd backend && ./gradlew -q --console=plain :libs:$(LIB):test"; else echo "libs/$(LIB) без build.gradle, пропуск"; fi

build-lib: ## сборка и тесты библиотеки: make build-lib LIB=common-web
	@$(NEEDLIB)
	@if [ -f backend/libs/$(LIB)/build.gradle ]; then $(Q) "cd backend && ./gradlew -q --console=plain :libs:$(LIB):build"; else echo "libs/$(LIB) без build.gradle, пропуск"; fi

changed-libs: ## JSON-список изменённых библиотек относительно BASE: make changed-libs BASE=origin/main
	@scripts/changed-libs.sh $(BASE)

compose-config: ## проверка валидности compose; без infra/compose/.env берётся .env.example (только для проверки)
	@f=infra/compose/.env; test -f $$f || f=infra/compose/.env.example; $(DC) --env-file $$f --profile app config -q && echo "compose ok ($$f)"

kube-check: ## проверка манифестов k8s без кластера (kubectl kustomize + dry-run=client)
	@command -v kubectl > /dev/null || { echo "kubectl не найден"; exit 1; }
	@kubectl kustomize infra/k8s/base > /dev/null && echo "kustomize ok"
	@kubectl kustomize infra/k8s/base | kubectl apply --dry-run=client --validate=false -f - > /dev/null && echo "dry-run ok"

image: ## docker-образ сервиса (bootJar + Dockerfile сервиса): make image SVC=workspace-service
	@$(NEED)
	@test -f backend/services/$(SVC)/Dockerfile || { echo "нет Dockerfile у $(SVC)"; exit 1; }
	@$(Q) "cd backend && ./gradlew -q --console=plain :services:$(SVC):bootJar && cp \$$(ls services/$(SVC)/build/libs/*.jar | grep -v -- -plain.jar | head -n 1) services/$(SVC)/build/app.jar && docker build -q -t odysseus/$(SVC):$(TAG) services/$(SVC)"

image-digest: ## дайджест базового образа для пина в Dockerfile: make image-digest IMG=eclipse-temurin:25-jdk
	@test -n "$(IMG)" || { echo "нужен IMG=<образ:тег>"; exit 1; }
	@docker buildx imagetools inspect $(IMG) --format '{{json .Manifest.Digest}}'

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

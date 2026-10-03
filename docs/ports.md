# Порты

Сервисы приложения
- workspace-service 8081
- project-service 8082
- task-service 8083
- calendar-service 8084

Инфраструктура (локально, compose)
- PostgreSQL 5432
- Kafka 9092
- Keycloak 8180
- nginx 80

Формат строки сервиса `- <имя> <порт>` читают скрипты (`scripts/lib-ports.sh`), не менять.
Новый порт сначала вписывается сюда, потом в compose.

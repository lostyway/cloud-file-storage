# Cloud File Storage

Облачное хранилище файлов на Java с использованием Spring Boot, PostgreSQL, Redis и MinIO, упакованное в Docker контейнеры и оркестрированное с помощью Docker Compose.

---

## Оглавление

- [Описание](#описание)  
- [Технологии](#технологии)  
- [Требования](#требования)  
- [Запуск](#запуск)  
- [Архитектура и компоненты](#архитектура-и-компоненты)  
- [Настройки окружения](#настройки-окружения)  
- [API](#api)  
- [Тестирование](#тестирование)  
- [Контейнеризация](#контейнеризация)  
- [Разработка и вклад](#разработка-и-вклад)  

---

## Описание

Проект реализует REST API для загрузки, хранения и получения файлов.  
Хранение реализовано с использованием MinIO (совместимый с S3 API), база данных — PostgreSQL, кеш и сессии — Redis.  
Предусмотрена интеграция с фронтендом (см. сервис `frontend` в `docker-compose.yaml`).

---

## Технологии

- Java 17  
- Spring Boot 3.5.4  
- Spring Data JPA (PostgreSQL)  
- Spring Data Redis + Spring Session  
- MinIO (S3-совместимое хранилище)  
- Spring Security  
- Liquibase (миграции БД)  
- MapStruct (маппинг DTO)  
- Docker, Docker Compose  
- Maven  
- Testcontainers (для интеграционных тестов)

---

## Требования

- Docker
- Docker Compose
- JDK 17 (если запускать локально без Docker)
- Maven 3.8+ (для сборки)

---

## Запуск

### 1. Запуск через Docker Compose (рекомендуется)

```bash
docker-compose up --build
```
Это поднимет все сервисы:

    cloudfilestorage (Java-приложение) на порту 8081

    postgres (PostgreSQL) на порту 5438

    redis (Redis) на порту 6379

    minio (MinIO) на порту 9000 (API) и 9001 (консоль)

    frontend (React фронтенд) на порту 3000

2. Запуск локально без Docker

./mvnw clean package
java -jar target/cloud-file-storage-0.0.1-SNAPSHOT.jar

При этом PostgreSQL, Redis и MinIO должны быть запущены отдельно и доступны по конфигурации (например, через docker-compose, описанный выше).
Архитектура и компоненты

    cloudfilestorage — Java Spring Boot сервис.

        REST API для работы с файлами.

        Сессии и кеш через Redis.

        Метаданные в PostgreSQL (миграции через Liquibase).

        Файлы хранятся в MinIO.

    PostgreSQL — реляционная БД.

    Redis — кеширование, сессии.

    MinIO — объектное хранилище файлов, совместимое с Amazon S3 API.

    frontend — React приложение, интегрированное с API.

Настройки окружения

В docker-compose.yaml параметры подключения передаются через переменные окружения в сервис cloudfilestorage:

environment:
  - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/filestorage
  - SPRING_DATASOURCE_USERNAME=postgres
  - SPRING_DATASOURCE_PASSWORD=password
  - SPRING_REDIS_HOST=redis
  - SPRING_REDIS_PORT=6379

Тестирование

Проект содержит модульные и интеграционные тесты с использованием:

    Spring Boot Test

    Testcontainers (PostgreSQL, Redis)

    Spring Security Test

Запуск тестов:

./mvnw test

Контейнеризация

    Dockerfile для сборки образа Java приложения.

    Docker Compose файл для локального развертывания всех необходимых сервисов.

    Тома (volumes) для хранения данных PostgreSQL, Redis, MinIO и приложения.

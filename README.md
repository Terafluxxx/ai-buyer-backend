# AI Buyer Backend

Java 21 / Spring Boot 3.5.7 / PostgreSQL / Maven. Модульный монолит.

## Реализовано

- Расчётное ядро рекомендаций по разделу 5 API.
- `POST /api/v1/auth/register`: атомарное создание компании и пользователя.
- `POST /api/v1/auth/login`: BCrypt, JWT HS256 на 3600 секунд.
- `GET /api/v1/auth/me`, `GET /api/v1/companies/current`.
- `POST /api/v1/auth/logout`: постоянный отзыв текущей сессии в БД.
- Flyway, единые ошибки, строгие JSON-поля, лимит попыток на IP в БД.

Остальные REST-модули пока не реализованы. Не считать этот этап полной реализацией API.

## Запуск

Создайте `.env` (не коммитить):

```dotenv
DATABASE_PASSWORD=<случайный-пароль>
JWT_SECRET=<base64-от-32-случайных-байт>
```

JWT_SECRET можно получить командой `openssl rand -base64 32`.

```sh
docker compose up --build
```

API: `http://localhost:8080/api/v1`. PostgreSQL хранится в постоянном volume.
Production требует TLS на reverse proxy. Все инстансы должны использовать одну БД и один JWT_SECRET.
Лимит входа: AUTH_ATTEMPT_LIMIT=20 за AUTH_WINDOW_SECONDS=60 секунд на реальный IP соединения.
Заголовки X-Forwarded-For не доверяются: за прокси IP общий; настройте лимиты с учётом развёртывания.

## Проверка

```sh
mvn verify
```

Интеграционные HTTP-тесты используют H2 в PostgreSQL mode. Они не заменяют проверку на настоящем PostgreSQL.
Профиль `local-jre-check` позволяет компилировать ECJ и выполнять тесты на Java 17 в ограниченной среде;
целевая сборка и CI используют Java 21.

## GitHub Flow

Ветка от main → небольшие коммиты → PR → зелёные проверки → review → merge.
Никакие реальные даты коммитов и PR не изменяются.

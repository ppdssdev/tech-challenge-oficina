.PHONY: up down logs test verify

up:
	docker compose up --build

down:
	docker compose down

logs:
	docker compose logs -f api

test:
	mvn test

verify:
	mvn clean verify

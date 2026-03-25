.PHONY: dev-up dev-down build-all lint test clean

dev-up:
	docker compose up -d

dev-down:
	docker compose down

build-all:
	./gradlew assemble
	pnpm -r build

lint:
	./gradlew detekt ktlintCheck
	pnpm -r lint --max-warnings 0
	pnpm format:check

test:
	./gradlew test
	pnpm -r test

test-integration:
	./gradlew integrationTest

test-architecture:
	./gradlew test --tests "*ArchitectureTest*"
	pnpm -r depcruise

clean:
	./gradlew clean
	pnpm -r exec rm -rf dist node_modules

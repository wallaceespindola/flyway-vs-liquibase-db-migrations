# Flyway vs Liquibase — DB Migrations
# Author: Wallace Espindola <wallace.espindola@gmail.com>

MVN  ?= mvn
JAR  := target/flyway-vs-liquibase-db-migrations.jar
PORT ?= 8080

.DEFAULT_GOAL := help
.PHONY: help build test verify coverage run start stop restart clean clean-db package docker-build docker-up docker-down api diagrams lint links slides docs-check

help: ## Show this help
	@echo "Flyway vs Liquibase — available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

build: ## Compile the application
	$(MVN) -B compile

test: ## Run the test suite
	$(MVN) -B test

verify: ## Run tests and enforce the JaCoCo coverage gate
	$(MVN) -B verify

coverage: verify ## Run the coverage gate and print where the report landed
	@echo "Coverage report: target/site/jacoco/index.html"

package: ## Build the executable jar
	$(MVN) -B -DskipTests package

run: ## Run in the foreground with the Spring Boot plugin
	$(MVN) -B spring-boot:run

start: ## Build and start in the background, then wait for health
	./start.sh

stop: ## Stop the background application
	./stop.sh

restart: stop start ## Restart the background application

clean-db: ## Delete both H2 databases so migrations re-run from scratch
	rm -rf data

clean: ## Remove build output, run state and both H2 databases
	$(MVN) -B clean
	rm -rf data .run

docker-build: ## Build the Docker image
	docker build -t flyway-vs-liquibase-db-migrations:1.0.0 .

docker-up: ## Start via Docker Compose
	docker compose up --build -d

docker-down: ## Stop Docker Compose
	docker compose down

api: ## Smoke-test the running API and print the headline result
	@curl -fsS http://localhost:$(PORT)/api/v1/comparison | \
		python3 -c "import json,sys; d=json.load(sys.stdin)['data']; \
		print('schemasEquivalent:', d['schemasEquivalent']); \
		print('differences:', d['schemaDifferences'] or 'none'); \
		print('flyway applied:', d['flyway']['appliedCount']); \
		print('liquibase applied:', d['liquibase']['appliedCount'])"

# ---------- Docs: diagrams, Markdown lint, links, slides ----------
PUML   := $(shell find docs -name '*.puml')
MMD    := $(shell find docs -name '*.mmd')
DECK   := docs/presentations/slides.md
CHROME ?= /Applications/Google Chrome.app/Contents/MacOS/Google Chrome

diagrams: ## Render every PlantUML and Mermaid source to PNG next to it
	@for f in $(PUML); do plantuml -tpng "$$f"; done
	@printf '{"executablePath":"%s"}' "$(CHROME)" > .puppeteer.json
	@for f in $(MMD); do npx -y @mermaid-js/mermaid-cli -q -p .puppeteer.json -i "$$f" -o "$${f%.mmd}.png" -b white -s 2; done
	@rm -f .puppeteer.json

lint: ## Lint all Markdown
	npx -y markdownlint-cli2 "**/*.md" "#node_modules" "#target"

links: ## Check relative links between Markdown files (offline)
	lychee --offline --no-progress --exclude-path target "**/*.md"

slides: ## Export the Marp deck to PDF (Speaker Deck) and rebuild the editable PPTX
	CHROME_PATH="$(CHROME)" npx -y @marp-team/marp-cli $(DECK) --pdf --allow-local-files \
		-o docs/presentations/flyway-vs-liquibase-db-migrations.pdf </dev/null
	uv run --with python-pptx --with pillow docs/presentations/build_pptx.py

docs-check: lint links ## Run all docs checks

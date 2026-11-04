DOCKER=docker
NETWORK_NAME=mfo-network
SQL_PATH=src/main/resources/postgres
IMAGE_NAME=musicfeston/kitchen-management-server
IMAGE_TAG=latest
RELEASE_DIR=releases
VERSION=$(shell grep '^  version:' src/main/resources/application.yaml | awk '{print $$2}')

# Remove network if it exists
.PHONY: clean-network
clean-network:
	@if $(DOCKER) network inspect $(NETWORK_NAME) >/dev/null 2>&1; then \
		echo "Network $(NETWORK_NAME) exists, removing..."; \
		$(DOCKER) network rm $(NETWORK_NAME); \
	fi

# Check if .env file exists, if not, clone .env.sample
.PHONY: check-env
check-env:
	@if [ ! -f .env.sample ]; then \
		echo ".env.sample not found"; \
		exit 1; \
	fi
	@if [ ! -f .env ]; then \
		cp .env.sample .env; \
		echo ".env created from .env.sample"; \
	fi

# Build the application Docker image
.PHONY: build-image
build-image:
	$(DOCKER) build -t $(IMAGE_NAME):$(IMAGE_TAG) .

# Tag the built image with the application version and export it as a
# compressed, checksummed tar archive under releases/, ready to be
# transferred with `docker load` on another server.
.PHONY: release-image
release-image: build-image
	@if [ -z "$(VERSION)" ]; then echo "Could not determine VERSION from application.yaml"; exit 1; fi
	$(DOCKER) tag $(IMAGE_NAME):$(IMAGE_TAG) $(IMAGE_NAME):$(VERSION)
	mkdir -p $(RELEASE_DIR)
	$(DOCKER) save $(IMAGE_NAME):$(VERSION) | gzip > $(RELEASE_DIR)/kitchen-management-server_$(VERSION).tar.gz
	shasum -a 256 $(RELEASE_DIR)/kitchen-management-server_$(VERSION).tar.gz > $(RELEASE_DIR)/kitchen-management-server_$(VERSION).tar.gz.sha256
	@echo "Release archive: $(RELEASE_DIR)/kitchen-management-server_$(VERSION).tar.gz"

# Run postgres setup migration against the external Postgres instance
# configured in .env (requires the psql client installed locally)
.PHONY: migrate
migrate: check-env
	@set -a; . ./.env; set +a; \
	echo "Running Postgres migrations against $$POSTGRES_HOST:$$POSTGRES_PORT/$$POSTGRES_DB..."; \
	for file in $(SQL_PATH)/*.sql; do \
		echo "Executing $$file..."; \
		PGPASSWORD=$$POSTGRES_PASSWORD psql -h $$POSTGRES_HOST -p $$POSTGRES_PORT -U $$POSTGRES_USER -d $$POSTGRES_DB -f $$file; \
	done

# Run base infrastructure for backend application (app + MongoDB).
# PostgreSQL is an external dependency: it is not started here, only
# referenced via the POSTGRES_* variables in .env.
.PHONY: up-infra
up-infra: clean-network check-env
	$(DOCKER) network create $(NETWORK_NAME)
	$(DOCKER) compose --env-file .env -f ./docker/docker-compose.yml up -d --build

# Shut down base infrastructure, docker volumes are preserved
.PHONY: down
down:
	$(DOCKER) compose --env-file .env -f ./docker/docker-compose.yml down
	$(DOCKER) network remove $(NETWORK_NAME)

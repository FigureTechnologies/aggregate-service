.PHONY: build-dist clean clean-test localstack-start localstack-stop localstack-status run-local test

# Make all environment variables available to child processes
.EXPORT_ALL_VARIABLES:

NAME            := aggregate-service
BUILD           := $(PWD)/build
GRADLEW         := ./gradlew
LOCALSTACK_PORT := 4566

all: run-local

clean:
	$(GRADLEW) clean

clean-test:
	$(GRADLEW) cleanTest

localstack-start:
	@echo "- Starting localstack"
	@docker-compose -f docker-compose.local.yml up --abort-on-container-exit

localstack-stop:
	@echo "- Stopping localstack"
	@docker-compose -f docker-compose.local.yml down --remove-orphans --volumes

localstack-status:
	@curl -s http://localhost:$(LOCALSTACK_PORT)/health | jq -e .services

build-dist:
	$(GRADLEW) installDist

run-local:  build-dist
	AWS_REGION=us-east-1 ENVIRONMENT=local $(BUILD)/install/$(NAME)/bin/$(NAME) $(ARGS)

test: clean-test
	$(GRADLEW) test -i

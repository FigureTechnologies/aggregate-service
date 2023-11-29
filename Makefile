.PHONY: build-dist clean clean-test run-local test

# Make all environment variables available to child processes
.EXPORT_ALL_VARIABLES:

NAME            := aggregate-service
BUILD           := $(PWD)/build
GRADLEW         := ./gradlew

all: run-local

clean:
	$(GRADLEW) clean

clean-test:
	$(GRADLEW) cleanTest

build-dist:
	$(GRADLEW) installDist

migrate:
	$(GRADLEW) flywayMigrate

run-local:  build-dist migrate
	ENVIRONMENT=local $(BUILD)/install/$(NAME)/bin/$(NAME) --restart $(ARGS)

test: clean-test
	$(GRADLEW) test -i


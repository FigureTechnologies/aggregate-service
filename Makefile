.PHONY: build clean cleanTest run test

NAME    := aggregate-service
BUILD   := $(PWD)/build
GRADLEW := ./gradlew

all: run

clean:
	$(GRADLEW) clean

cleanTest:
	$(GRADLEW) cleanTest

build:
	$(GRADLEW) installDist

run: build
	$(BUILD)/install/$(NAME)/bin/$(NAME) $(ARGS)

test: cleanTest
	$(GRADLEW) test

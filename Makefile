.PHONY: clean build run

NAME := aggregate-service
BUILD := $(PWD)/build

all: run

clean:
	./gradlew clean

build:
	./gradlew installDist

run: build
	$(BUILD)/install/$(NAME)/bin/$(NAME) $(ARGS)

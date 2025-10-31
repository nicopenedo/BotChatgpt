.PHONY: build run test format

build:
mvn -B -ntp clean package

run:
mvn spring-boot:run

test:
mvn test

format:
mvn spotless:apply

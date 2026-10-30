#!/bin/bash

podman start aprovia-postgres ollama

set -a
source .env
set +a

./mvnw spring-boot:run
#!/bin/bash

cd "$(dirname "$0")"

docker compose exec postgres psql -U app -d appdb -c "SELECT now();"
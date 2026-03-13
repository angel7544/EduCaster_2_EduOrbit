#!/usr/bin/env bash
set -euo pipefail
mvn clean install
mvn javafx:run
#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Change to the script directory
cd "$SCRIPT_DIR"

# Run the fat jar with the system property mongooseServer.config.file set to myConfig.yml
java -DmongooseServer.config.file=myConfig.yml -jar ../target/five-minute-yaml-tutorial-1.0-SNAPSHOT-jar-with-dependencies.jar

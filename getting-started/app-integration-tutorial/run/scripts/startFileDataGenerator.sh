#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd "$SCRIPT_DIR"
# Check if jar file exists, if not build it using maven
if [ ! -f "../../target/app-integration-tutorial.jar" ]; then
    cd ../../../../
    ./mvnw -pl getting-started/app-integration-tutorial/pom.xml package
fi

# Change to the script directory
cd "$SCRIPT_DIR"/../

# Create data-out directory if it doesn't exist
if [ ! -d "data-out" ]; then
    mkdir data-out
fi

if [ ! -d "data-in" ]; then
    mkdir data-in
fi

# Run the fat jar with the system property mongooseServer.config.file set to appConfig.yml
java -DmongooseServer.config.file=app-config/fileDataGenerator_config.yml --add-opens java.base/jdk.internal.misc=ALL-UNNAMED -Djava.util.logging.config.file=logging.properties -jar ../target/app-integration-tutorial.jar

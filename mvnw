#!/bin/sh
# Apache Maven Wrapper startup script
MVNW_REPOURL="https://repo.maven.apache.org/maven2"
WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

if [ -z "$MVNW_VERBOSE" ]; then
  MVNW_VERBOSE=false
fi

# download wrapper jar if missing
if [ ! -f "$WRAPPER_JAR" ]; then
  if [ "$MVNW_VERBOSE" = true ]; then
    echo "- Downloading Maven Wrapper jar"
  fi
  PROP_URL=$(grep -E "^wrapperUrl=" "$WRAPPER_PROPERTIES" | sed -e 's#wrapperUrl=##g')
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$WRAPPER_JAR" "$PROP_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -q -O "$WRAPPER_JAR" "$PROP_URL"
  else
    echo "Error: Please install curl or wget to download the Maven Wrapper." >&2
    exit 1
  fi
fi

# exec maven wrapper
JAVA_EXE="java"
exec "$JAVA_EXE" -Dmaven.multiModuleProjectDirectory="$PWD" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"

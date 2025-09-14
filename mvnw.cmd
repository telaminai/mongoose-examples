@ECHO OFF
setlocal
set WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
set WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_JAR%" (
  for /f "tokens=2 delims==" %%a in ('findstr /R "^wrapperUrl=" "%WRAPPER_PROPERTIES%"') do set WRAPPER_URL=%%a
  powershell -Command "[Net.ServicePointManager]::SecurityProtocol = 'Tls12'; Invoke-WebRequest -UseBasicParsing -Uri %WRAPPER_URL% -OutFile %WRAPPER_JAR%" || (
    echo Error: Failed to download Maven Wrapper jar
    exit /b 1
  )
)

set JAVA_EXE=java
"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*

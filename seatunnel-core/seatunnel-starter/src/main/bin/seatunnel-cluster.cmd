@echo off
REM Licensed to the Apache Software Foundation (ASF) under one or more
REM contributor license agreements.  See the NOTICE file distributed with
REM this work for additional information regarding copyright ownership.
REM The ASF licenses this file to You under the Apache License, Version 2.0
REM (the "License"); you may not use this file except in compliance with
REM the License.  You may obtain a copy of the License at
REM
REM    http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

setlocal enabledelayedexpansion

REM resolve links - %0 may be a softlink
for %%F in ("%~f0") do (
    set "PRG=%%~fF"
    set "PRG_DIR=%%~dpF"
    set "APP_DIR=%%~dpF.."
)

set "CONF_DIR=%APP_DIR%\config"
set "APP_JAR=%APP_DIR%\starter\seatunnel-starter.jar"
set "APP_MAIN=org.apache.seatunnel.core.starter.seatunnel.SeaTunnelServer"
set "OUT=%APP_DIR%\logs\seatunnel-server.out"

if not defined SEATUNNEL_HOME set "SEATUNNEL_HOME=%APP_DIR%"
if exist "%CONF_DIR%\seatunnel-env.cmd" call "%CONF_DIR%\seatunnel-env.cmd"
if not defined SEATUNNEL_HOME set "SEATUNNEL_HOME=%APP_DIR%"

set "HELP=false"
set "args="

for %%I in (%*) do (
    set "args=!args! %%I"
    if "%%I"=="-d" set "DAEMON=true"
    if "%%I"=="--daemon" set "DAEMON=true"
    if "%%I"=="-h" set "HELP=true"
    if "%%I"=="--help" set "HELP=true"
)

REM SeaTunnel Engine Config
set "HAZELCAST_CONFIG=%CONF_DIR%\hazelcast.yaml"
set "SEATUNNEL_CONFIG=%CONF_DIR%\seatunnel.yaml"
set "JAVA_OPTS=%JvmOption%"
call :expand_env_vars JAVA_OPTS

for %%I in (%*) do (
    set "arg=%%I"
    if "!arg:~0,10!"=="JvmOption=" (
        set "JVM_OPTION=!arg:~10!"
        call :expand_env_vars JVM_OPTION
        set "JAVA_OPTS=!JAVA_OPTS! !JVM_OPTION!"
    )
)

set "JAVA_OPTS=%JAVA_OPTS% -Dseatunnel.config=%SEATUNNEL_CONFIG%"
set "JAVA_OPTS=%JAVA_OPTS% -Dhazelcast.config=%HAZELCAST_CONFIG%"
set "JAVA_OPTS=%JAVA_OPTS% -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector"
set "JAVA_OPTS=%JAVA_OPTS% -Dlog4j2.isThreadContextMapInheritable=true -DAsyncLogger.ThreadNameStrategy=UNCACHED"

REM Server Debug Config
REM Usage instructions:
REM If you need to debug your code in cluster mode, please enable this configuration option and listen to the specified
REM port in your IDE. After that, you can happily debug your code.
REM set "JAVA_OPTS=%JAVA_OPTS% -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=5001,suspend=y"

if exist "%CONF_DIR%\log4j2.properties" (
    set "JAVA_OPTS=%JAVA_OPTS% -Dlog4j2.configurationFile=%CONF_DIR%\log4j2.properties"
    set "JAVA_OPTS=%JAVA_OPTS% -Dseatunnel.logs.path=%APP_DIR%\logs"
    set "JAVA_OPTS=%JAVA_OPTS% -Dseatunnel.logs.file_name=seatunnel-engine-server"
)

set "CLASS_PATH=%CONF_DIR%;%APP_DIR%\lib\*;%APP_JAR%"

REM Detect JDK major version
for /f "tokens=3" %%a in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION_STRING=%%a"
)
set "JAVA_VERSION_STRING=%JAVA_VERSION_STRING:"=%"
for /f "tokens=1 delims=." %%a in ("%JAVA_VERSION_STRING%") do (
    set "JAVA_MAJOR_VERSION=%%a"
)
REM For JDK 8, version is like 1.8.0_xxx, so major is 1
if "%JAVA_MAJOR_VERSION%"=="1" (
    for /f "tokens=2 delims=." %%a in ("%JAVA_VERSION_STRING%") do (
        set "JAVA_MAJOR_VERSION=%%a"
    )
)

for /f "usebackq delims=" %%I in ("%APP_DIR%\config\jvm_options") do (
    set "line=%%I"
    if not "!line:~0,1!"=="#" if "!line!" NEQ "" (
        REM Check for version-specific prefixes
        if "!line:~0,2!"=="8:" (
            REM JDK 8 specific option
            if "!JAVA_MAJOR_VERSION!"=="8" (
                set "line=!line:~2!"
                call :expand_env_vars line
                set "JAVA_OPTS=!JAVA_OPTS! !line!"
            )
        ) else if "!line:~0,3!"=="11:" (
            REM JDK 11+ specific option
            if !JAVA_MAJOR_VERSION! GEQ 11 (
                set "line=!line:~3!"
                call :expand_env_vars line
                set "JAVA_OPTS=!JAVA_OPTS! !line!"
            )
        ) else (
            call :expand_env_vars line
            set "JAVA_OPTS=!JAVA_OPTS! !line!"
        )
    )
)

REM Ensure HeapDumpPath directory exists to avoid OOM dump failures.
set "HEAP_DUMP_PATH="
for %%I in (!JAVA_OPTS!) do (
    set "opt=%%I"
    if "!opt:~0,18!"=="-XX:HeapDumpPath=" (
        set "HEAP_DUMP_PATH=!opt:~18!"
    )
)
if defined HEAP_DUMP_PATH (
    set "HEAP_DUMP_PATH=!HEAP_DUMP_PATH:/=\!"
    set "HEAP_DUMP_DIR=!HEAP_DUMP_PATH!"
    if "!HEAP_DUMP_PATH:~-1!"=="/" set "HEAP_DUMP_DIR=!HEAP_DUMP_PATH:~0,-1!"
    if "!HEAP_DUMP_PATH:~-1!"=="\\" set "HEAP_DUMP_DIR=!HEAP_DUMP_PATH:~0,-1!"
    if /I "!HEAP_DUMP_PATH:~-6!"==".hprof" (
        for %%D in ("!HEAP_DUMP_PATH!") do set "HEAP_DUMP_DIR=%%~dpD"
    ) else if /I "!HEAP_DUMP_PATH:~-4!"==".phd" (
        for %%D in ("!HEAP_DUMP_PATH!") do set "HEAP_DUMP_DIR=%%~dpD"
    ) else (
        for %%D in ("!HEAP_DUMP_PATH!") do (
            if not "%%~xD"=="" set "HEAP_DUMP_DIR=%%~dpD"
        )
    )
    if defined HEAP_DUMP_DIR if not exist "!HEAP_DUMP_DIR!" mkdir "!HEAP_DUMP_DIR!"
)

REM Ensure Xloggc directory exists to avoid GC logging failures.
REM Support both JDK 8 (-Xloggc:) and JDK 11+ (-Xlog:gc*:file=) formats
set "GC_LOG_PATH="
for %%I in (!JAVA_OPTS!) do (
    set "opt=%%I"
    if "!opt:~0,8!"=="-Xloggc:" (
        set "GC_LOG_PATH=!opt:~8!"
    ) else if "!opt:~0,5!"=="-Xlog" (
        REM Extract file path from -Xlog:gc*:file=/path/to/gc.log:...
        set "xlog_after_file=!opt:*:file=!"
        if "!xlog_after_file:~1,1!"==":" (
            set "xlog_drive=!xlog_after_file:~0,1!"
            set "xlog_rest=!xlog_after_file:~2!"
            for /f "tokens=1 delims=:" %%P in ("!xlog_rest!") do set "GC_LOG_PATH=!xlog_drive!:%%P"
        ) else (
            for /f "tokens=1 delims=:" %%P in ("!xlog_after_file!") do set "GC_LOG_PATH=%%P"
        )
    )
)
if defined GC_LOG_PATH (
    set "GC_LOG_PATH=!GC_LOG_PATH:/=\!"
    for %%D in ("!GC_LOG_PATH!") do set "GC_LOG_DIR=%%~dpD"
    if defined GC_LOG_DIR if not exist "!GC_LOG_DIR!" mkdir "!GC_LOG_DIR!"
)

if "%HELP%"=="false" (
    if not exist "%APP_DIR%\logs\" mkdir "%APP_DIR%\logs"
    start "SeaTunnel Server" java %JAVA_OPTS% -cp "%CLASS_PATH%" %APP_MAIN% %args% > "%OUT%" 2>&1
) else (
    java %JAVA_OPTS% -cp "%CLASS_PATH%" %APP_MAIN% %args%
)

endlocal
goto :eof

:expand_env_vars
set "value=!%~1!"
if not defined value goto :eof
set "value=!value:${SEATUNNEL_HOME}=!SEATUNNEL_HOME!!"
set "value=!value:$SEATUNNEL_HOME=!SEATUNNEL_HOME!!"
set "%~1=!value!"
goto :eof
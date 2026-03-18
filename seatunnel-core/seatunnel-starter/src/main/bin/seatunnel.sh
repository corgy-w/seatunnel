#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -eu
# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ] ; do
  # shellcheck disable=SC2006
  ls=`ls -ld "$PRG"`
  # shellcheck disable=SC2006
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    # shellcheck disable=SC2006
    PRG=`dirname "$PRG"`/"$link"
  fi
done

PRG_DIR=`dirname "$PRG"`
APP_DIR=`cd "$PRG_DIR/.." >/dev/null; pwd`
CONF_DIR=${APP_DIR}/config
APP_JAR=${APP_DIR}/starter/seatunnel-starter.jar
APP_MAIN="org.apache.seatunnel.core.starter.seatunnel.SeaTunnelClient"

if [ -f "${CONF_DIR}/seatunnel-env.sh" ]; then
    . "${CONF_DIR}/seatunnel-env.sh"
fi

SEATUNNEL_HOME="${SEATUNNEL_HOME:-$APP_DIR}"
export SEATUNNEL_HOME

SEATUNNEL_LOG_DIR=${SEATUNNEL_LOG_DIR:-${APP_DIR}/logs}

if [ $# == 0 ]; then
    set -- -h
fi

args=("$@")
args_str=" $* "

set +u
# SeaTunnel Engine Config
if [ -z $HAZELCAST_CLIENT_CONFIG ]; then
    HAZELCAST_CLIENT_CONFIG=${CONF_DIR}/hazelcast-client.yaml
fi

if [ -z $HAZELCAST_CONFIG ]; then
  HAZELCAST_CONFIG=${CONF_DIR}/hazelcast.yaml
fi

if [ -z $SEATUNNEL_CONFIG ]; then
    SEATUNNEL_CONFIG=${CONF_DIR}/seatunnel.yaml
fi

if test ${JvmOption} ;then
    JAVA_OPTS="${JAVA_OPTS} ${JvmOption}"
fi

for i in "$@"
do
  if [[ "${i}" == *"JvmOption"* ]]; then
    JVM_OPTION="${i}"
    JAVA_OPTS="${JAVA_OPTS} ${JVM_OPTION#*=}"
    break
  fi
done

JAVA_OPTS="${JAVA_OPTS} -Dhazelcast.client.config=${HAZELCAST_CLIENT_CONFIG}"
JAVA_OPTS="${JAVA_OPTS} -Dseatunnel.config=${SEATUNNEL_CONFIG}"
JAVA_OPTS="${JAVA_OPTS} -Dhazelcast.config=${HAZELCAST_CONFIG}"

# Client Debug Config
# Usage instructions:
# If you need to debug your code in cluster mode, please enable this configuration option and listen to the specified
# port in your IDE. After that, you can happily debug your code.
# JAVA_OPTS="${JAVA_OPTS} -Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=5000,suspend=y"

# Log4j2 Config
JAVA_OPTS="${JAVA_OPTS} -Dlog4j2.isThreadContextMapInheritable=true"
if [ -e "${CONF_DIR}/log4j2_client.properties" ]; then
  JAVA_OPTS="${JAVA_OPTS} -Dlog4j2.configurationFile=${CONF_DIR}/log4j2_client.properties"
  JAVA_OPTS="${JAVA_OPTS} -Dseatunnel.logs.path=${SEATUNNEL_LOG_DIR}"
  if [[ $args_str == *" -m local "* || $args_str == *" --master local "* || $args_str == *" -e local "* || $args_str == *" --deploy-mode local "* ]]; then
    ntime=$(echo `date "+%N"`|sed -r 's/^0+//')
    JAVA_OPTS="${JAVA_OPTS} -Dseatunnel.logs.file_name=seatunnel-starter-client-$((`date '+%s'`*1000+$ntime/1000000))"
  else
      JAVA_OPTS="${JAVA_OPTS} -Dseatunnel.logs.file_name=seatunnel-starter-client"
  fi
fi

CLASS_PATH=${CONF_DIR}:${APP_DIR}/lib/*:${APP_JAR}

# Detect JDK major version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | sed -E 's/.*"([0-9]+)\..*/\1/')
if [[ "$JAVA_VERSION" == "1" ]]; then
    # For JDK 8, version string is like "1.8.0_xxx"
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | sed -E 's/.*"1\.([0-9]+)\..*/\1/')
fi

while read -r line
do
    if [[ ! $line == \#* ]] && [ -n "$line" ]; then
        # Check for version-specific prefixes (8: or 11:)
        if [[ "$line" == 8:* ]]; then
            # JDK 8 specific option
            if [[ "$JAVA_VERSION" == "8" ]]; then
                line="${line#8:}"
            else
                continue
            fi
        elif [[ "$line" == 11:* ]]; then
            # JDK 11+ specific option
            if [[ "$JAVA_VERSION" -ge 11 ]]; then
                line="${line#11:}"
            else
                continue
            fi
        fi
        escaped_line=${line//\\/\\\\}
        escaped_line=${escaped_line//\"/\\\"}
        escaped_line=${escaped_line//\$\(\(/\\\$\(\(}
        escaped_line=${escaped_line//\$\(/\\\$\(}
        escaped_line=${escaped_line//\`/\\\`}
        eval "expanded_line=\"$escaped_line\""
        JAVA_OPTS="$JAVA_OPTS $expanded_line"
    fi
done < "${APP_DIR}/config/jvm_client_options"

# Ensure HeapDumpPath directory exists to avoid OOM dump failures.
HEAP_DUMP_PATH=""
for opt in $JAVA_OPTS; do
  if [[ "$opt" == -XX:HeapDumpPath=* ]]; then
    HEAP_DUMP_PATH="${opt#-XX:HeapDumpPath=}"
  fi
done
if [[ -n "$HEAP_DUMP_PATH" ]]; then
  HEAP_DUMP_DIR="$HEAP_DUMP_PATH"
  if [[ "$HEAP_DUMP_PATH" == */ ]]; then
    HEAP_DUMP_DIR="${HEAP_DUMP_PATH%/}"
  elif [[ "$HEAP_DUMP_PATH" == *.hprof || "$HEAP_DUMP_PATH" == *.phd ]]; then
    HEAP_DUMP_DIR="$(dirname "$HEAP_DUMP_PATH")"
  elif [[ -e "$HEAP_DUMP_PATH" && ! -d "$HEAP_DUMP_PATH" ]]; then
    HEAP_DUMP_DIR="$(dirname "$HEAP_DUMP_PATH")"
  elif [[ "${HEAP_DUMP_PATH##*/}" == *.* ]]; then
    HEAP_DUMP_DIR="$(dirname "$HEAP_DUMP_PATH")"
  fi
  if [[ -n "$HEAP_DUMP_DIR" && ! -d "$HEAP_DUMP_DIR" ]]; then
    mkdir -p "$HEAP_DUMP_DIR"
  fi
fi

# Ensure Xloggc directory exists to avoid GC logging failures.
# Support both JDK 8 (-Xloggc:) and JDK 11+ (-Xlog:gc*:file=) formats
GC_LOG_PATH=""
for opt in $JAVA_OPTS; do
  if [[ "$opt" == -Xloggc:* ]]; then
    GC_LOG_PATH="${opt#-Xloggc:}"
  elif [[ "$opt" == -Xlog:* ]] && [[ "$opt" == *:file=* ]]; then
    # Extract file path from -Xlog:gc*:file=/path/to/gc.log:...
    GC_LOG_PATH=$(echo "$opt" | sed -E 's/.*:file=([^:]+).*/\1/')
  fi
done
if [[ -n "$GC_LOG_PATH" ]]; then
  GC_LOG_DIR="$(dirname "$GC_LOG_PATH")"
  if [[ -n "$GC_LOG_DIR" && ! -d "$GC_LOG_DIR" ]]; then
    mkdir -p "$GC_LOG_DIR"
  fi
fi

java ${JAVA_OPTS} -cp ${CLASS_PATH} ${APP_MAIN} "${args[@]}"

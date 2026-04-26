#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIN_ROOT="$(wslpath -w "$ROOT_DIR")"

MUSIO_MAVEN_HOME_WIN="${MUSIO_MAVEN_HOME_WIN:-D:\\Env\\Maven\\apache-maven-3.6.1-bin\\apache-maven-3.6.1}"
MUSIO_JAVA_EXE_WIN="${MUSIO_JAVA_EXE_WIN:-D:\\Env\\JDK21\\bin\\java.exe}"
CLASSWORLDS_JAR="${MUSIO_MAVEN_HOME_WIN}\\boot\\plexus-classworlds-2.6.0.jar"
M2_CONF="${MUSIO_MAVEN_HOME_WIN}\\bin\\m2.conf"

CMD="${MUSIO_JAVA_EXE_WIN} -classpath ${CLASSWORLDS_JAR} -Dclassworlds.conf=${M2_CONF} -Dmaven.home=${MUSIO_MAVEN_HOME_WIN} -Dmaven.multiModuleProjectDirectory=${WIN_ROOT} org.codehaus.plexus.classworlds.launcher.Launcher $*"

cmd.exe /C "$CMD"

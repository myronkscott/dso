#!/bin/sh

#@COPYRIGHT@

TOPDIR=`dirname "$0"`/..
. "${TOPDIR}"/libexec/tc-functions.sh

tc_install_dir "${TOPDIR}"/.. false
tc_classpath "" true
tc_java_opts "-Dtc.install-root=${TC_INSTALL_DIR}"

tc_java -classpath "${TC_CLASSPATH}" ${TC_ALL_JAVA_OPTS} com.tc.object.tools.BootJarTool "$@"

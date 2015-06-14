#!/bin/bash
LIB_JENA_PATH=lib/apache-jena-2.11.1
LIB_JENA_CP=("jena-core-2.11.1.jar" "jena-arq-2.11.1.jar" "log4j-1.2.16.jar" "slf4j-api-1.6.4.jar" "slf4j-log4j12-1.6.4.jar" "jena-iri-1.0.1.jar" "xercesImpl-2.11.0.jar" "xml-apis-1.4.01.jar")
LIB_ORMLITE_PATH=lib
LIB_ORMLITE_CP=("ormlite-core-4.48.jar" "ormlite-jdbc-4.48.jar")
LIB_SESAME_PATH=lib/openrdf-sesame-2.7.10/lib
LIB_SESAME_CP=("sesame-rio-api-2.7.10.jar" "sesame-rio-rdfxml-2.7.10.jar" "sesame-model-2.7.10.jar" "sesame-util-2.7.10.jar" "commons-io-2.4.jar")
LIB_H2_PATH=lib
LIB_H2_CP=("h2-1.3.175.jar")
LIB_SCALATEST_PATH=lib
LIB_SCALATEST_CP=("scalatest_2.10-2.0.jar")
LIB_SECONDSTRING_PATH=lib
LIB_SECONDSTRING_CP=("secondstring-20120620.jar")
LIB_APACHE_COMMONS_CODEC_PATH=lib/openrdf-sesame-2.7.10/lib
LIB_APACHE_COMMONS_CODEC_CP=("commons-codec-1.4.jar")
LIB_LUCENE_PATH=lib/lucene-4.7.0
LIB_LUCENE_CP=("core/lucene-core-4.7.0.jar" "analysis/common/lucene-analyzers-common-4.7.0.jar" "queryparser/lucene-queryparser-4.7.0.jar")
# return: "PATH/CP" strings array
# $1: PATH
# $2: CP
buildLIBS() {
	ARG_1=( $1 )
	FULL_PATH=()
	for jar in "${ARG_1[@]}"; do
		FULL_PATH+=("$2/$jar")
	done
	echo "${FULL_PATH[*]}"
}
LIB_JENA_FULL_PATH=$(buildLIBS "${LIB_JENA_CP[*]}" "${LIB_JENA_PATH[*]}")
LIB_SESAME_FULL_PATH=$(buildLIBS "${LIB_SESAME_CP[*]}" "${LIB_SESAME_PATH[*]}")
LIB_ORMLITE_FULL_PATH=$(buildLIBS "${LIB_ORMLITE_CP[*]}" "${LIB_ORMLITE_PATH[*]}")
LIB_H2_FULL_PATH=$(buildLIBS "${LIB_H2_CP[*]}" "${LIB_H2_PATH[*]}")
LIB_SCALATEST_FULL_PATH=$(buildLIBS "${LIB_SCALATEST_CP[*]}" "${LIB_SCALATEST_PATH[*]}")
LIB_SECONDSTRING_FULL_PATH=$(buildLIBS "${LIB_SECONDSTRING_CP[*]}" "${LIB_SECONDSTRING_PATH[*]}")
LIB_APACHE_COMMONS_CODEC_FULL_PATH=$(buildLIBS "${LIB_APACHE_COMMONS_CODEC_CP[*]}" "${LIB_APACHE_COMMONS_CODEC_PATH[*]}")
LIB_LUCENE_FULL_PATH=$(buildLIBS "${LIB_LUCENE_CP[*]}" "${LIB_LUCENE_PATH[*]}")
# return: java classpath as expected by java or javac
# $1: full class path array
buildCP() {
	ARG_1=( $1 )
	local JAVA_CP=""
	for jar in "${ARG_1[@]}"; do
		JAVA_CP="$JAVA_CP:$jar"
	done
	echo "$JAVA_CP"
}
ALL_JARS_IN_LIB_DIR=( $(find -L lib -type f -iname "*jar" -print) )
ALL_LIBS_FULL_CP=( "$LIB_JENA_FULL_PATH" "$LIB_SESAME_FULL_PATH" "$LIB_ORMLITE_FULL_PATH" "$LIB_H2_FULL_PATH" "$LIB_SCALATEST_FULL_PATH" "$LIB_SECONDSTRING_FULL_PATH" "$LIB_APACHE_COMMONS_CODEC_FULL_PATH" "$LIB_LUCENE_FULL_PATH" )
SRC_CP_DIRS=( $(find src -type d -print) )
SRC_FILES=( $(find src -type f -iname "*.scala" -print) )
TEST_CP_DIRS=( $(find test -type d -print) )
TEST_SRC_FILES=( $(find test -type f -iname "*.scala" -print) )
CLASS_DIRS=( $(find bin -type d -print) )
CUSTOM_CP=":data:data/dnb-Josef_Spieler-Psychologe.rdf:./log4j.properties:data:data/dnb:data/smw-cora"
# required libs added manually:
FINAL_CP=".$(buildCP "${CLASS_DIRS[*]}")$(buildCP "${SRC_CP_DIRS[*]}")$(buildCP "${TEST_CP_DIRS[*]}")$(buildCP "${ALL_LIBS_FULL_CP[*]}")$CUSTOM_CP"
# simply all jar files in lib/
#FINAL_CP=".$(buildCP "${CLASS_DIRS[*]}")$(buildCP "${SRC_CP_DIRS[*]}")$(buildCP "${TEST_CP_DIRS[*]}")$(buildCP "${ALL_JARS_IN_LIB_DIR[*]}")$CUSTOM_CP"
FINAL_SRC_FILES=( "${SRC_FILES[*]}" "${TEST_SRC_FILES[*]}" )
#echo "CP=$FINAL_CP"
#echo "SRC_FILES=$FINAL_SRC_FILES"
usage() {
	echo "usage:	run.sh compile
	run.sh test <scalaTestSpec>
	run.sh run <scalaSourceFile> [arg0] ... [argN]
	run.sh clean"
}
if [[ $# = 1 && "$1" = "clean" ]]; then
	echo "cleaning..."
	rm -rf bin/*
elif [[ $# = 2 && "$1" = "test" ]]; then
	scala -cp "$FINAL_CP" org.scalatest.run "$2"
elif [[ "$1" = "run" ]]; then
	args=( $@ )
	scala -cp "$FINAL_CP" ${args[@]:1}
elif [[ $# = 1 && "$1" = "compile" ]]; then
	echo "compiling..."
	scalac -cp "$FINAL_CP" -d bin ${FINAL_SRC_FILES[@]}
else
	usage
	exit 1
fi

#!/bin/sh

# precalc stuff
BASEDIR=$(dirname $0)

# change directory and run
cd $BASEDIR
java -Djava.library.path=. -jar ProjectMaya.jar


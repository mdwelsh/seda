#!/bin/sh

# Generate a snapshot release of the NBIO directory

BASE_DIR=$HOME/src/releases
RELEASE=nbio-release-`date +%Y%m%d`
RELEASE_DIR=$BASE_DIR/$RELEASE
PUBLIC_DIR=$HOME/public_html/proj/java-nbio

echo "Creating release in $RELEASE_DIR"

rm -rf $RELEASE_DIR
mkdir -p $RELEASE_DIR
cd $RELEASE_DIR
echo "Unpacking from CVS archive..."
export CVS_RSH=ssh
cvs -z3 -d:ext:mdwelsh@cvs.seda.sourceforge.net:/cvsroot/seda -Q co seda/docs seda/lib seda/README seda/src/seda/nbio seda/src/seda/Makefile seda/src/seda/Makefile.include
find . -name CVS | xargs rm -r

echo "Performing test build..."
cd seda/src/seda
make clean
export CLASSPATH=.:$RELEASE_DIR/seda/src
export LD_LIBRARY_PATH=$RELEASE_DIR/seda/lib
make || { echo "Build of release failed with errors, exiting"; exit 1; }
make clean
rm $RELEASE_DIR/seda/lib/*

echo "Publishing javadoc documentation..."
cd $RELEASE_DIR/seda/docs/javadoc
make nbio || { echo "Build of docs failed with errors, exiting"; exit 1; }
rm -rf $PUBLIC_DIR/javadoc
cd $RELEASE_DIR/seda/docs
tar cf - javadoc | (cd $PUBLIC_DIR; tar xf -)
cd $RELEASE_DIR/seda/docs/javadoc
make clean

echo "Creating $RELEASE.tar.gz..."
cd $BASE_DIR
tar cfz $RELEASE.tar.gz $RELEASE
rm -rf $RELEASE

echo "Copying $RELEASE.tar.gz to $PUBLIC_DIR..."
cp $RELEASE.tar.gz $PUBLIC_DIR

echo "Don't forget to FTP $RELEASE to ftp://upload.sourceforge.net/incoming"
echo "Done."

#echo "Don't forget to tag CVS: cvs tag $RELEASE"
#echo "Done."

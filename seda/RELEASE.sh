#!/bin/sh

# Generate a release of the SEDA directory

BASE_DIR=$HOME/src/releases
RELEASE=seda-release-`date +%Y%m%d`
RELEASE_DIR=$BASE_DIR/$RELEASE
PUBLIC_DIR=$HOME/public_html/proj/seda/

echo "Creating release in $RELEASE_DIR"

rm -rf $RELEASE_DIR
mkdir -p $RELEASE_DIR
cd $RELEASE_DIR
echo "Unpacking from CVS archive..."
export CVS_RSH=ssh
cvs -z3 -d:ext:mdwelsh@cvs.seda.sourceforge.net:/cvsroot/seda -Q co seda
find . -name CVS | xargs rm -r

# Remove files that should not be in the release
rm -rf seda/src/seda/apps
rm -rf seda/src/seda/sandStorm/lib/aTLS

echo "Performing test build..."
cd seda/src/seda
make clean
export CLASSPATH=.:$RELEASE_DIR/seda/src
export LD_LIBRARY_PATH=$RELEASE_DIR/seda/lib
make || { echo "Build of release failed with errors, exiting"; exit 1; }
make clean

echo "Publishing javadoc documentation..."
cd $RELEASE_DIR/seda/docs/javadoc
make || { echo "Build of release failed with errors, exiting"; exit 1; }
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
cd $PUBLIC_DIR
ln -sf $RELEASE.tar.gz seda-release-current.tar.gz

echo "Don't forget to tag CVS: cvs tag $RELEASE"
echo "Don't forget to FTP $RELEASE to ftp://upload.sourceforge.net/incoming"
echo "Done."

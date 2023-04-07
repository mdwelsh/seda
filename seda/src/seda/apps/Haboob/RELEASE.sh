#!/bin/sh

# Generate a snapshot release of the Haboob directory

BASE_DIR=$HOME/src/releases
RELEASE=haboob-release-`date +%Y%m%d`
RELEASE_DIR=$BASE_DIR/$RELEASE
PUBLIC_DIR=$HOME/public_html/proj/seda/

echo "Creating release in $RELEASE_DIR"

rm -rf $RELEASE_DIR
mkdir -p $RELEASE_DIR
cd $RELEASE_DIR
echo "Unpacking from CVS archive..."
export CVS_RSH=ssh
cvs -z3 -d:ext:mdwelsh@cvs.seda.sourceforge.net:/cvsroot/seda -Q co seda/src/seda/apps/Makefile seda/src/seda/apps/Haboob seda/src/seda/Makefile seda/src/seda/Makefile.include
find . -name CVS | xargs rm -rf

echo "Creating $RELEASE.tar.gz..."
cd $BASE_DIR
tar cfz $RELEASE.tar.gz $RELEASE
rm -rf $RELEASE

echo "Copying $RELEASE.tar.gz to $PUBLIC_DIR..."
cp $RELEASE.tar.gz $PUBLIC_DIR

echo "Don't forget to tag CVS: cvs tag $RELEASE"
echo "Don't forget to FTP $RELEASE to ftp://upload.sourceforge.net/incoming"

echo "Done."

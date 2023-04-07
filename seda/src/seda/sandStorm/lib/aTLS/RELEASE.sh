#!/bin/sh

# Generate a snapshot release of the aTLS library

BASE_DIR=$HOME/src/releases
TMP_DIR=/tmp/release.$$
RELEASE=atls-release-`date +%Y%m%d`
RELEASE_DIR=$TMP_DIR/$RELEASE
PUBLIC_DIR=$HOME/public_html/proj/seda/

echo "Creating release in $RELEASE_DIR"

rm -rf $RELEASE_DIR
mkdir -p $RELEASE_DIR
cd $RELEASE_DIR
echo "Unpacking from CVS archive..."

export CVS_RSH=ssh
cvs -z3 -d:ext:mdwelsh@cvs.seda.sourceforge.net:/cvsroot/seda -Q co seda/src/seda/sandStorm/lib/aTLS
find . -name CVS | xargs rm -rf

echo "Creating $RELEASE.tar.gz..."
cd $TMP_DIR
tar cfz $RELEASE.tar.gz $RELEASE
rm -rf $RELEASE

echo "Copying $RELEASE.tar.gz to $PUBLIC_DIR..."
cp $RELEASE.tar.gz $PUBLIC_DIR

mv $RELEASE.tar.gz $BASE_DIR

echo "Don't forget to FTP $RELEASE to ftp://upload.sourceforge.net/incoming"
echo "Done."


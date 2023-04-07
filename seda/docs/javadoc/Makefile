
all: javadocs

PACKAGES = seda.sandStorm.api \
           seda.sandStorm.api.internal \
           seda.sandStorm.core \
           seda.sandStorm.main \
           seda.sandStorm.internal \
           seda.sandStorm.lib.aSocket \
           seda.sandStorm.lib.aDisk \
           seda.sandStorm.lib.http \
           seda.sandStorm.lib.Gnutella \
           seda.sandStorm.lib.util \
           seda.nbio \
           seda.util 

NBIOPACKAGES = seda.nbio

javadocs:
	javadoc -d . -header '<b>More information:</b> <a href="http://www.cs.berkeley.edu/~mdw/proj/seda/">http://www.cs.berkeley.edu/~mdw/proj/seda/</a>' -windowtitle 'SEDA Javadoc Documentation' -package -author $(PACKAGES)

nbio:
	javadoc -d . -header '<b>More information:</b> <a href="http://www.cs.berkeley.edu/~mdw/proj/java-nbio">http://www.cs.berkeley.edu/~mdw/proj/java-nbio/</a>' -windowtitle 'NBIO Javadoc Documentation' -package -author $(NBIOPACKAGES)

clean:
	rm -rf *.html seda package-list stylesheet.css

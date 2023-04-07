/*
 * Copyright (c) 2002 by The Regents of the University of California. 
 * All rights reserved.
 *
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for any purpose, without fee, and without written agreement is
 * hereby granted, provided that the above copyright notice and the following
 * two paragraphs appear in all copies of this software.
 *
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR
 * DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES ARISING OUT
 * OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF THE UNIVERSITY OF
 * CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE PROVIDED HEREUNDER IS
 * ON AN "AS IS" BASIS, AND THE UNIVERSITY OF CALIFORNIA HAS NO OBLIGATION TO
 * PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 * Author: Dennis Chi <denchi@uclink4.berkeley.edu> 
 *
 */

package seda.sandStorm.lib.aTLS;

import COM.claymoresystems.ptls.*;

/**
 * An aTLSInputStream is created by aTLSConnection to overwrite the handshake
 * input stream of pureTLS (sock_in_hp). The purpose of this class is solely
 * to keep track of how much data resides on the stream. Can't use SSLInputStream
 * to find out how much data is left because pureTLS did not implement the available() method.
 * The reason for this is that some implementations of SSL/TLS group multiple
 * handshake messsages into one large record. Because of the design of the aTLSHandshakeStage
 * and its calls to pureTLS, it is necessary to know how much data is left on the 
 * stream after each call, therefore necessary to track every read and write.
 */
public class aTLSInputStream extends SSLInputStream {
    private static final boolean DEBUG = false;
    
    aTLSConnection atlsconn;
    SSLInputStream stream;
    int total = 0;

    /**
     * Creates an aTLSInputStream which will still make calls to SSLInputStream so that
     * data is handled appropriately.
     */
    public aTLSInputStream (aTLSConnection atlsconn, SSLInputStream stream) {
	super (atlsconn.rr);
	if (DEBUG) System.err.println ("aTLSInputStream: creating an aTLSInputStream.");
	this.atlsconn = atlsconn;
	this.stream = stream;
    }

    /**
     * The amount written for a write is determined by the size of the record.
     */
    public void write(SSLRecord r){
	 if (DEBUG) System.err.println ("aTLSInputStream: writing to a stream");
	 total += r.data.value.length;
	 if (DEBUG) System.err.println ("aTLSInputStream: the total in the stream is: " + total);
	 stream.write(r);
    }

    /**
     * This reads one byte from the stream.
     */
    public int read() throws java.io.IOException {
	total --;
	if (DEBUG) System.err.println ("aTLSInputStream: In read, the total in the stream is: " + total);
	return stream.read();
    }

    /**
     * This reads len bytes from b at an offset of off.
     */
    public int read(byte[] b,int off,int len) throws java.io.IOException {
	total -= len;
	if (DEBUG) System.err.println ("aTLSInputStream: In readarray(), the total in the stream is: " + total);
	return stream.read(b, off, len);
    }
}
    

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

import seda.sandStorm.core.BufferElement;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * In order to properly handle the sending of data, needed to overwrite
 * the output stream of pureTLS with this one. PureTLS sends data directly
 * through a socket's stream (look at SSLRecord.send() and variable sock_out), 
 * but aTLS needs to enqueue the data to the ATcpConnection so the aSocket layer
 * can handle it.
 */
public class aTLSBufferedOutputStream extends BufferedOutputStream {
    
    private static final boolean DEBUG = false;
    private static final boolean DEBUGBYTES = false;

    aTLSConnection atlsconn;

    /**
     * Creates a stream associated with the given aTLSConnection.
     * Data will be posted to the ATcpConnection associated with the atlsconn.
     */
    public aTLSBufferedOutputStream (OutputStream out, aTLSConnection atlsconn) {
	super (out);
	this.atlsconn = atlsconn;
    }

    /**
     * Flush does nothing because data is sent out each time write() is called.
     */
    public void flush() throws IOException {
    }

    /**
     * Receives data written out by SSLRecord.java (PureTLS) and posts it to the
     * ATcpConnection associated with the aTLSConnection.
     */
    public void write(byte[] b, int off, int len) throws IOException {
	if (DEBUG) System.err.println ("aTLSBufferedOutputStream: Writing this much data out: " + len);

	if (DEBUGBYTES) {
	    System.err.println ("aTLSBufferedOutputStream: contents of the byte array argument:");
	    for (int i = 0; i < b.length; i++) {
		System.err.print (b[i] + " ");
	    }
	    System.err.println ();
	}
	
	byte[] data = new byte[len];
	
	System.arraycopy (b, 0, data, 0, len);
	
	if (DEBUGBYTES) {
	    System.err.println ("aTLSBufferedOutputStream: This is the data that will be sent:");
	    for (int i = 0; i < len; i++) {
		System.err.print (data[i] + " ");
	    }
	    System.err.println ();
	}
	
	BufferElement be = new BufferElement (data);
	atlsconn.getConnection().enqueue_lossy(be);
    }
}






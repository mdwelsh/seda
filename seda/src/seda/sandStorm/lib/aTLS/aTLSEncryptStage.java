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

import seda.sandStorm.api.*;

/**
 * The Encrypt Stage is only used for application data packets, not for
 * handshake packets. This is because handshake packets, except for the last one, do
 * not undergo the full encryption process, so not as expensive. Therefore no
 * reason to pass through this stage. This stage is used only for application data
 * that must be encrypted by PureTLS.
 * Not multithreaded yet.
 */

//class aTLSEncryptStage implements EventHandlerIF {
class aTLSEncryptStage implements EventHandlerIF, SingleThreadedEventHandlerIF {
    private static final boolean DEBUG = false;

    private ManagerIF mgr;
    private SinkIF mySink;

    public aTLSEncryptStage (ManagerIF mgr) throws Exception {
	this.mgr = mgr;
	mgr.createStage("aTLSEncryptStage", this, null);
    }
    
    public void init(ConfigDataIF config) {
	mySink = config.getStage().getSink();
    }

    /**
     * If the QueueElement is an aTLSEncryptPacket, then will write to PureTLS stream so that the
     * record can be encrypted, and then sent to aTLSBufferedOutputStream to be sent over the socket by ATcp layer.
     * If it is an aTLSCloseRequest, then the necessary close functions are called and cleanup is done.
     */
    public void handleEvent (QueueElementIF element) {
	if (DEBUG) System.err.println("aTLSEncryptStage GOT QEL: "+element);
	
	if (element instanceof aTLSEncryptPacket) {		
	    aTLSEncryptPacket ep = (aTLSEncryptPacket) element;
	    aTLSConnection atlsconn = ep.getConnection();
	    byte[] data = ep.getBytes();
	    
	    try {
		if (DEBUG) System.err.println ("aTLSEncryptstage: writing out this much: " + data.length);
		atlsconn.conn.sock_out_external.write(data, 0, data.length);
	    }
	    catch (Exception e) {
		System.err.println ("aTLSEncryptStage: Exception trying to write data out " + e);
		try {
		    atlsconn.getConnection().close(atlsconn.recordStageSink);
		}
		catch (SinkClosedException sce) {
		    System.err.println ("aTLSEncryptStage: Exception trying to close the connection " + sce);
		}
	    }

	}
	else if (element instanceof aTLSCloseRequest) {
	    // want to pass this close request back to the record stage because the main clean up has to occur
	    // in that stage. Also, record stage will pass the close event back to the user.
	    aTLSCloseRequest cr = (aTLSCloseRequest)element;
	    aTLSConnection atlsconn = cr.conn;
	    try {
		atlsconn.getConnection().close (atlsconn.recordStageSink);
	    }
	    catch (SinkClosedException sce) {
		System.err.println ("aTLSEncryptStage: Exception trying to close the ATcpConnection: " + sce);
	    }
	}
	else {
	    System.err.println ("aTLSEncryptStage: Received a bad element: " + element + 
				". Internal Error, email mdw@cs.berkeley.edu");
	}
    }

    public void handleEvents(QueueElementIF[] qelarr) {
	for (int i = 0; i < qelarr.length; i++) {
	    handleEvent(qelarr[i]);
	}
    }

    /** 
     * Return sink so record stage can redirect packets to be encrypted here
     */
    SinkIF getSink() {
	return mySink;
    }

    /** 
     * The Sandstorm stage destroy method.
     */
    public void destroy() {
    }
}









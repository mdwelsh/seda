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
import seda.sandStorm.api.*;
import seda.sandStorm.lib.aTLS.protocol.aTLSHandshakeRecord;
import seda.sandStorm.lib.aTLS.protocol.aTLSRecord;

import java.io.ByteArrayInputStream;
import java.io.PushbackInputStream;
import java.util.LinkedList;

/**
 * aTLSHandshakeStage is a SandStorm stage that deals exclusively with the handshake protocol.
 * Makes calls to PureTLS, and controls the input stream so that pureTLS doesn't block waiting for
 * records. This stage is not multithreaded yet.
 */
//class aTLSHandshakeStage implements aTLSConst, EventHandlerIF{
class aTLSHandshakeStage implements aTLSConst, EventHandlerIF, SingleThreadedEventHandlerIF{
    private static final boolean DEBUG = false;
    
    private ManagerIF mgr;
    private SinkIF mySink;
    private LinkedList handshakeQueue;
    private int i = 0;

    // this is added to profile to see how long things take in this stage:
    private static final boolean PROFILE = false;

    /**
     * Within the constructor, will call mgr.createStage() to set up the
     * sink, etc.. for this stage
     */
    public aTLSHandshakeStage (ManagerIF mgr) throws Exception {
	this.mgr = mgr;
	handshakeQueue = new LinkedList();
	mgr.createStage("aTLSHandshakeStage", this, null);
    }

    /**
     * Assign mySink to this stages' sink
     */
    public void init(ConfigDataIF config) {
	mySink = config.getStage().getSink();
    }

    /** 
     * This function handles the handshake by placing the messages into the pureTLS input stream and calling
     * the readRecord and handshakeContinue functions to maintain everything for aTLS. 
     * Also, had to handle SSLv2 client hello differently because the structure of the record is completely
     * different.
     * NOTE: need to handle multiple handshake messages in one record, that's why its is necessary to 
     * overwrite PureTLS input stream with aTLSInputStream because need to check that amount of data left
     * on the stream, and loop appropriately so that no handshake messages are lost.
     */
    public void handleEvent (QueueElementIF element) {
	if (DEBUG) System.err.println("aTLSHandshakeStage GOT QEL: "+element);

	aTLSConnection atlsconn;
	aTLSRecord record;
	byte[] data = null;
	boolean changeCipher = false;
	boolean version2 = false;

	if (element instanceof aTLSCipherSpecPacket) {
	    if (DEBUG) System.err.println ("aTLSHandshakeStage: Handling a change cipher spec.");
	    aTLSCipherSpecPacket ccsp = (aTLSCipherSpecPacket) element;
	    atlsconn = ccsp.getConnection();
	    record = ccsp.getRecord();
	    changeCipher = true;
	}
	else if (element instanceof aTLSHandshakePacket) {
	    if (DEBUG) System.err.println ("aTLSHandshakeStage: Handling a handshake message.");
	    aTLSHandshakePacket hp = (aTLSHandshakePacket) element;
	    atlsconn = hp.getConnection();
	    record = hp.getRecord();
	    
	    // initially, the first handshake packet sent by the record stage is for the client
	    // to start sending a client hello, so have to check for that
	    if (!(hp.getboolHS())) {
		data = record.data;
		version2 = ((aTLSHandshakeRecord)record).sslv2;
	    }
	}
	else {
	    System.err.println ("aTLSHandshakeStage: Received bad element: " + element + 
				". Internal error, email mdw@cs.berkeley.edu");
	    return;
	} 

	synchronized (atlsconn) {

	    if (PROFILE) {
		atlsconn.prof.addMeasurements(System.currentTimeMillis(), "aTLSHandshakeStage just received event from recordstage. this is the " + atlsconn.hscounter + " handshake message");
	    }

	    if (DEBUG) {
		System.err.println ("aTLSHandshakeStage, dealing with a packet from: " + atlsconn.getConnection());
		System.err.println ("aTLSHandshakeStage: the state of this is: " + atlsconn.conn.hs.state);

		if (record != null) {
		    System.err.println ("aTLSHandshakeStage: the length of the record data is: " + record.data.length);
		}
	    }
	    if (version2) {
		// need to handle SSLv2 separately
		if (DEBUG) System.err.println ("aTLSHandshakeStage: dealing with SSLv2 Client Hello.");
	    
		ByteArrayInputStream bais = new ByteArrayInputStream (data);

		// so we "filled" the buffer with the sslv2 client hello record
		// have to do this because when handshakeContinue() (puretls) is called, it will try to read
		// off of sock_in, and will block until something comes in, therefore we must provide it
		atlsconn.conn.sock_in = new PushbackInputStream (bais);
		
		try {
		    atlsconn.conn.hs.handshakeContinue();
		}
		catch (Exception e) {
		    System.err.println ("aTLSHandshakeStage: Exception in trying to do the sslv2clienthello " + e);
		 
		    try {
			atlsconn.getConnection().close(atlsconn.recordStageSink);
		    }
		    catch (SinkClosedException sce) {
			System.err.println ("aTLSHandshakeStage: Exception trying to close the connection " + e);
			for (int i = 0; i < 5; i++) {
			    System.err.println ();
			}
		    }
		}
	    }
	    else if (record != null) {
		boolean clear = false;

		// we want to call readRecord, so stick the entire record onto sock_in
		ByteArrayInputStream bais = new ByteArrayInputStream (record.data);
		atlsconn.conn.sock_in = new PushbackInputStream (bais);
		
		try {
		    atlsconn.rr.readRecord();
		}
		catch (Exception e) {
		    System.err.println ("aTLSHandshakeStage: Exception trying to readrecord " + e);
		    System.err.println ("aTLsHAndshakeStage: this exception happened on this connection: " + atlsconn.getConnection());

		    try {
			System.err.println ("aTLSHandshakeStage: This is how much is left in the sock_in after excepytion: " + 
					    atlsconn.conn.sock_in.available());
		    }
		    catch (Exception ie) {
			System.err.println ("atlshandhssake stage aidjcoiajfierwofwj");
		    }

		    for (int i = 0; i < 5; i++) {
			System.err.println ();
		    }
		    e.printStackTrace();
		
		    try {
			atlsconn.getConnection().close(atlsconn.recordStageSink);
		    }
		    catch (SinkClosedException sce) {
			System.err.println ("aTLSHandshakeStage: Exception trying to close the connection " + sce);
			for (int i = 0; i < 5; i++) {
			    System.err.println ();
			}
		    }
		}

		// don't need to call handshakeContinue() for a change cipher spec message because
		// readRecord already handles it. Also, don't have to worry about the while loop
		// because changeCipherSpec is a separate record type, so won't be bunched up with handshake messages.
		if (changeCipher) {
		    return;
		}

		// this while loop will keep looping if there were multiple handshake messages in one record
		while (((aTLSInputStream)atlsconn.conn.sock_in_hp).total > 0) {
		    if (DEBUG) System.err.println ("aTLSHandshakeStage: in while loop, dealing with a handshake mesage");

		    // This part is a little ugly, because if the server is waiting for a client hello, need to fill both
		    // input streams with the data. This is because PureTLS first looks at sock_in then goes to sock_in_hp. 

		    // NOTE: have to remember to clear out the conn.sock_in buffer before it loops back, 
		    // because normally it would be empty after readRecord() because a client hello can't be bunched with
		    // multiple handshake messages, and our while loop will loop when its not supposed to.
		    if ((atlsconn.conn.hs.state == SSL_HS_WAIT_FOR_CLIENT_HELLO) && atlsconn.isServer) {
			ByteArrayInputStream temp = new ByteArrayInputStream(record.data);

			clear = true;
			atlsconn.conn.sock_in = new PushbackInputStream (temp);
		    }
		    
		    try {
			if (DEBUG) System.err.println ("aTLSHandshakeStage: Calling handshakeContinue()");
			atlsconn.conn.hs.handshakeContinue();
			if (DEBUG) System.err.println ("aTLSHandshakeStage: DONE Calling handshakeContinue()");
		    }
		    catch (Exception e) {
			System.err.println ("aTLSHandshakeStage: Exception in handshakeContinue " + e);
			System.err.println ("aTLsHAndshakeStage: this exception happened on this connection: " + atlsconn.getConnection());
			e.printStackTrace();
			for (int i = 0; i < 5; i++) {
			    System.err.println ();
			}
			
			// so let's just close this connection then if there's a handshake error
			// I want to send the sinkClosedEvent to the recordStage, so this stage
			try {
			    atlsconn.getConnection().close(atlsconn.recordStageSink);
			}
			catch (SinkClosedException sce) {
			    System.err.println ("aTLSHandshakeStage: Exception trying to close the connection " + sce);
			    for (int i = 0; i < 5; i++) {
				System.err.println ();
			    }
			}
		    }
		    
		    if (atlsconn.conn.hs.state == SSL_HANDSHAKE_FINISHED) {
			// have to do this for pureTLS because didn't use their function
			atlsconn.conn.sock_out_external=new SSLOutputStream(atlsconn.conn);
		    
			if (PROFILE) {
			    atlsconn.prof.addMeasurements (System.currentTimeMillis(), "aTLSHandshake has just finished the entire handshake, passing the atlsconnection to user");
			}
		 
			// aTLSConnection passed back to the user because handshake has just been completed.
			atlsconn.getNewConnSink().enqueue_lossy(atlsconn);
			break;
		    }
				    
		    if (clear) {
			// so reset sock_in, so when it loops back, read == -1
			byte[] buf = new byte[0];
			ByteArrayInputStream in = new ByteArrayInputStream(buf);
			atlsconn.conn.sock_in = new PushbackInputStream (in);
		    }
		}
		if (DEBUG) System.err.println ("aTLSHandshakeStage: Leaving the while loop");
	    }
	    else {
		// this else is for the initial handshake packet to get the handshake started.

		// the server doesn't need to do anything, but the client needs to send a client hello
	    
		if ((!(atlsconn.isServer)) && atlsconn.conn.hs.state == SSL_HS_HANDSHAKE_START) {
		    // puretls just sends a client hello, and then blocks, waiting for a server hello to come in.
		    // Since aTLS can't block, just going to send the first server hello and change the state manually.
		    try {
			((SSLHandshakeClient)atlsconn.conn.hs).sendClientHello();
		    }
		    catch (Exception e) {
			System.err.println ("aTLsHandshakeStage: Exception trying to call sendclientHello()");
			try {
			    atlsconn.getConnection().close(atlsconn.recordStageSink);
			}
			catch (SinkClosedException sce) {
			    System.err.println ("aTLSHandshakeStage: Exception trying to close the connection " + sce);
			}
		    }
		    atlsconn.conn.hs.state=SSL_HS_SENT_CLIENT_HELLO;
		}
	    }

	    if (DEBUG) System.err.println ("aTLSHandshakeStage, DONE dealing with a packet from: " + atlsconn.getConnection());
	    if (PROFILE) {
		atlsconn.prof.addMeasurements (System.currentTimeMillis(), "aTLSHandshakeStage just finished dealing with " + atlsconn.hscounter + " handshake message, leaving the stage.");
		atlsconn.hscounter++;
	    }
	}	
    }
    
    public void handleEvents(QueueElementIF[] qelarr) {
	for (int i = 0; i < qelarr.length; i++) {
	    if (qelarr[i] == null) {
		System.err.println ("aTLSHandshakeStage: Received a null in handleEvents(). Internal Error, email mdw@cs.berkeley.edu");
	    }
	    else {
		handleEvent(qelarr[i]);
	    }
	}
    }
   
    /** 
     * Return mySink
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






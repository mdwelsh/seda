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
import COM.claymoresystems.sslg.*;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkClosedException;
import seda.sandStorm.api.SinkException;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.lib.aSocket.ATcpClientSocket;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.sandStorm.lib.aSocket.ATcpServerSocket;
import seda.sandStorm.lib.aTLS.protocol.aTLSRecord;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.util.LinkedList;

/**
 * Internal class that represents a connection between a client/server
 * and is the only class that creates instances of any PureTLS code, because
 * when an aTLSConnection is created in the record stage, data must now
 * be controlled.
 * This object will not be passed back to the user until the 
 * entire handshake has been completed and a secure connection exists.
 */

public class aTLSConnection extends ATcpConnection implements SinkIF, aTLSConst, QueueElementIF {
    // this is added because need to make HandshakeStage ordered
    private aTLSOrderData hsorderer;

    private static final boolean DEBUG = false;
    
    private ATcpConnection atcpconn;

    private SinkIF userNewConnSink;
    private SinkIF userDataSink = null;

    LinkedList dataQueue;

    private SSLContext ctx;
    private SSLPolicyInt policy;
    private aTLSPacketReader reader;
    private int myState;
    
    boolean isServer;
    public SinkIF encryptSink;
    public SinkIF recordStageSink;

    // profile utilities
    private static final boolean PROFILE = false;
    public long proftimer;
    public int hscounter = 0;
    public aTLSprofiler prof = null;
    public String type = null;
   
    // just incase, have a lock for the userDataSink
    Object dataSinkLock;
    
    public SSLConn conn;
    public SSLRecordReader rr;

    byte[] aTLSSessionID;

    boolean closed = false;

    private aTLSClientSocket atlscs = null;
    private aTLSServerSocket atlsss = null;
	
    public aTLSConnection (aTLSClientSocket atlscs, ATcpConnection atcpconn, SinkIF userSink, SinkIF encryptSink, 
			   SinkIF recordStageSink, boolean isServer, SSLContext ctx, byte[] aTLSSessionID) {
	this (atcpconn, userSink, encryptSink, recordStageSink, isServer, ctx, aTLSSessionID);
	this.atlscs = atlscs;
    }

    public aTLSConnection (aTLSServerSocket atlsss, ATcpConnection atcpconn, SinkIF userSink, SinkIF encryptSink, 
			   SinkIF recordStageSink, boolean isServer, SSLContext ctx, byte[] aTLSSessionID) {
	this (atcpconn, userSink, encryptSink, recordStageSink, isServer, ctx, aTLSSessionID);
	this.atlsss = atlsss;
    }

    /**
     * Handles all the configuration to deal with receiving/sending data and interacting with PureTLS.
     * Creates the queue to hold data before startReader is called, and creates a new aTLSPacketReader.
     * To interact with PureTLS, creates an SSLConn (SSLSocket had to be created also) to access
     * PureTLS' record reader and to overwrite its input and output stream.
     * This allows aTLS to have complete control over all data sent and received on this connection.
     */
    public aTLSConnection (ATcpConnection atcpconn, SinkIF userSink, SinkIF encryptSink, 
			   SinkIF recordStageSink, boolean isServer, SSLContext ctx, byte[] aTLSSessionID) {
	super ();

	// added to make handshake ordered
	hsorderer = new aTLSOrderData();

	this.atcpconn = atcpconn;
	userNewConnSink = userSink;

	// this is to keep track of messages until the user calls aTLSConnection.startReader()
	dataQueue = new LinkedList();

	this.encryptSink = encryptSink;
	this.recordStageSink = recordStageSink;

	this.aTLSSessionID = aTLSSessionID;

	this.ctx = ctx;
	policy = ctx.getPolicy();
	dataSinkLock = new Object();

	SSLSocket s = new SSLSocket();

	int how;

	// added for profile
	if (isServer) {
	    how = SSL_SERVER;
	    type = "server";
	}
	else {
	    how = SSL_CLIENT;
	    type = "client";
	}
	
	try {
	    conn = new SSLConn (s, null, null, ctx, how);
	}
	catch (Exception e){
	    System.err.println ("aTLSConnection: Exception trying to create SSLConn " + e);
	}

	this.isServer = isServer;
	reader = new aTLSPacketReader(this);

	rr = ((SSLInputStream)conn.sock_in_hp).rdr;

	// SSLConn.handshake() cannot be called, so need to set up these variables
	conn.max_ssl_version=policy.negotiateTLSP()?TLS_V1_VERSION:SSL_V3_VERSION;
	conn.ssl_version=policy.negotiateTLSP()?TLS_V1_VERSION:SSL_V3_VERSION;

	if(how == SSL_CLIENT){
	    conn.hs=new SSLHandshakeClient(conn);
	    ((SSLHandshakeClient)conn.hs).aTLSSessionID = aTLSSessionID;
	}
	else {
	    conn.hs=new SSLHandshakeServer(conn);
	}

	// overwrite both the input and output streams.
	// overwrite input stream because handshakeStage needs to keep track of how much data is left
	// on the stream.
	// need to overwrite output stream because rather than letting PureTLS sent directly to the socket's stream,
	// aTLS needs to post to ATcp layer.
	conn.sock_out = new aTLSBufferedOutputStream (new ByteArrayOutputStream(), this);

	SSLInputStream temp = (SSLInputStream)conn.sock_in_hp;
	conn.sock_in_hp = new aTLSInputStream (this, temp);
	rr.streams[0] = (SSLInputStream)conn.sock_in_hp;
    }

    /**
     * Enqueue an outgoing data packet to the Encrypt Stage for encryption by PureTLS.
     */
    public void enqueue (QueueElementIF element) throws SinkException {
	if (closed) throw new SinkClosedException ("aTLSConnection: Sink is closed.");

	if (!(element instanceof BufferElement)) {
	    System.err.println ("aTLSConnection: Bad packet in enqueue(). Internal Error, email mdw@cs.berkeley.edu");
	}

	if (DEBUG) System.err.println ("aTLSConnection: enqueueing an encrypt packet to encrypt stage.");
	encryptSink.enqueue(new aTLSEncryptPacket (this, ((BufferElement)element).getBytes()));
    }

    /**
     * Enqueue an outgoing data packet to the Encrypt Stage for encryption by PureTLS.
     * Drops the packet if it cannot be enqueued.
     */
    public boolean enqueue_lossy(QueueElementIF element) {
	if (closed) return false;

	if (!(element instanceof BufferElement)) {
	    System.err.println ("aTLSConnection: Bad packet in enqueue_lossy(). Internal Error, email mdw@cs.berkeley.edu");
	}

	if (DEBUG) System.err.println ("aTLSConnection: enqueue_lossy an encrypt packet to encrypt stage.");
	return encryptSink.enqueue_lossy(new aTLSEncryptPacket (this, ((BufferElement)element).getBytes()));
    }
    
    /**
     * Enqueue a set of outgoing data packets to the Encrypt Stage for encryption by pureTLS.
     */
    public void enqueue_many(QueueElementIF[] elements) throws SinkException {
	if (closed) throw new SinkClosedException ("aTLSConnection: Sink is closed.");

	if (DEBUG) System.err.println ("aTLSConnection: enqueue_many an encrypt packet to encrypt stage.");

	for ( int i = 0; i < elements.length; i++) {
	    QueueElementIF element = elements[i];
	    if (!(element instanceof BufferElement)) {
		System.err.println ("aTLSConnection: Bad packet in enqueue_lossy(). Internal Error, email mdw@cs.berkeley.edu");
		return;
	    }
	    encryptSink.enqueue_lossy(new aTLSEncryptPacket (this, ((BufferElement)element).getBytes()));
	}
    }
    
    /** 
     * Return the address of the peer.
     */
    public InetAddress getAddress() {
      	return atcpconn.getAddress();
    }
    
    /**
     * Return the port of the peer.
     */
    public int getPort() {
	return atcpconn.getPort();
    }
    
    /** 
     * Return the ATcpServerSocket associated with this connection.
     * Will be null if connection created by client.
     */
    public ATcpServerSocket getServerSocket() {
	return atlsss;
    }
    
    /** 
     * Return the ATcpClientSocket associated with this connection.
     * Will be null if connection created by server.
     */
    public ATcpClientSocket getClientSocket() {
	return atlscs;
    }

    /** 
     * Associate a SinkIF with this connection and allow data
     * to start flowing into it. When data is read, ATcpInPacket objects
     * will be pushed into the given SinkIF. Until this method is called by the user,
     * all data will be pushed onto the dataQueue by the record stage.
     * The dataSinkLock is necessary because the record stage is enqueuing messages
     * onto the dataQueue if startReader has not been called and this method removes from
     * the dataQueue, so need to synchronize the two events so that messages will not be lost.
     */
    public void startReader(SinkIF receiveQ) {
	synchronized (dataSinkLock) {
	    if (DEBUG) System.err.println ("aTLSConnection: assigning the userDataSink var in startReader()");
	    userDataSink = receiveQ;
	    while (!(dataQueue.isEmpty())) {
		aTLSRecord record = (aTLSRecord) dataQueue.removeFirst();
		userDataSink.enqueue_lossy (new ATcpInPacket (this, new BufferElement(record.data)));
	    }
	}
    }

    /** 
     * Associate a SinkIF with this connection and allow data
     * to start flowing into it. When data is read, ATcpInPacket objects
     * will be pushed into the given SinkIF. Until this method is called by the user,
     * all data will be pushed onto the dataQueue by the record stage.
     * The dataSinkLock is necessary because the record stage is enqueuing messages
     * onto the dataQueue if startReader has not been called and this method removes from
     * the dataQueue, so need to synchronize the two events so that messages will not be lost.
     * Unlike ATcpConnection.startReader(), readClogTries does nothing for now.
     */
    public void startReader(SinkIF receiveQ, int readClogTries) {
	synchronized (dataSinkLock) {
	    if (DEBUG) System.err.println ("aTLSConnection: assigning the userDataSink var in startReader()");
	    userDataSink = receiveQ;
	    while (!(dataQueue.isEmpty())) {
		aTLSRecord record = (aTLSRecord) dataQueue.removeFirst();
		userDataSink.enqueue_lossy (new ATcpInPacket (this, new BufferElement(record.data)));
	    }
	}
    }

    /**
     * Returns the SinkIF that new aTLSConnections should be posted to.
     */
    public SinkIF getNewConnSink() {
	return userNewConnSink;
    }

    /**
     * Returns the SinkIF that data will be posted to.
     */
    public SinkIF getDataSink() {
	return userDataSink;
    }

    /**
     * Returns the ATcpConnection associated with this connection.
     */
    public ATcpConnection getConnection() {
	return atcpconn;
    }

    /** 
     * Returns the aTLSPacketReader associated with this connection.
     */
    public aTLSPacketReader getReader() {
	return reader;
    }    

    /**
     * Returns the SSLContext (PureTLS) associated with this connection.
     */
    public SSLContext getContext() {
	return ctx;
    }

    /**
     * Returns the SSLPolicyInt (PureTLS) associated with this connection.
     */
    public SSLPolicyInt getPolicy() {
	return policy;
    }

    /**
     * Returns the profile size of this connection.
     */
    public int profileSize() {
	return atcpconn.profileSize();
    }

    public String toString() {
	return atcpconn.toString();
    }

    /**
     * Return the number of elements in this sink.
     */
    public int size() {
	return atcpconn.size();
    }

    /**
     * This function will create an aTLSCloseRequest and post it to the
     * encrypt stage. Cannot just close the ATcpConnection immediately because
     * there might be pending packets for this connection in the encrypt
     * stage that have not been sent yet, so by enqueuing this message, guaranteed
     * that messages that were sent before will be sent.
     */
    public void close(final SinkIF compQ) throws SinkClosedException {
	if (closed) throw new SinkClosedException("ATcpConnection closed");
	closed = true;
	if (PROFILE) System.err.println ("DENCHI: aTLSConnection received close request, going to enqueue close request to encryptstage");
	encryptSink.enqueue_lossy(new aTLSCloseRequest(this, compQ));
    }

    /**
     * Flush the socket. 
     */
    public void flush(SinkIF compQ) throws SinkClosedException {
	atcpconn.flush(compQ);
    }
}









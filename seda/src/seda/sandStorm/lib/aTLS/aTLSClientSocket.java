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
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.lib.aSocket.ATcpClientSocket;
import seda.sandStorm.main.Sandstorm;
import seda.sandStorm.main.SandstormConfig;

import java.net.InetAddress;

/** 
 * An aTLSClientSocket is created to begin the connection process to the
 * specified server. It creates the three stages (record, handshake, encrypt), if not already
 * created, and sends a connect request to the record stage. Also loads in the appropriate
 * files needed for the client (root.pem, client.pem, random.pem) and sets the policy and
 * context in PureTLS. If the files are not specified in a config file, initialize() method
 * must be called in order for the connection process to continue.
 * An ATcpClientSocket will be created when the record stage receives the
 * connect request, and an aTLSConnection will be associated with this
 * client once an ATcpConnection has been received in the record stage for this client.
 */

public class aTLSClientSocket extends ATcpClientSocket{

    private static final boolean DEBUG = true;
    
    static boolean initialized = false;
    
    static private aTLSEncryptStage EncryptStage;
    static private aTLSRecordStage RecordStage;
    static private aTLSHandshakeStage HandshakeStage;

    static SSLContext ctx;
    static private Object randomFileLock = new Object();
    private static ManagerIF mgr;

    ATcpClientSocket atcpcs;
    int clientPort;
    private SinkIF clientSink;
    InetAddress clientHost;

    static private boolean filesInitialized = false;

    // Could have just assigned the session_id variable, but have a reference
    // to the entire atlsconnection incase other info might be needed later on
    aTLSConnection atlsconn;

    // if the user wants to resume a session, then will provide a non-null byte[]
    byte[] aTLSSessionID;
    
    private static String rootfile = null;
    private static String keyfile = null;
    private static String randomfile = null;
    private static String password = null;

    // profile utilities
    private static final boolean PROFILE = false;
    public aTLSprofiler prof = null;
    
    /**
     * Create an aTLSClientSocket connecting to the given address and port.
     * An aTLSConnection will be posted to the given SinkIF when the
     * connection is established (handshake has finished).
     */
    public aTLSClientSocket(InetAddress addr, int port, SinkIF clientSink) throws Exception { 
	this (addr, port, clientSink, null);
    }
    
    /**
     * Create an aTLSClientSocket connecting to the given host and port.
     * An aTLSConnection will be posted to the given SinkIF when the
     * connection is established (handshake has finished).
     */
    public aTLSClientSocket(String host, int port, SinkIF clientSink) throws Exception {
	this(InetAddress.getByName(host), port, clientSink, null);
    }

    /**
     * Create an aTLSClientSocket connecting to the given host and port.
     * An aTLSConnection will be posted to the given SinkIF when the
     * connection is established (handshake has finished).
     * This client will attempt to resume a previous session with the specified
     * sessionID.
     */
    public aTLSClientSocket(String host, int port, SinkIF clientSink, byte[] sessionID) throws Exception {
	this(InetAddress.getByName(host), port, clientSink, sessionID);
    }
    
    /**
     * Create an aTLSClientSocket connecting to the given address and port. An aTLSConnection
     * will be posted to the given SinkIF when connection is established. Will attempt to resume
     * with the specified sessionID, if not null. 
     * The three stages will be created if this is the first client socket to be instantiated.
     * Loads context and policy used for this secure connection to PureTLS, assuming those are the
     * same for all clients. Will also attempt to load the necessary client files from the SandStormConfig
     * class, if not already initialized.
     * An aTLSConnectRequest is then posted to the Record Stage.
     */
    public aTLSClientSocket(InetAddress addr, int port, SinkIF clientSink, byte[] sessionID) throws Exception { 
	super();
	if (DEBUG) System.err.println ("aTLSClientSocket: creating in constructor");

	if (PROFILE) {
	    long timer = System.currentTimeMillis();
	    prof = new aTLSprofiler (timer);
	    prof.addMeasurements (timer, "aTLSClientSocket just created");
	}
	    	
	this.clientSink = clientSink;
	this.clientHost = addr;
	this.clientPort = port;

	aTLSSessionID = sessionID;

	if (!initialized) {
	    Sandstorm ss = Sandstorm.getSandstorm();
	    if (ss == null) {
		SandstormConfig cfg = new SandstormConfig();
		try {
		    ss = new Sandstorm(cfg);
		}
		catch (Exception e) {
		    System.err.println("aTLSClientSocket: Warning: Sandstorm Initialization failed: " + e);
		    e.printStackTrace();
		    return;
		}
	    }
	    
	    aTLSClientSocket.mgr = ss.getManager();

	    // for now assuming that all client sockets have the same policy and context

	    if (DEBUG) System.err.println ("aTLSClientSocket: Creating the SSLContext.");
	    ctx=new SSLContext();
	    
	    // policy holds a list of default ciphersuites to use, unless the user provides a list.
	    // just use their default for now
	    SSLPolicyInt policy=new SSLPolicyInt();
	    
	    // don't know how to generate our own certificates, so just accept any
	    policy.acceptUnverifiableCertificates(true);
	    
	    ctx.setPolicy(policy);

	    if (!filesInitialized) {
		rootfile = mgr.getConfig().getString("global.aTLS.rootfile");
		if (rootfile == null) {
		    throw new IllegalArgumentException("Must specify 'rootfile' option in <aTLS> section of config file or call initialize() with appropriate files as arguments");
		}
		
		keyfile = mgr.getConfig().getString("global.aTLS.keyfile");
		if (keyfile == null) {
		    throw new IllegalArgumentException("Must specify 'keyfile' option in <aTLS> section of config file or call initialize() with appropriate files as arguments");
		}
		

		randomfile = mgr.getConfig().getString("global.aTLS.randomfile");
		if (randomfile == null) {
		    throw new IllegalArgumentException("Must specify 'randomFile' option in <aTLS> section of config file or call initialize() with appropriate files as arguments");
		}
		    
		password = mgr.getConfig().getString("global.aTLS.password");
		if (password == null) {
		    throw new IllegalArgumentException("Must specify 'password' option in <aTLS> section of config file or call initialize() with appropriate files as arguments");
		}
		filesInitialized = true;
	    }
	    try {
		ctx.loadRootCertificates(rootfile);
		ctx.loadEAYKeyFile(keyfile,password);
		
		// useRnadomnessfile rewrites the randomfile, so if we are running a benchmark wth differrent nodes
		// then they each create a separate aTLSClientSocket, so there coudl be synchro problems with this.
		//synchronized (randomFileLock) {
		ctx.useRandomnessFile(randomfile,password);
		    //}
	    } 
	    catch (Exception e){
		System.err.println ("aTLSClientSocket: Exception setting up SSLContext information: " + e);
		e.printStackTrace();
	    }
	    
	    if (DEBUG) System.err.println ("aTLSClientSocket: Creating the three stages.");
	    
	    HandshakeStage = new aTLSHandshakeStage(mgr);
	    EncryptStage = new aTLSEncryptStage (mgr);
	    RecordStage = new aTLSRecordStage(mgr, HandshakeStage.getSink(), EncryptStage.getSink());
	    initialized = true;
	}
	else {
	    if (DEBUG) System.err.println ("aTLSClientSocket: No need to create SSLContext and Stages, already created.");
	}
	
	if (DEBUG) System.err.println ("aTLSClientSocket: Enqueuing a connectRequest to the recordStage.");
	
	(RecordStage.getSink()).enqueue_lossy(new aTLSConnectRequest (this));
    }

    /**
     * This function must be called if no .cfg file used for the clients. PureTLS must have a 
     * root file, key file, random file and a password for each client, so necessary to 
     * specify the path of the files and the password.
     */
    public static void initialize(String root, String key, String random, String pass) {
	if (filesInitialized) {
	    if (DEBUG) System.err.println ("aTLSClientSocket: files already initialized.");
	    return;
	}
	rootfile = root;
	keyfile = key;
	randomfile = random;
	password = pass;
	filesInitialized = true;
    }

    /**
     * Returns clientSink.
     */
    public SinkIF getSink() {
	return clientSink;
    }

    /**
     * For now, this function only necessary if user wants to resume this client's connection.
     * Only way to reference the sessionID is with the aTLSConnection, so record stage needs to
     * call this function when a new aTLSConnection is created. Remember that the session id won't 
     * be available until the hello messages have been sent.
     */
    public void setaTLSConn (aTLSConnection atlsconn) {
	if (DEBUG) System.err.println ("aTLSClientSocket: Setting aTLSConn variable");
	this.atlsconn = atlsconn;
    }

    /**
     * This function returns the session id for this client. But session ID only determined after hello messages sent.
     */
    public byte[] getSessionID() {
	// Not sure how the error should be dealt with here
	if (atlsconn == null) {
	    System.err.println ("aTLSClientSocket: Error, user trying to access session id before aTLSConnection has been created!");
	    return null;
	}
	if (atlsconn.conn.hs.session_id == null) {
	    System.err.println ("aTLSClientSocket: Error, No session ID has been assigned yet!");
	    return null;
	}
	return atlsconn.conn.hs.session_id;
    }

    /** 
     * This function is necessary to support the ATcpClientSocket functions.
     * The recordStage stage will receive a reference to this aTLSClientSocket 
     * in the aTLSConnectRequest and will call this function when it has
     * created an ATcpClientSocket.
     */
    public void setATcpClientSocket(ATcpClientSocket atcpcs) {
	this.atcpcs = atcpcs;
    }

    /** 
     * The Sandstorm stage destroy method.
     */
    public void destroy() {
    } 
 
    /**
     * Return the InetAddress which this socket is connected to.
     */
    public InetAddress getAddress() {    
	return atcpcs.getAddress();  
    }
    
    /**
     * Return the port which this socket is connected to.
     */
    public int getPort() {
	return atcpcs.getPort();  
    }
    
    public String toString() {
	return atcpcs.toString();
    }
}







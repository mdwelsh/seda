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
import seda.sandStorm.lib.aSocket.ATcpServerSocket;

/** 
 * An aTLSServerSocket is created to begin receiving client connections on the specified port
 * It creates the three stages (record, handshake, encrypt), if not already created, 
 * and sends a listen request to the record stage. Also loads in the appropriate
 * files needed for the server (root.pem, rsa-server.pem/server.pem) and sets the policy and
 * context in PureTLS. Assuming that a config file will have the path to those files, so no
 * initialize() method like in aTLSClientSocket. 
 * An ATcpServerSocket will be created when the record stage receives the listen request.
 */

public class aTLSServerSocket extends ATcpServerSocket {

    private static final boolean DEBUG = true;
 
    static boolean initialized = false;
    static private aTLSEncryptStage EncryptStage;
    static private aTLSRecordStage RecordStage;
    static private aTLSHandshakeStage HandshakeStage;
    
    private ManagerIF mgr;
    
    private ATcpServerSocket atcpss;
    int serverPort;
    private SinkIF serverSink;
    SSLContext ctx;

    /**
     * Create an aTLSServerSocket to receive connections on the given serverPort. An aTLSConnection
     * will be posted to the given SinkIF when connection is established.
     * The three stages will be created if this is the first server socket to be instantiated.
     * Loads context and policy used for this secure connection to PureTLS, assuming those are the
     * same for all clients. Assumes that all files will be referenced in a config file.
     * An aTLSListenRequest is then posted to the Record Stage.
     */
    public aTLSServerSocket (ManagerIF mgr, SinkIF serverSink, int serverPort) throws Exception{
	super();
	if (DEBUG) System.err.println ("aTLSServerSocket: creating an aTLSServerSocket");
	
	this.mgr = mgr;
	this.serverSink = serverSink;
	this.serverPort = serverPort;
	
	ctx=new SSLContext();
	
	SSLPolicyInt policy=new SSLPolicyInt();
	
	// policy holds a list of default ciphersuites to use, unless 
	// the user provides a list - just use their default for now
	// Assuming, that not going to accept unverifiable certificates
	policy.acceptUnverifiableCertificates(false);
	
	ctx.setPolicy(policy);
	
	String rootfile;
	String keyfile;
	String password;

	rootfile = mgr.getConfig().getString("global.aTLS.rootfile");
	if (rootfile == null) {
	  throw new IllegalArgumentException("Must specify 'rootfile' option in <aTLS> section of config file");
	}

	keyfile = mgr.getConfig().getString("global.aTLS.keyfile");
	if (keyfile == null) {
	  throw new IllegalArgumentException("Must specify 'keyfile' option in <aTLS> section of config file");
	}

	password = mgr.getConfig().getString("global.aTLS.password");
	if (password == null) {
	  throw new IllegalArgumentException("Must specify 'password' option in <aTLS> section of config file");
	}

	ctx.loadRootCertificates(rootfile);
	ctx.loadEAYKeyFile(keyfile,password);

	if (!initialized) {
	  if (DEBUG) System.err.println ("aTLSServerSocket: creating the three stages.");

	  HandshakeStage = new aTLSHandshakeStage(mgr);
	  EncryptStage = new aTLSEncryptStage (mgr);
	  RecordStage = new aTLSRecordStage(mgr, HandshakeStage.getSink(), EncryptStage.getSink());
	  initialized = true;
	}
	if (DEBUG)  System.err.println ("aTLSServerSocket: sending a listenRequest to the recordStage.");

	(RecordStage.getSink()).enqueue_lossy(new aTLSListenRequest (this));
    }

    /**
     * Returns serverSink.
     */
    public SinkIF getSink () {
      return serverSink;
    }

    /**
     * Return the port that this socket is listening on.
     */
    public int getPort() {
	return atcpss.getPort();  
    }

     /**
      * Return the local port for this socket. 
      */
    public int getLocalPort() {
	return atcpss.getLocalPort();
    }

    /** 
     * This function is necessary to support the suspend/resumeAccept()
     * functions for ATcpServerSocket..
     * The recordStage stage will receive a reference to this aTLSClientSocket 
     * in the aTLSListenRequest and will call this function when it has
     * created an ATcpClientSocket.
     */
    public void setATcpServerSocket(ATcpServerSocket atcpss) {
	this.atcpss = atcpss;
    }

    /** 
     * Suspend acceptance of new connections using ATcpServerSocket method
     */
    public void suspendAccept() {
	atcpss.suspendAccept();
    }

    /** 
     * Resume acceptance of new connections using ATcpServerSocket method
     */
    public void resumeAccept() {
	atcpss.resumeAccept();
    }

    /** 
     * The Sandstorm stage destroy method.
     */
    public void destroy() {
    }

    /**
     * Asynchronously close this server socket. An ATcpServerSocketClosedEvent
     * will be posted to the completion queue associated with this
     * server socket when the close completes.
     */
    public void close() {
	atcpss.close();
    }
}


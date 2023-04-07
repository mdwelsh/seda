/* 
 * Copyright (c) 2000 by Matt Welsh and The Regents of the University of 
 * California. All rights reserved.
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
 * Author: Matt Welsh <mdw@cs.berkeley.edu>
 * 
 */

package seda.sandStorm.lib.aSocket;

import seda.sandStorm.api.SinkIF;

import java.io.IOException;

/** 
 * This class represents an asynchronous server socket.
 * An application creates an ATcpServerSocket to listen for incoming
 * TCP connections on a given port; when a connection is received,
 * an ATcpConnection object is pushed to the SinkIF associated with 
 * the ATcpServerSocket. The ATcpConnection is then used for communication.
 *
 * @author Matt Welsh
 * @see ATcpConnection
 *
 */
public class ATcpServerSocket {

  /** Internal state used by aSocket implementation */
  public ListenSockState lss;
  int serverPort;

  /**
   * Open a server socket listening on the given port. When a connection 
   * arrives, an ATcpConnection will be posted to the given compQ. 
   * If the server socket dies, an ATcpServerSocketDeadEvent will be
   * posted instead.
   */
  public ATcpServerSocket(int serverPort, SinkIF compQ) throws IOException {
    this.serverPort = serverPort;
    aSocketMgr.enqueueRequest(new ATcpListenRequest(this, serverPort, compQ, -1));
  }

  /**
   * Open a server socket listening on the given port. When a connection 
   * arrives, an ATcpConnection will be posted to the given compQ. 
   * If the server socket dies, an ATcpServerSocketDeadEvent will be
   * posted instead.
   *
   * @param writeClogThreshold The maximum number of outstanding write 
   *   requests to a connection established using this socket before a
   *   SinkCloggedEvent is pushed onto the completion queue for that
   *   connection. The default value is -1, which indicates that no
   *   SinkCloggedEvents will be generated.
   */
  public ATcpServerSocket(int serverPort, SinkIF compQ, 
    int writeClogThreshold) throws IOException {
    this.serverPort = serverPort;
    aSocketMgr.enqueueRequest(new ATcpListenRequest(this, serverPort, compQ, writeClogThreshold));
  }

  protected ATcpServerSocket() {
  }

  /**
   * Request that this server socket stop accepting new connections.
   * This request will not take effect immediately.
   */
  public void suspendAccept() {
    aSocketMgr.enqueueRequest(new ATcpSuspendAcceptRequest(this));
  }

  /**
   * Request that this server socket resume accepting new connections.
   * This request will not take effect immediately.
   */
  public void resumeAccept() {
    aSocketMgr.enqueueRequest(new ATcpResumeAcceptRequest(this));
  }

  /**
   * Return the port that this socket is listening on.
   */
  public int getPort() {
    return serverPort;  
  }

  /**
   * Return the local port for this socket. Returns -1 if no local port
   * has yet been established.
   */
  public int getLocalPort() {
    if (lss != null) {
      return lss.getLocalPort();
    } else return -1;
  }

  /**
   * Asynchronously close this server socket. An ATcpServerSocketClosedEvent
   * will be posted to the completion queue associated with this
   * server socket when the close completes.
   */
  public void close() {
    aSocketMgr.enqueueRequest(new ATcpCloseServerRequest(this));
  }

}


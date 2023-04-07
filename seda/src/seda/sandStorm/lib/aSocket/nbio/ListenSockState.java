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

package seda.sandStorm.lib.aSocket.nbio;

import seda.nbio.NonblockingServerSocket;
import seda.nbio.NonblockingSocket;
import seda.nbio.SelectItem;
import seda.nbio.Selectable;
import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

/**
 * Internal class used to represent a server socket listening on a 
 * given port.
 */
public class ListenSockState extends seda.sandStorm.lib.aSocket.ListenSockState {

  private static final boolean DEBUG = false;

  NonblockingServerSocket nbservsock;
  private SelectItem si;
  private SelectSource listen_selsource;

  public ListenSockState(ATcpListenRequest req, SelectSourceIF listen_selsource) throws IOException {
    this(req);
    this.listen_selsource = (SelectSource)listen_selsource;
    System.out.println("nbservsock = " + nbservsock);
    si = new SelectItem(nbservsock, this, Selectable.ACCEPT_READY);
    listen_selsource.register(si); 
  }

  ListenSockState(ATcpListenRequest req) throws IOException {
    this.port = req.port;
    this.compQ = req.compQ;
    this.writeClogThreshold = req.writeClogThreshold;
    if (DEBUG) System.err.println("ListenThread: Creating nbservsock on port "+port);

    this.servsock = req.servsock;
    try {
      nbservsock = new NonblockingServerSocket(port);
    } catch (IOException ioe) {
      // Can't create socket - probably because the address was 
      // already in use
      ATcpListenFailedEvent ev = new ATcpListenFailedEvent(servsock, ioe.getMessage());
      compQ.enqueue_lossy(ev);
      return;
    }
    this.servsock.lss = this;
    compQ.enqueue_lossy(new ATcpListenSuccessEvent(servsock));
  }

  protected Socket accept() throws IOException {
    if (nbservsock == null) return null; // If already closed
    NonblockingSocket nbsock;
    try {
      if (DEBUG) System.err.println("LSS: Calling nbservsock.nbAccept");
      nbsock = nbservsock.nbAccept();
      if (DEBUG) System.err.println("LSS: nbservsock.nbAccept returned "+nbsock);
    } catch (SocketException e) {
      // Assume this is a 'Too many open files' exception
      // XXX Probably want to throttle the listenthread somehow?
      if (DEBUG) System.err.println("LSS: nbservsock.nbAccept got SocketException "+e);
      return null;

    } catch (IOException e) {
      System.err.println("LSS: nbAccept got IOException: "+e);
      e.printStackTrace();

      ATcpServerSocketClosedEvent dead = new ATcpServerSocketClosedEvent(servsock);
      compQ.enqueue_lossy(dead);
      // Deregister
      listen_selsource.deregister(si); 
      throw e;
    }

    return nbsock;
  }

  protected void suspend() {
    if (nbservsock == null) return; // If already closed
    System.err.println("LSS: Suspending accept on "+servsock);
    si.events &= ~(Selectable.ACCEPT_READY);
    listen_selsource.update(si);
  }

  protected void resume() {
    if (nbservsock == null) return; // If already closed
    System.err.println("LSS: Resuming accept on "+servsock);
    si.events |= Selectable.ACCEPT_READY;
    listen_selsource.update(si);
  }

  protected void close() {
    if (nbservsock == null) return; // If already closed
    listen_selsource.deregister(si);
    listen_selsource = null;
    try {
      nbservsock.close();
    } catch (IOException e) {
      // Ignore
    }
    nbservsock = null;
    ATcpServerSocketClosedEvent closed = new ATcpServerSocketClosedEvent(servsock);
    compQ.enqueue_lossy(closed);
  }

  protected void complete(ATcpConnection conn) {
    if (DEBUG) System.err.println("LSS: complete called on conn "+conn);
    if (!compQ.enqueue_lossy(conn)) {
      if (DEBUG) System.err.println("LSS: Could not enqueue_lossy new conn "+conn);
    }
  }

  public int getLocalPort() {
    return nbservsock.getLocalPort();
  }

}


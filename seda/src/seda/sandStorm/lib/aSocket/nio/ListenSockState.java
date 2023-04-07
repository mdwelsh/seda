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

package seda.sandStorm.lib.aSocket.nio;

import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
/**
 * Internal class used to represent a server socket listening on a 
 * given port.
 */
public class ListenSockState extends seda.sandStorm.lib.aSocket.ListenSockState {

  private static final boolean DEBUG = false;

  ServerSocketChannel nio_servsock;
  private SelectionKey selkey;
  private NIOSelectSource listen_nio_selsource;

/*  ATcpServerSocket servsock;
  private static int num_connections = 0;
  private int port;
  private SinkIF compQ;
  int writeClogThreshold; */

  public ListenSockState(ATcpListenRequest req, SelectSourceIF listen_nio_selsource) throws IOException {
    this(req);
    this.listen_nio_selsource = (NIOSelectSource)listen_nio_selsource;
    this.listen_nio_selsource.setName("ListenSelectSource");
    selkey = (SelectionKey)listen_nio_selsource.register(nio_servsock, SelectionKey.OP_ACCEPT);
    selkey.attach(this);
  }

  protected ListenSockState(ATcpListenRequest req) throws IOException {
    this.port = req.port;
    this.compQ = req.compQ;
    this.writeClogThreshold = req.writeClogThreshold;
    if (DEBUG) System.err.println("ListenThread: Creating nio_servsock on port "+port);

    this.servsock = req.servsock;
    try {
      nio_servsock = ServerSocketChannel.open();
      nio_servsock.configureBlocking(false);
      nio_servsock.socket().bind(new InetSocketAddress(port));
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
    if (nio_servsock == null) return null;
    Socket sock;
    try {
      if (DEBUG) System.err.println("LSS: Calling nio_servsock.accept");
      // XXX: why does this give back a socket, rather than a socketChannel?
      SocketChannel sc = nio_servsock.accept();
      if (sc == null)
          return null;

      sock = sc.socket();
      if (DEBUG) System.err.println("LSS: nio_servsock.accept returned "+sock);
      sock.getChannel().configureBlocking(false);
    } catch (SocketException e) {
      if (DEBUG) System.err.println("LSS: nio_servsock.accept got SocketException "+e);
      return null;

    } catch (IOException e) {
      System.err.println("LSS: accept got IOException: "+e);
      e.printStackTrace();

      ATcpServerSocketClosedEvent dead = new ATcpServerSocketClosedEvent(servsock);
      compQ.enqueue_lossy(dead);
      // Deregister
      listen_nio_selsource.deregister(selkey); 
      throw e;
    }

    return sock;
  }

  protected void suspend() {
    if (nio_servsock == null) return; // If already closed
    System.err.println("LSS: Suspending accept on "+servsock);
    selkey.interestOps(selkey.interestOps() & ~(SelectionKey.OP_ACCEPT));
    listen_nio_selsource.update(selkey);
  }

  protected void resume() {
    if (nio_servsock == null) return; // If already closed
    System.err.println("LSS: Resuming accept on "+servsock);
    selkey.interestOps(selkey.interestOps() | SelectionKey.OP_ACCEPT);
    listen_nio_selsource.update(selkey);
  }

  protected void close() {
    if (nio_servsock == null) return; // If already closed
    listen_nio_selsource.deregister(selkey);
    listen_nio_selsource = null;
    try {
        nio_servsock.close();
    } catch (IOException e) {
      // Ignore
    }
    nio_servsock = null;

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
    return nio_servsock.socket().getLocalPort();
  } 


  public Object getSocket() {
    if (nio_servsock != null)
      return nio_servsock.socket();
    else
      return null;
  }
}


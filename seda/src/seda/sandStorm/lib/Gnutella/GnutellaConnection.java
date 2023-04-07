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

package seda.sandStorm.lib.Gnutella;

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkClosedException;
import seda.sandStorm.api.SinkException;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.BufferElement;
import seda.sandStorm.core.SimpleSink;
import seda.sandStorm.lib.aSocket.ATcpConnection;

import java.net.InetAddress;

/**
 * A GnutellaConnection represents a virtual connection to the
 * Gnutella network. It may implement a single point-to-point connection
 * between two peers, or a one-to-many connection to multiple peers.
 *
 * @author Matt Welsh (mdw@cs.berkeley.edu)
 */
public class GnutellaConnection extends SimpleSink implements QueueElementIF, GnutellaConst {

  private static final boolean DEBUG = false;

  private GnutellaServer gs;
  private GnutellaPacketReader gpr;
  private ATcpConnection conn;
  private InetAddress addr;
  private int port;
  private boolean closed;

  GnutellaConnection(GnutellaServer gs, ATcpConnection conn) {
    this.gs = gs;
    this.conn = conn;
    this.gpr = new GnutellaPacketReader();
    addr = conn.getAddress();
    port = conn.getPort();
    closed = false;
  }

  /**
   * Return the InetAddress of the peer. Returns null if this is a 
   * 'virtual' connection.
   */
  public InetAddress getAddress() {
    return addr;
  }

  /**
   * Return the port of the peer. Returns -1 if this is a 'virtual'
   * connection.
   */
  public int getPort() {
    return port;
  }

  /**
   * Send a ping to this connection.
   */
  public void sendPing() {
    GnutellaPingPacket ping = new GnutellaPingPacket();
    enqueue_lossy(ping);
  }

  /**
   * Send a ping to this connection with the given TTL.
   */
  public void sendPing(int ttl) {
    GnutellaPingPacket ping = new GnutellaPingPacket(new GnutellaGUID(), ttl, 0);
    enqueue_lossy(ping);
  }

  // Package access only
  ATcpConnection getConnection() {
    return conn;
  }

  // Package access only
  GnutellaPacketReader getReader() {
    return gpr;
  }

  /* SinkIF methods ******************************************************/

  public void enqueue(QueueElementIF element) throws SinkException {
    GnutellaPacket packet = (GnutellaPacket)element;
    BufferElement buf = packet.getBuffer();
    buf.compQ = gs.getSink();
    conn.enqueue(buf);
  }

  public boolean enqueue_lossy(QueueElementIF element) {
    GnutellaPacket packet = (GnutellaPacket)element;
    BufferElement buf = packet.getBuffer();
    buf.compQ = gs.getSink();
    return conn.enqueue_lossy(buf);
  }

  public void enqueue_many(QueueElementIF elements[]) throws SinkException {
    for (int i = 0; i < elements.length; i++) {
      enqueue(elements[i]);
    }
  }

  public int size() {
    return conn.size();
  }

  public void close(SinkIF compQ) throws SinkClosedException {
    // XXX For now, allow a connection to be closed multiple times
    // This is because clogged connections may push multiple
    // clogged events up, causing the server to attempt to close
    // multiple times
    //if (closed) throw new SinkClosedException("GnutellaConnection already closed!");
    gs.cleanupConnection(conn, this);
    gs.closeConnection(conn, compQ);
    closed = true;
  }

  public void flush(SinkIF compQ) throws SinkClosedException {
    conn.flush(compQ);
  }

  public Object enqueue_prepare(QueueElementIF enqueueMe[]) throws SinkException {
    return conn.enqueue_prepare(enqueueMe);
  }

  public void enqueue_commit(Object key) {
    conn.enqueue_commit(key);
  }

  public void enqueue_abort(Object key) {
    conn.enqueue_abort(key);
  }

  public String toString() {
    return "GnutellaConnection ["+addr.getHostAddress()+":"+port+"]";
  }


}

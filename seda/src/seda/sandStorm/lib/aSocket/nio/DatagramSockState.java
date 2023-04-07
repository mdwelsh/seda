/* 
 * Copyright (c) 2001 by Matt Welsh and The Regents of the University of 
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

import seda.sandStorm.api.*;
import seda.sandStorm.core.ssLinkedList;
import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

/**
 * Internal class used to represent state of an active datagram socket.
 */
public class DatagramSockState extends seda.sandStorm.lib.aSocket.DatagramSockState {

  private static final boolean DEBUG = false;

  private SelectionKey rselkey, wselkey;
  private DatagramChannel nio_dgsock;

/*  AUdpSocket udpsock;
  private SinkIF readCompQ;
  private QueueElementIF clogged_qel;
  private int clogged_numtries;
  private int readClogTries, writeClogThreshold, maxPacketSize;

  private byte readBuf[];
  private ByteBuffer nio_readbuf;
  boolean closed = false;
  long seqNum = 1;
  private AUdpInPacket pkt;

  int outstanding_writes, numEmptyWrites;
  ssLinkedList writeReqList;
  AUdpWriteRequest cur_write_req; 
  BufferElement cur_write_buf; */

  private NIOSelectSource nio_read_selsource;
  private NIOSelectSource nio_write_selsource;
  private ByteBuffer nio_readbuf;
  private boolean connected;

  public DatagramSockState(AUdpSocket sock, InetAddress addr, int port) throws IOException {
    if (DEBUG) System.err.println("DatagramSockState: Constructor called");
    this.udpsock = sock;
    this.readCompQ = sock.compQ;
    this.writeClogThreshold = sock.writeClogThreshold;
    this.maxPacketSize = sock.maxPacketSize;
    this.connected = false;

    readBuf = new byte[maxPacketSize];
    this.nio_write_selsource = null;
    
    nio_readbuf = ByteBuffer.wrap(readBuf);

    if (DEBUG) System.err.println("DatagramSockState : setting up socket");
    this.nio_dgsock = DatagramChannel.open();
    this.nio_dgsock.configureBlocking(false);
    this.nio_dgsock.socket().bind(
        new InetSocketAddress(addr, port)
    );

    if (DEBUG) System.err.println("DatagramSockState "+nio_dgsock+": Setting flags");
    outstanding_writes = 0;
    numEmptyWrites = 0;
    writeReqList = new ssLinkedList();
    clogged_qel = null;
    clogged_numtries = 0;
    if (DEBUG) System.err.println("DatagramSockState "+nio_dgsock+": Const done");
  }

  // This is synchronized with close() 
  protected synchronized void readInit(SelectSourceIF read_selsource, SinkIF compQ, int readClogTries) {
    if (DEBUG) System.err.println("readInit called on "+this);
    if (DEBUG) System.err.println("read_selsource = " + read_selsource);
    if (closed) return; // May have been closed already
    this.readCompQ = compQ;
    this.readClogTries = readClogTries;
    this.nio_read_selsource = (NIOSelectSource)read_selsource;
    rselkey =
        (SelectionKey)this.nio_read_selsource.register(nio_dgsock,
            SelectionKey.OP_READ);
    rselkey.attach(this);
  }

  protected void doRead() {
    if (DEBUG) System.err.println("DatagramSockState: doRead called");

    // When using SelectSource, we need this guard, since after closing 
    // a socket we may have outstanding read events still in the queue
    if (closed) return;

    if (clogged_qel != null) {
      // Try to drain the clogged element first
      if (DEBUG) System.err.println("DatagramSockState: doRead draining clogged element "+clogged_qel);
      try {
	readCompQ.enqueue(clogged_qel);
      } catch (SinkFullException qfe) {
	// Nope, still clogged
	if ((readClogTries != -1) &&
	    (++clogged_numtries >= readClogTries)) {
	  if (DEBUG) System.err.println("DatagramSockState: warning: readClogTries exceeded, dropping "+clogged_qel);
	  clogged_qel = null;
	  clogged_numtries = 0;
	} else {
	  // Try again later
	  return;
	}
      } catch (SinkException sce) {
	// Whoops - user went away - just drop
	this.close(null);
      }
    }

    int len;
    DatagramPacket p;

    try {
      if (DEBUG) System.err.println("DatagramSockState: doRead trying receive");
      SocketAddress peersa = nio_dgsock.receive(nio_readbuf);
      len = nio_readbuf.position();
      p = new DatagramPacket(readBuf, 0, len);
      p.setSocketAddress(peersa);
      p.setLength(len);

      if (DEBUG) System.err.println("DatagramSockState: receive returned "+len);

      if (len == 0) {
	// Didn't read anything - just drop
	return;
      } else if (len < 0) {
	// Read failed - assume socket is dead
	if (DEBUG) System.err.println("dgss.doRead: read failed, sock closed");
	this.close(readCompQ);
	return;
      }
    } catch (Exception e) {
      // Read failed - assume socket is dead
      if (DEBUG) System.err.println("dgss.doRead: read got IOException: "+e.getMessage() + e);
      this.close(readCompQ);
      return;
    }

    if (DEBUG) System.err.println("dgss.doRead: Pushing up new AUdpInPacket, len="+len);

    pkt = new AUdpInPacket(udpsock, p, seqNum);
    System.err.println("pkt.size()="+pkt.size());
    // 0 is special (indicates no sequence number)
    seqNum++; if (seqNum == 0) seqNum = 1;
    // XXX Should really test for READ_BUFFER_COPY here as in SockState.java
    readBuf = new byte[maxPacketSize];
    nio_readbuf = ByteBuffer.wrap(readBuf);

    try {
      readCompQ.enqueue(pkt);
    } catch (SinkFullException qfe) {
      clogged_qel = pkt;
      clogged_numtries = 0;
      return;
    } catch (SinkException sce) {
      // User has gone away
      this.close(null);
      return;
    }
  }

  // This is synchronized with close() to avoid a race with close()
  // removing the writeReqList while this method is being called.
  // Probably a better way to do this...
  protected synchronized boolean addWriteRequest(aSocketRequest req, SourceIF write_selsource) {
    if (closed) return false;

    if (DEBUG) System.err.println("DatagramSockState: addWriteRequest called");

    if (this.nio_write_selsource == null) {
        if (DEBUG) System.err.println("DatagramSockState: Setting selsource to "+write_selsource);
        this.nio_write_selsource = (NIOSelectSource)write_selsource;
        wselkey = (SelectionKey)this.nio_write_selsource.register(
            nio_dgsock, SelectionKey.OP_WRITE
        );
        if (wselkey == null) return false;
        wselkey.attach(this);
        if (DEBUG) System.err.println("dgSockState: Registered with selkey");
    } else if (this.outstanding_writes == 0) {
        numEmptyWrites = 0;
        writeMaskEnable();
    }

    if ((writeClogThreshold != -1) &&
	(this.outstanding_writes > writeClogThreshold)) {
      if (DEBUG) System.err.println("DatagramSockState: warning: writeClogThreshold exceeded, dropping "+req);
      if (req instanceof AUdpWriteRequest) return false;
      if (req instanceof AUdpCloseRequest) {
	// Do immediate close: Assume socket is clogged
	AUdpCloseRequest creq = (AUdpCloseRequest)req;
	this.close(creq.compQ);
	return true;
      }
    }

    if (DEBUG) System.err.println("DatagramSockState: Adding writeReq to tail");
    writeReqList.add_to_tail(req);
    this.outstanding_writes++;
    return true;
  }

  // does not override inherited method from DatagramSocketState since it is
  // private to a different package
  void initWrite(AUdpWriteRequest req) {
    this.cur_write_req = req;
    this.cur_write_buf = req.buf;
  }

  protected boolean tryWrite() throws SinkClosedException {
    int ret;
    ByteBuffer bb;
    DatagramPacket outgoing;
    InetAddress send_addr;
    int send_port;

    try {
      if (cur_write_buf instanceof AUdpPacket) {
	AUdpPacket udpp = (AUdpPacket)cur_write_buf;
	bb = ByteBuffer.wrap(udpp.data, udpp.offset, udpp.size);
	InetSocketAddress isa = new InetSocketAddress(udpp.address, udpp.port);
	ret = nio_dgsock.send(bb, isa);
      } else {
	// XXX Should check if !connected and throw IOException 
	// (not just SinkClosedException)
	bb = ByteBuffer.wrap(cur_write_buf.data, cur_write_buf.offset, cur_write_buf.size);
	ret = nio_dgsock.write(bb);
      }

    } catch (IOException ioe) {
      // Assume this is because socket was already closed
      if (DEBUG) System.err.println("dgss.tryWrite(): Got exception: "+ioe);
      this.close(null);
      throw new SinkClosedException("DatagramSockState: tryWrite got exception doing write: "+ioe.getMessage());
    }
    if (ret == cur_write_buf.size) return true;
    else return false;
  }

  // does not override inherited method from DatagramSocketState since it is
  // private to a different package
  void writeReset() {
    this.cur_write_req = null;
    this.outstanding_writes--;
  }

  protected void writeMaskEnable() {
    wselkey.interestOps(wselkey.interestOps() | SelectionKey.OP_WRITE);
  }

  protected void writeMaskDisable() {
    wselkey.interestOps(wselkey.interestOps() & ~SelectionKey.OP_WRITE);
  }

  // This is synchronized to avoid close() interfering with
  // addWriteRequest
  protected synchronized void close(seda.sandStorm.api.SinkIF closeEventQueue) {
    if (closed) return;

    closed = true;

    if (DEBUG) System.err.println("DatagramSockState.close(): Deregistering with selsources");
    if (nio_read_selsource != null) nio_read_selsource.deregister(rselkey);
    if (nio_write_selsource != null) nio_write_selsource.deregister(wselkey);
    if (DEBUG) System.err.println("DatagramSockState.close(): done deregistering with selsources");
    // Eliminate write queue

    writeReqList = null;

    if (DEBUG) System.err.println("DatagramSockState.close(): doing close");
    try {
        nio_dgsock.close();
    } catch (IOException ioe) {
        System.err.println("Error closing socket: " + ioe);
    }

    if (closeEventQueue != null) {
      SinkClosedEvent sce = new SinkClosedEvent(udpsock);
      closeEventQueue.enqueue_lossy(sce);
    }
  }

  public String toString() {
    return "DatagramSockState [" + nio_dgsock + "]";
  }

  protected DatagramSocket getSocket() {
      return nio_dgsock.socket();
  }

  protected void connect(InetAddress addr, int port) {
      try {
      	nio_dgsock.connect(new InetSocketAddress(addr, port));
	this.connected = true;
      } catch (IOException ioe) {
          System.err.println("DatagramSockState: Error connecting: " + ioe);
      }
  }
}

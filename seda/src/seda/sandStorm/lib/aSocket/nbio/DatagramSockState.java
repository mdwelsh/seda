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

package seda.sandStorm.lib.aSocket.nbio;

import seda.nbio.NonblockingDatagramSocket;
import seda.nbio.SelectItem;
import seda.nbio.Selectable;
import seda.sandStorm.api.*;
import seda.sandStorm.core.ssLinkedList;
import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Internal class used to represent state of an active datagram socket.
 */
public class DatagramSockState extends seda.sandStorm.lib.aSocket.DatagramSockState {

  private static final boolean DEBUG = false;

  private NonblockingDatagramSocket dgsock;
  private SelectItem readsi, writesi;

/*  AUdpSocket udpsock;
  private SinkIF readCompQ;
  private QueueElementIF clogged_qel;
  private int clogged_numtries;
  private int readClogTries, writeClogThreshold, maxPacketSize;

  private byte readBuf[];
  boolean closed = false;
  long seqNum = 1;
  private AUdpInPacket pkt;

  int outstanding_writes, numEmptyWrites;
  ssLinkedList writeReqList;
  AUdpWriteRequest cur_write_req;
  BufferElement cur_write_buf; */

  private SelectSource read_selsource;
  private SelectSource write_selsource;
//  private BufferElement cur_write_buf;

  public DatagramSockState(AUdpSocket sock, InetAddress addr, int port) throws IOException {
    if (DEBUG) System.err.println("DatagramSockState: Constructor called");
    this.udpsock = sock;
    this.readCompQ = sock.compQ;
    this.writeClogThreshold = sock.writeClogThreshold;
    this.maxPacketSize = sock.maxPacketSize;

    if (DEBUG) System.err.println("DatagramSockState : setting up socket");
    this.dgsock = new NonblockingDatagramSocket(port, addr);

    readBuf = new byte[maxPacketSize];
    this.write_selsource = null;
    if (DEBUG) System.err.println("DatagramSockState "+dgsock+": Const creating readBuf of size "+maxPacketSize);

    if (DEBUG) System.err.println("DatagramSockState "+dgsock+": Setting flags");
    outstanding_writes = 0;
    numEmptyWrites = 0;
    writeReqList = new ssLinkedList();
    clogged_qel = null;
    clogged_numtries = 0;
    if (DEBUG) System.err.println("DatagramSockState "+dgsock+": Const done");
  }

  // This is synchronized with close() 
  protected synchronized void readInit(SelectSourceIF read_selsource, SinkIF compQ, int readClogTries) {
    if (DEBUG) System.err.println("readInit called on "+this);
    if (DEBUG) System.err.println("read_selsource = " + read_selsource);
    if (closed) return; // May have been closed already
    this.readCompQ = compQ;
    this.readClogTries = readClogTries;
    this.read_selsource = (SelectSource)read_selsource;
    readsi = new SelectItem(dgsock, this, Selectable.READ_READY);
    this.read_selsource.register(readsi); 
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
      p = new DatagramPacket(readBuf, 0, readBuf.length);
      len = dgsock.nbReceive(p);
      if (DEBUG) System.err.println("DatagramSockState: receive returned "+len);

      if (len == 0) {
	// Didn't read anything - just drop
	readsi.revents = 0;
	return;
      } else if (len < 0) {
	// Read failed - assume socket is dead
	if (DEBUG) System.err.println("dgss.doRead: read failed, sock closed");
	this.close(readCompQ);
	readsi.revents = 0;
	return;
      }
    } catch (Exception e) {
      // Read failed - assume socket is dead
      if (DEBUG) System.err.println("dgss.doRead: read got IOException: "+e.getMessage());
      this.close(readCompQ);
      readsi.revents = 0;
      return;
    }

    if (DEBUG) System.err.println("dgss.doRead: Pushing up new AUdpInPacket, len="+len);

    pkt = new AUdpInPacket(udpsock, p, seqNum);
    // 0 is special (indicates no sequence number)
    seqNum++; if (seqNum == 0) seqNum = 1;
    readBuf = new byte[maxPacketSize];

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
    readsi.revents = 0;
  }

  // This is synchronized with close() to avoid a race with close()
  // removing the writeReqList while this method is being called.
  // Probably a better way to do this...
  protected synchronized boolean addWriteRequest(aSocketRequest req, SourceIF write_selsource) {
    if (closed) return false;

    if (DEBUG) System.err.println("DatagramSockState: addWriteRequest called");

    if (this.write_selsource == null) {
      if (DEBUG) System.err.println("DatagramSockState: Setting selsource to "+write_selsource);
      this.write_selsource = (SelectSource)write_selsource;
      writesi = new SelectItem(dgsock, this, Selectable.WRITE_READY);
      this.write_selsource.register(writesi);
      if (DEBUG) System.err.println("SockState: Registered with selsource");
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
    DatagramPacket outgoing;

    try {
      if (cur_write_buf instanceof AUdpPacket) {
	AUdpPacket udpp = (AUdpPacket)cur_write_buf;
	outgoing = new DatagramPacket(udpp.data, udpp.offset, udpp.size, udpp.address, udpp.port);
      } else {
	outgoing = new DatagramPacket(cur_write_buf.data, cur_write_buf.offset, cur_write_buf.size);
      }
       ret = dgsock.nbSend(outgoing);
    } catch (IOException ioe) {
      // Assume this is because socket was already closed
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
    writesi.events |= Selectable.WRITE_READY;
    write_selsource.update(writesi);
  }

  protected void writeMaskDisable() {
    writesi.events &= ~(Selectable.WRITE_READY);
    write_selsource.update(writesi);
  }

  // does not override inherited method from DatagramSocketState since it is
  // private to a different package
  boolean isClosed() {
    return closed;
  }

  // This is synchronized to avoid close() interfering with
  // addWriteRequest
  protected synchronized void close(SinkIF closeEventQueue) {
    if (closed) return;

    closed = true;

    if (DEBUG) System.err.println("DatagramSockState.close(): Deregistering with selsources");
    if (read_selsource != null) read_selsource.deregister(readsi); 
    if (write_selsource != null) write_selsource.deregister(writesi); 
    if (DEBUG) System.err.println("DatagramSockState.close(): done deregistering with selsources");
    // Eliminate write queue

    writeReqList = null;

    if (DEBUG) System.err.println("DatagramSockState.close(): doing close");
    dgsock.close();

    if (closeEventQueue != null) {
      SinkClosedEvent sce = new SinkClosedEvent(udpsock);
      closeEventQueue.enqueue_lossy(sce);
    }
  }

  public String toString() {
    return "DatagramSockState ["+dgsock+"]";
  }

  protected DatagramSocket getSocket() {
    return dgsock;
  }

  protected void connect(InetAddress addr, int port) {
    dgsock.connect(addr, port);
  }

}


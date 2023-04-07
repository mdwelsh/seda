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

import seda.sandStorm.api.*;
import seda.sandStorm.core.ssLinkedList;
import seda.sandStorm.lib.aSocket.*;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

/**
 * Internal class used to represent state of an active socket connection.
 */
public class SockState extends seda.sandStorm.lib.aSocket.SockState {

  private static final boolean DEBUG = false;

  private SelectionKey rselkey, wselkey;
  private ByteBuffer byte_buffer, read_byte_buffer; 

  private NIOSelectSource read_selsource, write_selsource;

  SockState(ATcpConnection conn, Socket nbsock, int writeClogThreshold) throws IOException {
    if (DEBUG) System.err.println("SockState: Constructor called with "+conn+", "+nbsock+", "+writeClogThreshold);
    this.conn = conn;
    this.nbsock = nbsock;
    this.writeClogThreshold = writeClogThreshold;
    this.write_selsource = null;

    if (DEBUG) System.err.println("SockState "+nbsock+": Const creating readBuf of size "+aSocketConst.READ_BUFFER_SIZE);
    readBuf = new byte[aSocketConst.READ_BUFFER_SIZE];
    read_byte_buffer = ByteBuffer.wrap(readBuf);

    if (DEBUG) System.err.println("SockState "+nbsock+": Setting flags");
    outstanding_writes = 0;
    numEmptyWrites = 0;
    writeReqList = new ssLinkedList();

    clogged_qel = null;
    clogged_numtries = 0;
    if (DEBUG) System.err.println("SockState "+nbsock+": Const done");
  }

  public SockState(ATcpConnection conn, Socket nbsock, Integer writeClogThreshold) throws IOException {
      this(conn, nbsock, writeClogThreshold.intValue());
    }

  // This is synchronized with close() 
  protected synchronized void readInit(SelectSourceIF read_selsource, SinkIF compQ, int readClogTries) {
    if (DEBUG) System.err.println("readInit called on "+this);
    if (closed) return; // May have been closed already
    this.read_selsource = (NIOSelectSource)read_selsource;
    this.read_selsource.setName("ReadSelectSource");
    this.readCompQ = compQ;
    this.readClogTries = readClogTries;
    if (DEBUG) System.err.println("n_keys = " + ((NIOSelectSource)read_selsource).getSelector().keys().size());
    rselkey = (SelectionKey)read_selsource.register(
        nbsock.getChannel(), SelectionKey.OP_READ
    );
    if (rselkey == null) {
        System.err.println("SockState: register returned null");
        return;
    }
    rselkey.attach(this);
  }

  protected void doRead() {
    if (DEBUG) System.err.println("SockState: doRead called");

    // When using SelectSource, we need this guard, since after closing 
    // a socket we may have outstanding read events still in the queue
    if (closed) return;

    if (clogged_qel != null) {
      // Try to drain the clogged element first
      if (DEBUG) System.err.println("SockState: doRead draining clogged element "+clogged_qel);
      try {
	readCompQ.enqueue(clogged_qel);
	clogged_qel = null;
	clogged_numtries = 0;
      } catch (SinkFullException qfe) {
	// Nope, still clogged
	if ((readClogTries != -1) &&
	    (++clogged_numtries >= readClogTries)) {
	  if (DEBUG) System.err.println("SockState: warning: readClogTries exceeded, dropping "+clogged_qel);
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

    try {
      if (DEBUG) System.err.println("SockState: doRead trying read");
      len = nbsock.getChannel().read(read_byte_buffer);
      if (DEBUG) System.err.println("SockState: read returned "+len);

      if (len == 0) {
	// XXX MDW: Sometimes we get an error return result from
	// poll() which causes an attempted read here, but no
	// IOException. For now I am going to just drop the "null"
	// packet - on Linux it seems that certain TCP errors can
	// trigger this.
	// System.err.println("ss.doRead: Warning: Got empty read on socket");
        return;
      } else if (len < 0) {
	// Read failed - assume socket is dead
	if (DEBUG) System.err.println("ss.doRead: read failed, sock closed");
	this.close(readCompQ);
	return;
      }
    } catch (Exception e) {
      // Read failed - assume socket is dead
      if (DEBUG) System.err.println("ss.doRead: read got IOException: "+e.getMessage());
      this.close(readCompQ);
      return;
    }

    if (DEBUG) System.err.println("ss.doRead: Pushing up new ATcpInPacket, len="+len);

    pkt = new ATcpInPacket(conn, readBuf, len, aSocketConst.READ_BUFFER_COPY, seqNum);
    // 0 is special (indicates no sequence number)
    seqNum++; if (seqNum == 0) seqNum = 1;
    if (aSocketConst.READ_BUFFER_COPY == false) {
      readBuf = new byte[aSocketConst.READ_BUFFER_SIZE];
      read_byte_buffer = ByteBuffer.wrap(readBuf);
    }

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
      read_byte_buffer.rewind();
  }

  // XXX This is synchronized with close() to avoid a race with close()
  // removing the writeReqList while this method is being called.
  // Probably a better way to do this...
  protected synchronized boolean addWriteRequest(aSocketRequest req, SelectSourceIF write_selsource) {
    if (closed) return false;

    if (DEBUG) System.err.println("SockState: addWriteRequest called");

    if (this.write_selsource == null) {
        if (DEBUG) System.err.println("SockState: Setting selsource to " + write_selsource);
        if (DEBUG) {
	  if (read_selsource == null) {
	    System.err.println("w/r=" + ((NIOSelectSource)write_selsource).getSelector() + "/null");
	  } else {
	    System.err.println("w/r=" + ((NIOSelectSource)write_selsource).getSelector() + "/" + ((NIOSelectSource)read_selsource).getSelector());
	  }
	}
        if (DEBUG) System.err.println("n_keys = " + ((NIOSelectSource)write_selsource).getSelector().keys().size());
        this.write_selsource = (NIOSelectSource)write_selsource;
	this.write_selsource.setName("WriteSelectSource");
        wselkey = (SelectionKey)write_selsource.register(
            nbsock.getChannel(), SelectionKey.OP_WRITE
        );
        if (wselkey == null) {
            System.err.println("SockState: register returned null");
            return false;
        }
        wselkey.attach(this);
        numActiveWriteSockets++;
        if (DEBUG) System.err.println("SockState: Registered with selsource");
    } else if (this.outstanding_writes == 0) {
        numEmptyWrites = 0;
        writeMaskEnable();
    }

    if ((writeClogThreshold != -1) &&
	(this.outstanding_writes > writeClogThreshold)) {
      if (DEBUG) System.err.println("SockState: warning: writeClogThreshold exceeded, dropping "+req);
      if (req instanceof ATcpWriteRequest) return false;
      if (req instanceof ATcpCloseRequest) {
	// Do immediate close: Assume socket is clogged
	ATcpCloseRequest creq = (ATcpCloseRequest)req;
	this.close(creq.compQ);
	return true;
      }
    }

    if (DEBUG) System.err.println("SockState: Adding writeReq to tail");
    writeReqList.add_to_tail(req);
    this.outstanding_writes++;
    if (DEBUG) System.err.println("SockState: " + this.outstanding_writes + " outstanding writes");
    return true;
  }

  protected void initWrite(ATcpWriteRequest req) {
    this.cur_write_req = req;
    this.writeBuf = req.buf.data;
    this.cur_offset = req.buf.offset;
    this.cur_length_target = req.buf.size + cur_offset;
    this.byte_buffer = ByteBuffer.wrap(writeBuf, cur_offset, req.buf.size);
  }

  protected boolean tryWrite() throws SinkClosedException {
    try {
      int tryLen;
      if (DEBUG) System.err.println("SockState: tryWrite()");
      if (MAX_WRITE_LEN == -1) {
	tryLen = cur_length_target - cur_offset;
      } else {
	tryLen = Math.min(cur_length_target - cur_offset, MAX_WRITE_LEN);
      }
      if (DEBUG) System.err.println("writing " + tryLen + " bytes");
      byte_buffer.limit(byte_buffer.position() + tryLen);
      cur_offset += nbsock.getChannel().write(byte_buffer);
      if (DEBUG) System.err.println("SockState: tryWrite() of "+tryLen+" bytes (len="+cur_length_target+", off="+cur_offset);

    } catch (IOException ioe) {
      // Assume this is because socket was already closed
      this.close(null);
      throw new SinkClosedException("tryWrite got exception doing write: "+ioe.getMessage());
    }
    if (cur_offset == cur_length_target) {
      if (DEBUG) System.err.println("SockState: tryWrite() completed write of "+cur_length_target+" bytes");
      return true;
    }
    else return false;
  }

  protected void writeMaskEnable() {
    numActiveWriteSockets++;
    wselkey.interestOps(wselkey.interestOps() | SelectionKey.OP_WRITE);
  }

  protected void writeMaskDisable() {
    numActiveWriteSockets--;
    wselkey.interestOps(wselkey.interestOps() & ~SelectionKey.OP_WRITE);
  }

  // This is synchronized to avoid close() interfering with
  // addWriteRequest
  protected synchronized void close(SinkIF closeEventQueue) {
    if (closed) return;

    closed = true;

    if (DEBUG) System.err.println("SockState.close(): Deregistering with selsources");
    if (read_selsource != null) read_selsource.deregister(rselkey);
    if (write_selsource != null) write_selsource.deregister(wselkey);
    if (DEBUG) System.err.println("SockState.close(): done deregistering with selsources");

    // Eliminate write queue
    writeReqList = null;

    try {
      if (DEBUG) System.err.println("SockState.close(): doing close ["+nbsock+"]");
      nbsock.close();
      if (DEBUG) System.err.println("SockState.close(): done with close ["+nbsock+"]");
    } catch (IOException e) {
      // Do nothing
    }

    if (closeEventQueue != null) {
      SinkClosedEvent sce = new SinkClosedEvent(conn);
      closeEventQueue.enqueue_lossy(sce);
    }
    if (DEBUG) System.err.println("SockState.close(): returning");
  }

}


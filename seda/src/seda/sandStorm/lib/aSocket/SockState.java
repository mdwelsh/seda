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

import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkClosedException;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.core.ssLinkedList;

import java.net.Socket;

/**
 * Internal class used to represent state of an active socket connection.
 */
public abstract class SockState implements aSocketConst {

  private static final boolean DEBUG = false;

  protected Socket nbsock;
  protected ATcpConnection conn;
  protected SinkIF readCompQ;
  protected QueueElementIF clogged_qel;
  protected int clogged_numtries;
  protected int readClogTries, writeClogThreshold;
  protected byte readBuf[];
  protected boolean closed = false;
  protected long seqNum = 1;

  protected int outstanding_writes, numEmptyWrites;
  protected ssLinkedList writeReqList;
  protected ATcpWriteRequest cur_write_req;
  protected int cur_offset, cur_length_target;
  protected byte writeBuf[];
  protected ATcpInPacket pkt;

  protected static int numActiveWriteSockets = 0;

  // This is synchronized with close() 
  protected abstract void readInit(SelectSourceIF read_selsource, SinkIF compQ, int readClogTries);
  protected abstract void doRead();

  // XXX This is synchronized with close() to avoid a race with close()
  // removing the writeReqList while this method is being called.
  // Probably a better way to do this...
  protected abstract boolean addWriteRequest(aSocketRequest req, SelectSourceIF write_selsource);

  protected abstract void initWrite(ATcpWriteRequest req);

  protected abstract boolean tryWrite() throws SinkClosedException;

  void writeReset() {
    this.cur_write_req = null;
    this.outstanding_writes--;
  }

  protected abstract void writeMaskEnable();

  protected abstract void writeMaskDisable();

  static int numActiveWriters() {
    return numActiveWriteSockets;
  }

  boolean isClosed() {
    return closed;
  }

  // XXX This is synchronized to avoid close() interfering with
  // addWriteRequest
  protected abstract void close(SinkIF closeEventQueue);

  public String toString() {
    return "SockState ["+nbsock+"]";
  }

}


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

import seda.sandStorm.api.SinkIF;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;

public class MessageReader implements SimpleP2PConst {
  private static final boolean DEBUG = false;

  int cur_offset;
  byte cur_data[];
  SinkIF compQ;
  ATcpConnection conn;

  MessageReader(SinkIF compQ) {
    this.cur_offset = 0;
    this.cur_data = new byte[MSG_SIZE];
    this.conn = null;
    this.compQ = compQ;
  }

  MessageReader(ATcpConnection conn, SinkIF compQ) {
    this.cur_offset = 0;
    this.cur_data = new byte[MSG_SIZE];
    this.conn = conn;
    this.compQ = compQ;
  }

  void parse(ATcpInPacket pkt) {

    byte recv_data[] = pkt.getBytes();
    int recv_offset = 0;

    if (DEBUG) System.err.println("MessageReader: Got "+recv_data.length+" bytes, need "+(MSG_SIZE-cur_offset)+", cur_offset "+cur_offset);

    while (recv_offset < recv_data.length) {
      int toread = Math.min(MSG_SIZE - cur_offset, recv_data.length - recv_offset);
      if (DEBUG) System.err.println("MessageReader: recv_data.length "+recv_data.length+", recv_offset "+recv_offset+", cur_data.length "+cur_data.length+" cur_offset "+cur_offset+", toread "+toread);
      System.arraycopy(recv_data, recv_offset, cur_data, cur_offset, toread);
      cur_offset += toread;
      recv_offset += toread;
      if (cur_offset == MSG_SIZE) { 
	push();
      }
    }
  }

  private void push() {
    Message msg = new Message(cur_data, conn);
    if (DEBUG) System.err.println("MessageReader: Pushing "+msg);
    compQ.enqueue_lossy(msg);
    cur_offset = 0;
    cur_data = new byte[MSG_SIZE];
  }
}


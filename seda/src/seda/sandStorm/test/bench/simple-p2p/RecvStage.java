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

import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.QueueElementIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.lib.aSocket.ATcpConnection;
import seda.sandStorm.lib.aSocket.ATcpInPacket;
import seda.sandStorm.lib.aSocket.ATcpServerSocket;

import java.util.Hashtable;

public class RecvStage implements EventHandlerIF, SimpleP2PConst {

  private static final boolean DEBUG = false;

  private SinkIF mysink, nextSink;
  private ATcpServerSocket servsock;
  private Hashtable connTbl;

  public RecvStage() {
  }

  public void init(ConfigDataIF config) throws Exception {
    System.err.println(config.getStage().getName()+": Started");
    connTbl = new Hashtable();

    mysink = config.getStage().getSink();

    String nextHandler = config.getString("next_handler");
    if (nextHandler == null) {
      System.err.println("Warning: RecvStage: Must specify next_handler");
    }
    nextSink = config.getManager().getStage(nextHandler).getSink();

    servsock = new ATcpServerSocket(PORT, mysink);
  }

  public void destroy() {
  }

  public void handleEvent(QueueElementIF item) {
    if (DEBUG) System.err.println("GOT QEL: "+item);

    if (item instanceof ATcpConnection) {
      System.err.println("Got connection: "+item);
      ATcpConnection conn = (ATcpConnection)item;
      conn.startReader(mysink);
      connTbl.put(conn, new MessageReader(conn, nextSink));

    } else if (item instanceof ATcpInPacket) {
      ATcpInPacket pkt = (ATcpInPacket)item;
      MessageReader rdr = (MessageReader)connTbl.get(pkt.getConnection());
      rdr.parse(pkt);

    } else {
      System.err.println("RecvStage got unknown event: "+item);
    }
  }

  public void handleEvents(QueueElementIF items[]) {
    for(int i=0; i<items.length; i++)
      handleEvent(items[i]);
  }

}


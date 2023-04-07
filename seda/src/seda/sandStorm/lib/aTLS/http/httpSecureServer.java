/*
 * Copyright (c) 2002 by Matt Welsh and The Regents of the University of 
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

package seda.sandStorm.lib.aTLS.http;

import seda.sandStorm.api.ConfigDataIF;
import seda.sandStorm.api.EventHandlerIF;
import seda.sandStorm.api.ManagerIF;
import seda.sandStorm.api.SinkIF;
import seda.sandStorm.lib.aTLS.aTLSServerSocket;
import seda.sandStorm.lib.http.httpConnection;
import seda.sandStorm.lib.http.httpConst;
import seda.sandStorm.lib.http.httpRequest;
import seda.sandStorm.lib.http.httpServer;

/**
 * An httpSecureServer is a SandStorm stage which accepts incoming HTTP 
 * connections from an aTLS socket. The server has a client sink 
 * associated with it, onto which httpConnection and httpRequest events 
 * are pushed. When a connection is closed, a SinkClosedEvent is pushed, 
 * with the sink pointer set to the httpConnection that closed. 
 *
 * @author Matt Welsh (mdw@cs.berkeley.edu)
 * @see httpServer
 * @see httpConnection
 * @see httpRequest
 */
public class httpSecureServer extends httpServer 
  implements EventHandlerIF, httpConst {

  private static final boolean DEBUG = false;
  private static final int DEFAULT_SECURE_HTTP_PORT = 443;

  /**
   * Create an HTTP server listening for incoming connections on 
   * the default port of 443.
   */
  public httpSecureServer(ManagerIF mgr, SinkIF clientSink) throws Exception {
    this(mgr, clientSink, DEFAULT_SECURE_HTTP_PORT);
  }

  /** 
   * Create an HTTP server listening for incoming connections on
   * the given listenPort. 
   */
  public httpSecureServer(ManagerIF mgr, SinkIF clientSink, int listenPort) throws Exception {
    super(mgr, clientSink, listenPort);
  }

  /** 
   * The Sandstorm stage initialization method.
   */
  public void init(ConfigDataIF config) throws Exception {
    mySink = config.getStage().getSink();
    servsock = new aTLSServerSocket(mgr, mySink, listenPort);
  }

}

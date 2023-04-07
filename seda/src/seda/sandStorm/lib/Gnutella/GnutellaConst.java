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


/**
 * This interface defines various constants used by the Gnutella
 * protocol. 
 */
public interface GnutellaConst {
  final static int DEFAULT_GNUTELLA_PORT = 6346;
  final static int DEFAULT_DOWNLOAD_PORT = 8081;
  final static int DEFAULT_PING_INTERVAL = 5000;  // msec
  final static int DEFAULT_TTL = 5;
  final static int DEFAULT_HOPS = 0;
  final static int DEFAULT_SPEED = 1000; // kbits/sec

  final static int PACKET_HEADER_SIZE = 23;
  // Set to -1 to accept all packets regardless of payload size
  final static int MAX_PAYLOAD_SIZE = 1000; 

  final static String GNUTELLA_CONNECT = "GNUTELLA CONNECT/0.4\n\n";
  final static String GNUTELLA_OK = "GNUTELLA OK\n\n";

  final static byte GNUTELLA_FN_PING = (byte)0x00;
  final static byte GNUTELLA_FN_PONG = (byte)0x01;
  final static byte GNUTELLA_FN_PUSH = (byte)0x40;
  final static byte GNUTELLA_FN_QUERY = (byte)0x80;
  final static byte GNUTELLA_FN_QUERYHITS = (byte)0x81;

  //final static int WRITE_CLOG_THRESHOLD = 500;
  final static int WRITE_CLOG_THRESHOLD = -1;

}

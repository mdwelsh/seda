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

/* This is a simple test program demonstrating the NBIO library.
 * It creates a single nonblocking server socket, issues a nonblocking
 * accept(), and then continuously issues nonblocking reads, using
 * SelectSet.select() to wait for them.
 */

import seda.nbio.*;

import java.io.IOException;
import java.io.InputStream;

public class NBIOSelectServer {

  public static final int SELECT_TIMEOUT = 10000;

  public static void main(String args[]) {

    try {
      System.err.println("NBIO server starting...");
      NonblockingServerSocket s = new NonblockingServerSocket(4046);
      NonblockingSocket clisock = null;

      SelectSet selset = new SelectSet();
      SelectItem selitem = new SelectItem(s, Selectable.ACCEPT_READY);
      selset.add(selitem);
      
      do {
        System.err.println("Waiting for connection...");
        selset.select(SELECT_TIMEOUT);
        if ((selitem.revents & Selectable.ACCEPT_READY) != 0) {
          clisock = s.nbAccept();
          if (clisock == null) throw new IOException("Got ACCEPT_READY but no connection?");
        }
      } while (clisock == null);

      selitem = new SelectItem(clisock, Selectable.READ_READY);
      selset.add(selitem);

      System.err.println("Got connection from "+clisock.getInetAddress().getHostName());
      InputStream is = clisock.getInputStream();
      byte barr[] = new byte[1024];

      while (true) {
        System.err.println("Calling select...");
 	int numevents = selset.select(SELECT_TIMEOUT);
	System.err.println("Got "+numevents+" events after select");
	System.err.println("revents is "+selitem.revents);
        int c = is.read(barr,0,1024);
        if (c == -1) {
          System.err.println("READ ERROR");
        } else if (c == 0) {
	  // This should not happen!
          System.err.println("READ NOTHING (should not happen)");
        } else {
          String str = new String(barr,0,c);
          System.err.println("READ: "+str);
        }
      }

    } catch (Exception e) {
      System.err.println("NBIOTest: Caught exception: "+e);
      e.printStackTrace();
      System.exit(-1);
    }

  }

}

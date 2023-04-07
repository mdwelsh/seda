//-*-java-*-
/**
 * McastReceiver.java
 *
 * Author: Rob von Behren <jrvb@cs.berkeley.edu>
 * Created: 10/14/01
 * 
 * This software is copyrighted by Rob von Behren and the Regents of
 * the University of California.  The following terms apply to all
 * files associated with the software unless explicitly disclaimed in
 * individual files.
 * 
 * The authors hereby grant permission to use this software without
 * fee or royalty for any non-commercial purpose.  The authors also
 * grant permission to redistribute this software, provided this
 * copyright and a copy of this license (for reference) are retained
 * in all distributed copies.
 *
 * For commercial use of this software, contact the authors.
 * 
 * IN NO EVENT SHALL THE AUTHORS OR DISTRIBUTORS BE LIABLE TO ANY
 * PARTY FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL
 * DAMAGES ARISING OUT OF THE USE OF THIS SOFTWARE, ITS DOCUMENTATION,
 * OR ANY DERIVATIVES THEREOF, EVEN IF THE AUTHORS HAVE BEEN ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE AUTHORS AND DISTRIBUTORS SPECIFICALLY DISCLAIM ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND
 * NON-INFRINGEMENT.  THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS,
 * AND THE AUTHORS AND DISTRIBUTORS HAVE NO OBLIGATION TO PROVIDE
 * MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 **/

import seda.nbio.NonblockingMulticastSocket;
import seda.nbio.SelectItem;
import seda.nbio.SelectSet;
import seda.nbio.Selectable;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Multicast listener - writes contents of received packets to the console
 *
 * @author  <A HREF="http://www.cs.berkeley.edu/~jrvb/">Rob von Behren</A> 
 *      &lt;<A HREF="mailto:jrvb@cs.berkeley.edu">jrvb@cs.berkeley.edu</A>&gt;
 **/


public class McastReceiver {

   public static void main(String argv[]) throws Exception {

      int count=0;
      
      // process command line
      if(argv.length != 2) {
         System.err.println("Format: java McastReceiver <multicast addr> <port>");
         System.exit(1);
      }
      InetAddress gaddr = InetAddress.getByName(argv[0]);
      int port = Integer.parseInt(argv[1]);
      

      // create the multicast socket
      NonblockingMulticastSocket sock = new NonblockingMulticastSocket(port);
      sock.joinGroup(gaddr);
      //sock.connect(gaddr,port); -- only for send-only sockets.
      // XXX MDW: Not sure what this is
      //sock.setLoopState(false);

      byte b1[] = ("message "+count).getBytes(); count++;
      DatagramPacket p = new DatagramPacket(b1, 0, b1.length, gaddr, port);
      sock.nbSend(p);


      // spin, receiving packets
      byte data[] = new byte[1000];
      DatagramPacket p3 = new DatagramPacket(new byte[1000], 1000);
      int len;
      int waitcount = 0;
      int msgcount = 0;
      System.out.print("waiting.");
      while(true) {
         // do a select, to wait for data
         SelectSet set = new SelectSet();
         SelectItem item = new SelectItem(sock,null,Selectable.READ_READY);
         set.add(item);
         int ret = set.select(10000);
         //System.out.println("got "+ret+" from select()");
         
         // get waiting data
         p3.setData(data,0,data.length);
         len = sock.nbReceive(p3);
         //sock.receive(p3); len=p3.getLength();

         if(len == 0) {
            // no data, so sleep
            if(waitcount % 5 == 0)  {
               System.out.print(".");
               //byte b[] = ("["+name+"] message "+msgcount).getBytes();
               //sock.nbSend(new DatagramPacket(b, 0, b.length,gaddr,port));
               msgcount++;
            }
            waitcount++;
            Thread.sleep(1000);
         } else {
            // print the data
            String msg = new String(p3.getData());
            System.out.println("got message ("+len+" bytes) --- "+msg);
            System.out.print("waiting.");

            byte b2[] = ("message "+count).getBytes(); count++;
            DatagramPacket p2 = new DatagramPacket(b2, 0, b2.length, gaddr, port);
            len = sock.nbSend(p2);
            
         } 
      }
   }
}

//////////////////////////////////////////////////
// Set the emacs indentation offset
// Local Variables: ***
// c-basic-offset:3 ***
// End: ***
//////////////////////////////////////////////////

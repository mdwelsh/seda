/*
 * Copyright (c) 2002 by The Regents of the University of California. 
 * All rights reserved.
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
 * Author: Dennis Chi <denchi@uclink4.berkeley.edu> 
 *
 */

package seda.sandStorm.lib.aTLS;

/** 
 * Internal constants used by the aTLS implementation.
 * Mostly taken from code from pureTLS.
 */

public interface aTLSConst {

    public static final int SSL_CLIENT = 1;
    public static final int SSL_SERVER = 2;

    // protocols
    public static final int SSL_V3_VERSION = 768;
    public static final int TLS_V1_VERSION = 769;

    // Handshake message types
    public static final int SSL_HT_HELLO_REQUEST = 0;
    public static final int SSL_HT_CLIENT_HELLO = 1;
    public static final int SSL_HT_SERVER_HELLO = 2;
    public static final int SSL_HT_CERTIFICATE = 11;
    public static final int SSL_HT_SERVER_KEY_EXCHANGE = 12;
    public static final int SSL_HT_CERTIFICATE_REQUEST = 13;
    public static final int SSL_HT_SERVER_HELLO_DONE = 14;
    public static final int SSL_HT_CERTIFICATE_VERIFY = 15;
    public static final int SSL_HT_CLIENT_KEY_EXCHANGE = 16;
    public static final int SSL_HT_FINISHED = 20;
    public static final int SSL_HT_V2_CLIENT_HELLO = 255;  

    // Client States (during handshake)
    public static final int SSL_HS_HANDSHAKE_START = 0;
    public static final int SSL_HS_SENT_CLIENT_HELLO = 1;
    public static final int SSL_HS_RECEIVED_SERVER_HELLO = 2;
    public static final int SSL_HS_RECEIVED_CERTIFICATE = 3;
    public static final int SSL_HS_RECEIVED_SERVER_KEY_EXCHANGE = 4;
    public static final int SSL_HS_RECEIVED_CERTIFICATE_REQUEST = 5;
    public static final int SSL_HS_RECEIVED_SERVER_HELLO_DONE = 6;
    
    // Server States (during handshake)
    public static final int SSL_HS_WAIT_FOR_CLIENT_HELLO =1;
    public static final int SSL_HS_WAIT_FOR_CERTIFICATE = 2;
    public static final int SSL_HS_WAIT_FOR_CLIENT_KEY_EXCHANGE = 3;
    public static final int SSL_HS_WAIT_FOR_CERTIFICATE_VERIFY= 4;
    public static final int SSL_HS_SEND_HELLO_REQUEST = 5;
    
    // States shared by both client and server
    public final int SSL_HS_WAIT_FOR_CHANGE_CIPHER_SPECS = 20;
    public final int SSL_HS_WAIT_FOR_FINISHED = 21;
    public static final int SSL_HANDSHAKE_FINISHED = 255;

    // record types
    public static final int SSL_CT_CHANGE_CIPHER_SPEC = 20;
    public static final int SSL_CT_ALERT = 21;
    public static final int SSL_CT_HANDSHAKE = 22;
    public static final int SSL_CT_APPLICATION_DATA = 23;
}

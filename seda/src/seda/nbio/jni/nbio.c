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

/*
 * This file implements the native method bindings for the nbio library.
 */

#ifdef SOLARIS
// Needed for errno etc. to work correctly
	#define _REENTRANT
#endif

#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef _WIN32
	#include <windows.h>

	#define IN_CLASSD(i)            (((long)(i) & 0xf0000000) == 0xe0000000)
	#define IN_CLASSD_NET           0xf0000000       /* These ones aren't really */
	#define IN_CLASSD_NSHIFT        28               /* net and host fields, but */
	#define IN_CLASSD_HOST          0x0fffffff       /* routing needn't know.    */
	#define IN_MULTICAST(i)         IN_CLASSD(i)
#else
	#include <errno.h>
	#include <string.h>
	#include <sys/types.h>
	#include <sys/socket.h>
	#include <sys/ioctl.h>
	#include <sys/poll.h>
	#include <netinet/tcp.h>
	#include <netinet/in.h>
	#include <arpa/inet.h>
	#include <unistd.h>
	#include <fcntl.h>
	#include <dlfcn.h>
	#include <sys/stat.h>
	#include "mdw-btree.h"
#endif

#ifdef SOLARIS
	#include <stropts.h>
	#include <sys/filio.h>
#endif

#ifdef HAS_DEVPOLL
	#include <sys/devpoll.h>
	#ifndef POLLREMOVE
		#define POLLREMOVE 0x1000
	#endif
#endif

#include "NonblockingSocketImpl.h"
#include "NonblockingSocketInputStream.h"
#include "NonblockingSocketOutputStream.h"
#include "SelectSetDevPollImpl.h"
#include "SelectSetPollImpl.h"
#include "mdw-exceptions.h"

#define DEBUG(_x) 


/* Constants *****************************************************************/

/* These need to stay consistent with the Java code - javah -jni does not
 * include constant definitions
 */
#define SELECTABLE_READ_READY 0x01
#define SELECTABLE_WRITE_READY 0x02
#define SELECTABLE_SELECT_ERROR 0x80

/* Java field/method IDs *****************************************************/

/* When we are first initialized we obtain all of the field/method IDs
 * that we need so that these don't have to be determined on the fly.
 */
static int _nbio_fids_init = 0;

static jfieldID FID_seda_nbio_NonblockingSocketInputStream_fd;
static jfieldID FID_seda_nbio_NonblockingSocketOutputStream_fd;
static jfieldID FID_seda_nbio_NonblockingSocketImpl_fd;
static jfieldID FID_seda_nbio_NonblockingSocketImpl_address;
static jfieldID FID_seda_nbio_NonblockingSocketImpl_port;
static jfieldID FID_seda_nbio_NonblockingSocketImpl_localport;
static jfieldID FID_seda_nbio_NBIOFileDescriptor_fd;
static jfieldID FID_java_net_InetAddress_address;
static jfieldID FID_java_net_InetAddress_family;
static jfieldID FID_java_net_DatagramPacket_buf;
static jfieldID FID_java_net_DatagramPacket_offset;
static jfieldID FID_java_net_DatagramPacket_length;
static jfieldID FID_java_net_DatagramPacket_address;
static jfieldID FID_java_net_DatagramPacket_port;
static jfieldID FID_seda_nbio_SelectItem_fd;
static jfieldID FID_seda_nbio_SelectItem_events;
static jfieldID FID_seda_nbio_SelectItem_revents;
static jfieldID FID_seda_nbio_SelectSetPollImpl_itemarr;
static jfieldID FID_seda_nbio_SelectSetDevPollImpl_itemarr;
static jfieldID FID_seda_nbio_SelectSetDevPollImpl_retevents;
static jfieldID FID_seda_nbio_SelectSetDevPollImpl_native_state;

static int nbio_init_fids(JNIEnv *env) {

	char _nbio_init_fids_err[512];
	jclass _nbio_init_fids_cls;

#ifdef _WIN32

	/*
	**	XXX TWL - Jan. 03, '02
	**	Initialize winsock. Is this complete?
	*/  
	WORD    wVersionRequested = MAKEWORD(MAJOR_WINSOCK_VERSION, MINOR_WINSOCK_VERSION);
	WSADATA wsaData;
	int     result;

	memset(&wsaData, 0, sizeof(wsaData));
	result = WSAStartup(wVersionRequested, &wsaData);

	if (IS_DEBUG) {
		char buffer[2048];
		int  pos = 0;

		memset(buffer, '\0', sizeof(buffer));

		pos += sprintf(&buffer[pos], "WSAStartup()                : %d\n", result);
		pos += sprintf(&buffer[pos], "WSAStartup() - version      : %d.%d\n", LOBYTE(wsaData.wVersion), HIBYTE(wsaData.wVersion));
		pos += sprintf(&buffer[pos], "WSAStartup() - high Version : %u\n", wsaData.wHighVersion);
		pos += sprintf(&buffer[pos], "WSAStartup() - description  : %s\n", wsaData.szDescription);
		pos += sprintf(&buffer[pos], "WSAStartup() - systemStat   : %s\n", wsaData.szSystemStatus);
		pos += sprintf(&buffer[pos], "WSAStartup() - maxSockets   : %u\n", wsaData.iMaxSockets);
		pos += sprintf(&buffer[pos], "WSAStartup() - maxUdpDg     : %u\n", wsaData.iMaxUdpDg);
		pos += sprintf(&buffer[pos], "WSAStartup() - vendorInfo   : %s", wsaData.lpVendorInfo);

		DEBUG(fprintf(stderr, "%s", buffer));
	}

	if (result != 0) {
		THROW_WIN_EXCEPTION(env, "java/lang/UnsatisfiedLinkError", WSAGetLastError());
		return -1;
	}

	if (LOBYTE(wsaData.wVersion) != MAJOR_WINSOCK_VERSION || 
		HIBYTE( wsaData.wVersion ) != MINOR_WINSOCK_VERSION ) {
		THROW_EXCEPTION(env, "java/lang/UnsatisfiedLinkError", "Unsupported winsock version requested");
		WSACleanup();
		return -1; 
	}

#endif

#define NBIO_GET_CLASS(__cname) { \
  _nbio_init_fids_cls = (*env)->FindClass(env, __cname); \
  if (_nbio_init_fids_cls == NULL) { \
    sprintf(_nbio_init_fids_err, "NBIO: Cannot resolve class %s in nbio_init_fids() -- this is a bug, please contact <mdw@cs.berkeley.edu", __cname); \
    THROW_EXCEPTION(env, "java/lang/UnsatisfiedLinkError", _nbio_init_fids_err); \
    return -1; \
  } \
  }

#define NBIO_GET_FIELD(__fname, __typename, __fid) \
  __fid = (*env)->GetFieldID(env, _nbio_init_fids_cls, __fname, __typename); \
  if (__fid == NULL) { \
    sprintf(_nbio_init_fids_err, "NBIO: Cannot resolve field %s (%s) in nbio_init_fids() -- this is a bug, please contact <mdw@cs.berkeley.edu", __fname, __typename); \
    THROW_EXCEPTION(env, "java/lang/UnsatisfiedLinkError", _nbio_init_fids_err); \
    return -1; \
  }

#define NBIO_GET_METHOD(__mname, __sig, __mid) \
  __mid = (*env)->GetMethodID(env, _nbio_init_fids_cls, __mname, __sig); \
  if (__mid == NULL) { \
    sprintf(_nbio_init_fids_err, "NBIO: Cannot resolve method %s (%s) in nbio_init_fids() -- this is a bug, please contact <mdw@cs.berkeley.edu", __mname, __sig); \
    THROW_EXCEPTION(env, "java/lang/UnsatisfiedLinkError", _nbio_init_fids_err); \
    return -1; \
  } 

	/* seda/nbio/NonblockingSocketInputStream */
	NBIO_GET_CLASS("seda/nbio/NonblockingSocketInputStream");
	NBIO_GET_FIELD("fd", "Lseda/nbio/NBIOFileDescriptor;", FID_seda_nbio_NonblockingSocketInputStream_fd);

	/* seda/nbio/NonblockingSocketOutputStream */
	NBIO_GET_CLASS("seda/nbio/NonblockingSocketOutputStream");
	NBIO_GET_FIELD("fd", "Lseda/nbio/NBIOFileDescriptor;", FID_seda_nbio_NonblockingSocketOutputStream_fd);

	/* seda/nbio/NonblockingSocketImpl */
	NBIO_GET_CLASS("seda/nbio/NonblockingSocketImpl");
	NBIO_GET_FIELD("fd", "Lseda/nbio/NBIOFileDescriptor;", FID_seda_nbio_NonblockingSocketImpl_fd);
	NBIO_GET_FIELD("address", "Ljava/net/InetAddress;", FID_seda_nbio_NonblockingSocketImpl_address);
	NBIO_GET_FIELD("port", "I", FID_seda_nbio_NonblockingSocketImpl_port);
	NBIO_GET_FIELD("localport", "I", FID_seda_nbio_NonblockingSocketImpl_localport);

	/* seda/nbio/NBIOFileDescriptor */
	NBIO_GET_CLASS("seda/nbio/NBIOFileDescriptor");
	NBIO_GET_FIELD("fd", "I", FID_seda_nbio_NBIOFileDescriptor_fd);

	/* java/net/InetAddress */
	NBIO_GET_CLASS("java/net/InetAddress");
	NBIO_GET_FIELD("address", "I", FID_java_net_InetAddress_address);
	NBIO_GET_FIELD("family", "I", FID_java_net_InetAddress_family);

	/* java/net/DatagramPacket */
	NBIO_GET_CLASS("java/net/DatagramPacket");
	NBIO_GET_FIELD("buf", "[B", FID_java_net_DatagramPacket_buf);
	NBIO_GET_FIELD("offset", "I", FID_java_net_DatagramPacket_offset);
	NBIO_GET_FIELD("length", "I", FID_java_net_DatagramPacket_length);
	NBIO_GET_FIELD("address", "Ljava/net/InetAddress;", FID_java_net_DatagramPacket_address);
	NBIO_GET_FIELD("port", "I", FID_java_net_DatagramPacket_port);

	/* seda/nbio/SelectItem */
	NBIO_GET_CLASS("seda/nbio/SelectItem");
	NBIO_GET_FIELD("fd", "Lseda/nbio/NBIOFileDescriptor;", FID_seda_nbio_SelectItem_fd);
	NBIO_GET_FIELD("events", "S", FID_seda_nbio_SelectItem_events);
	NBIO_GET_FIELD("revents", "S", FID_seda_nbio_SelectItem_revents);

	/* seda/nbio/SelectSetPollImpl */
	NBIO_GET_CLASS("seda/nbio/SelectSetPollImpl");
	NBIO_GET_FIELD("itemarr", "[Lseda/nbio/SelectItem;", FID_seda_nbio_SelectSetPollImpl_itemarr);

	/* seda/nbio/SelectSetDevPollImpl */
	NBIO_GET_CLASS("seda/nbio/SelectSetDevPollImpl");
	NBIO_GET_FIELD("itemarr", "[Lseda/nbio/SelectItem;", FID_seda_nbio_SelectSetDevPollImpl_itemarr);
	NBIO_GET_FIELD("retevents", "[Lseda/nbio/SelectItem;", FID_seda_nbio_SelectSetDevPollImpl_retevents);
	NBIO_GET_FIELD("native_state", "J", FID_seda_nbio_SelectSetDevPollImpl_native_state);

#undef NBIO_GET_CLASS
#undef NBIO_GET_FIELD

	_nbio_fids_init = 1;

	return 0;

}

static void nbio_make_nonblocking(JNIEnv *env, int fd) {
	/* Set fd to nonblocking mode */
#ifdef _WIN32
	u_long arg = 1;
	if (ioctlsocket(fd, FIONBIO, &arg) < 0) {
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
		return;
	}
#else
	if (fcntl(fd, F_SETFL, O_NONBLOCK) < 0) {
		THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
		return;
	}
#endif
	DEBUG(fprintf(stderr,"Set fd=%d to nonblocking mode\n", fd));
}

static void nbio_make_blocking(JNIEnv *env, int fd) {
	/* Set fd to blocking mode */
#ifdef _WIN32
	u_long arg = 0;
	if (ioctlsocket(fd, FIONBIO, &arg) < 0) {
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
		return;
	}
#else
	if (fcntl(fd, F_SETFL, 0) < 0) {
		THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
		return;
	}
#endif
	DEBUG(fprintf(stderr,"Set fd=%d to blocking mode\n", fd));
}

static void nbio_disable_nagle(JNIEnv *env, int fd) {
	int enable = 1;
	if (setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, (char *)&enable, sizeof(int)) < 0) {
#ifdef _WIN32
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
#else
		THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
#endif
		return;
	}
}

#ifdef _WIN32

/* Poll masks */
	#ifndef POLLIN
		#define POLLIN      0x0001    /* There is data to read */
	#endif
	#ifndef POLLPRI
		#define POLLPRI     0x0002    /* There is urgent data to read */
	#endif
	#ifndef POLLOUT
		#define POLLOUT     0x0004    /* Writing now will not block */
	#endif
	#ifndef POLLERR
		#define POLLERR     0x0008    /* Error condition */
	#endif
	#ifndef POLLHUP
		#define POLLHUP     0x0010    /* Hung up */
	#endif
	#ifndef POLLNVAL
		#define POLLNVAL    0x0020    /* Invalid request: fd not open */
	#endif

/* array of streams to poll */
struct pollfd {
	int fd;
	short   events;
	short   revents;
	int _ifd;		/* Internal "fd" for the benefit of the kernel */
};

static int poll(struct pollfd *ufds, unsigned int nfds, int timeout) {
	/*
	**	XXX - TWL Jan. 03, '02
	**	Attempt to mimic poll using select. No attempt to be complete has been made
	*/
	int i, result;
	fd_set readfds;
	fd_set writefds;
	fd_set exceptfds;
	struct timeval tout;

	FD_ZERO(&readfds);
	FD_ZERO(&writefds);
	FD_ZERO(&exceptfds);

	/* 
	 * timeout is given in milliseconds or 1/1000 of a second
	 * timeval uses seconds and microseconds or 1/1000000 of a second
	 */
	tout.tv_sec  = timeout / 1000;			// seconds
	tout.tv_usec = (timeout % 1000) * 1000;	// microseconds


	for (i = 0; i < nfds; i++) {
		if (ufds[i].events & POLLIN)  FD_SET(ufds[i].fd, &readfds);
		if (ufds[i].events & POLLOUT) FD_SET(ufds[i].fd, &writefds);
		ufds[i].revents = 0;
	}

	result = select(nfds, &readfds, &writefds, &exceptfds, &tout);

	if (result > 0) {
		for (i = 0; i < nfds; i++) {
			if (FD_ISSET(ufds[i].fd, &readfds))	 ufds[i].revents |= POLLIN;
			if (FD_ISSET(ufds[i].fd, &writefds)) ufds[i].revents |= POLLOUT;
		}
	}

	return result;
}
#endif

void nbio_interrupt_select(JNIEnv *env, int fd) {
  DEBUG(fprintf(stderr, "NBIO: nbio_interrupt_select called.\n"));
  if (fd > 0) {
    char buf = 0;
    int rc;
    rc = write(fd, &buf, 1);
    if (rc < 0) {
      THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
      return;
    }
    DEBUG(fprintf(stderr, "NBIO: nbio_interrupt_select: write to wakeup socket %d returned %d\n", fd, rc));
  }
  else
    DEBUG(fprintf(stderr, "NBIO: nbio_interrupt_select: wakeup socket fd "
		  "was %d\n", fd ));
}

void nbio_read_wakeup_socket(int fd) {
  // A Poll operation got interrupted.  Read the wakeup socket dry.
  char crud[80];
  int bytes;
  while ((bytes = read(fd, crud, sizeof(crud)))
	 > 0) {
    DEBUG(fprintf(stderr,"NBIO nbio_read_wakeup_socket: read %d byte(s) from wakeup socket %d.\n", bytes, fd));
  }
  DEBUG(fprintf(stderr,"NBIO nbio_read_wakeup_socket: read from wakeup socket %d gave return %d, errno %d.\n", fd, bytes, errno));
}

#define MAX_POLLOBJS 100
static struct {
    jint pollobj;
    int wakeup_sockets[2];
 } pollobjs[MAX_POLLOBJS] = { { -1, {-1, -1} } };

 static int bum_wakeup_sockets[2] = {-1}; 

static int n_pollobjs = 0;


/* NonblockingSocketImpl *****************************************************/

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketCreate
 * Signature: (Z)V
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketCreate(JNIEnv *env, jobject this, jboolean stream) {
	int fd;
	jobject fdobj;
	long enable = 1;

	if (!_nbio_fids_init) {
		if (nbio_init_fids(env) < 0) {
			return;
		}
	}

	fd = socket(AF_INET, (stream ? SOCK_STREAM: SOCK_DGRAM), 0);
#ifdef _WIN32
	if (fd == INVALID_SOCKET ) {
		THROW_WIN_EXCEPTION(env, "java/io/IOException", WSAGetLastError());
		return;
	}
#else
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/io/IOException", strerror(errno));
		return;
	}
#endif 

	DEBUG(fprintf(stderr,"NBIO: Created socket, fd=%d\n", fd));

	// XXX MDW: Turn these on for all sockets
	// XXX MDW: (These are probably best to turn on only for servers)
	//
	// XXX JRVB: SO_REUSEADDR is also necessary for Multicast sockets to
	// XXX JRVB: work as expected, so if this is removed from here, it
	// XXX JRVB: needs to be specifically enabled for multicast sockets
	// XXX JRVB: elsewhere.
	setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char *)&enable, sizeof(int));
	setsockopt(fd, SOL_SOCKET, SO_KEEPALIVE, (char *)&enable, sizeof(int));

	// Want this for all TCP sockets
	if (stream)	nbio_disable_nagle(env, fd);

	// XXX Should also turn on SO_LINGER? Apache does for server sockets..
	// XXX Should also set SO_SNDBUF to increase send buffer size?

	nbio_make_nonblocking(env, fd);

	fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}
	(*env)->SetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd, fd);
	DEBUG(fprintf(stderr,"NBIO: Returning from nbSocketCreate with fd=%d\n", fd));
	return;
}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketConnect
 * Signature: (Ljava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketConnect (JNIEnv *env, jobject this, jobject address, jint port) {
	int fd, inet_address, inet_family, localport;
	struct sockaddr_in him;
	int ret;
	int myerrno;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}

	if (address == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "address is NULL");
		return;
	}
	inet_address = (*env)->GetIntField(env, address, FID_java_net_InetAddress_address);

	// In JDK 1.4 the meaning of the 'family' field changed in InetAddress,
	// so hardwire this to AF_INET (all we care about anyway).
	//inet_family = (*env)->GetIntField(env, address, FID_java_net_InetAddress_family);
	inet_family = AF_INET;

	memset((char *)&him,  0, sizeof(him));
	him.sin_port = htons((short)port);
	him.sin_addr.s_addr = (unsigned long)htonl(inet_address);
	him.sin_family = inet_family;

	again:
#ifdef _WIN32
	if ((ret = connect(fd, (struct sockaddr *)&him, sizeof(him))) != 0) {
		myerrno = WSAGetLastError();
		DEBUG(fprintf(stderr,"NBIO: connect returned %d, errno %d\n", ret, myerrno));
		if ((myerrno == WSAEINPROGRESS) || (myerrno == WSAEWOULDBLOCK)) {
			/* This is ok - connection not yet done */
			goto connect_ok;
		} else if (myerrno == WSAECONNREFUSED) {
			THROW_WIN_EXCEPTION(env, "java/net/ConnectException", myerrno);
		} else if (myerrno == WSAETIMEDOUT || myerrno == WSAENETUNREACH) {
			THROW_WIN_EXCEPTION(env, "java/net/NoRouteToHostException", myerrno);
		} else if (myerrno == WSAEINTR) {
			DEBUG(fprintf(stderr, "***** NBIO: connect: Interrupted, trying again"));
			goto again;
		} else {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", myerrno);
		}
		return;
	}
#else
	if ((ret = connect(fd, (struct sockaddr *)&him, sizeof(him))) < 0) {
		myerrno = errno;
		DEBUG(fprintf(stderr,"NBIO: connect returned %d, errno %d\n", ret, myerrno));
		if (myerrno == EINPROGRESS) {
			/* This is ok - connection not yet done */
			goto connect_ok;
		} else if (myerrno == ECONNREFUSED) {
			THROW_EXCEPTION(env, "java/net/ConnectException", strerror(myerrno));
		} else if (myerrno == ETIMEDOUT || myerrno == EHOSTUNREACH) {
			THROW_EXCEPTION(env, "java/net/NoRouteToHostException", strerror(myerrno));
		} else if (myerrno == EINTR) {
			DEBUG(fprintf(stderr,"***** NBIO: connect: Interrupted, trying again\n"));
			goto again;
		} else {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(myerrno));
		}
		return;
	}
#endif
	connect_ok:

	(*env)->SetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_address, address);
	(*env)->SetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_port, port);
	localport = (*env)->GetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_localport);
	if (localport == 0) {
		/* Set localport value -- may have been previously set by bind operation */
		int len = sizeof(him);
#ifdef _WIN32
		if (getsockname(fd,(struct sockaddr *)&him, &len) == SOCKET_ERROR) {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
			return;
		}
#else
		if (getsockname(fd,(struct sockaddr *)&him, &len) == -1) {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
			return;
		}
#endif
		(*env)->SetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_localport, ntohs(him.sin_port));
	}

	return;
}


JNIEXPORT jboolean JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketConnectDone (JNIEnv *env, jobject this) {

	/* This is a bit strange. Although the man pages say you use
	 * select() followed by getsockopt() to find out if the connection was
	 * established, looks like you do select() and call connect() again...
	 */

	int fd, inet_address, inet_family;
	jobject address; 
	jint port;
	struct sockaddr_in him;
	int ret;
	int myerrno;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return JNI_FALSE;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return JNI_FALSE;
	}

	address = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_address);
	port = (*env)->GetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_port);

	if (address == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "address is NULL");
		return JNI_FALSE;
	}

	inet_address = (*env)->GetIntField(env, address, FID_java_net_InetAddress_address);

	// In JDK 1.4 the meaning of the 'family' field changed in InetAddress,
	// so hardwire this to AF_INET (all we care about anyway).
	//inet_family = (*env)->GetIntField(env, address, FID_java_net_InetAddress_family);
	inet_family = AF_INET;

	memset((char *)&him,  0, sizeof(him));
	him.sin_port = htons((short)port);
	him.sin_addr.s_addr = (unsigned long)htonl(inet_address);
	him.sin_family = inet_family;

	DEBUG(fprintf(stderr,"NBIO: connectDone: recalling connect on fd %d\n", fd));

	again:
#ifdef _WIN32
	if ((ret = connect(fd, (struct sockaddr *)&him, sizeof(him))) == SOCKET_ERROR) {
		myerrno = WSAGetLastError();
		DEBUG(fprintf(stderr,"NBIO: connectDone: connect got errorno %d\n", myerrno));
		if (myerrno == WSAEINPROGRESS) return JNI_FALSE;
		else if ((myerrno == WSAEALREADY) || (myerrno == WSAEINVAL) || (myerrno == WSAEWOULDBLOCK))	return JNI_FALSE;
		else if (myerrno == WSAEISCONN)	return JNI_TRUE;
		else if (myerrno == WSAEINTR) {
			//orange: this may be incorrect; can you only be interrupted by calling WSACancelBlockingCall()?
			// in which case we should just stop.... but shouldn't matter-- only affects blocking calls?
			DEBUG(fprintf(stderr,"NBIO: connectDone: connect returned EINTR, trying again"));
			goto again;
		} else {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", myerrno);
			return JNI_FALSE;
		}
	}
#else
	if ((ret = connect(fd, (struct sockaddr *)&him, sizeof(him))) < 0) {
		myerrno = errno;
		DEBUG(fprintf(stderr,"NBIO: connectDone: connect got errorno %d\n", myerrno));
		if (myerrno == EINPROGRESS)	return JNI_FALSE;
		else if (myerrno == EALREADY) return JNI_FALSE;
		else if (myerrno == EISCONN) return JNI_TRUE;
		else if (myerrno == EINTR) {
			DEBUG(fprintf(stderr,"NBIO: connectDone: connect returned EINTR, trying again"));
			goto again;
		} else {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(myerrno));
			return JNI_FALSE;
		}
	}
#endif
	return JNI_TRUE;
}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketBind
 * Signature: (Ljava/net/InetAddress;I)V
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketBind (JNIEnv *env, jobject this, jobject address, jint port) {
	int fd, inet_address, inet_family;
	struct sockaddr_in him;
	int ret;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}

	/* A bind address of NULL represents AF_INET/INADDR_ANY */
	if (address == NULL) {
		inet_address = INADDR_ANY;
		inet_family = AF_INET;
	} else {
		inet_address = (*env)->GetIntField(env, address, FID_java_net_InetAddress_address);
		inet_family = AF_INET;
	}

	memset((char *)&him,  0, sizeof(him));
	him.sin_port = htons((short)port);
	him.sin_addr.s_addr = (unsigned long)htonl(inet_address);
	him.sin_family = inet_family;

#ifdef _WIN32
	if ((ret = bind(fd, (struct sockaddr *)&him, sizeof(him))) == SOCKET_ERROR) {
		int myerrno = WSAGetLastError();
		if ((myerrno == WSAEINVAL) || (myerrno == WSAEADDRINUSE) ||(myerrno == WSAEADDRNOTAVAIL)) {
			THROW_WIN_EXCEPTION(env, "java/net/BindException", myerrno);
		} else {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", myerrno);
		}
		return;
	}
#else
	if ((ret = bind(fd, (struct sockaddr *)&him, sizeof(him))) < 0) {
		// JRVB: some occurrences of errno - should have been myerrno
		int myerrno = errno;
		fprintf(stderr,"bind: returned %d, errno %d (%s)\n", ret, myerrno, strerror(myerrno));
		if (myerrno == EACCES) {
			THROW_EXCEPTION(env, "java/net/BindException", strerror(myerrno));
		} else {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(myerrno));
		}
		return;
	}
#endif

	(*env)->SetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_address, address);
	(*env)->SetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_port, port);

	/* Set local port value */

	if (port == 0) {
		/* Set localport value -- may have been previously set by bind operation */
		int len = sizeof(him);
#ifdef _WIN32
		if (getsockname(fd,(struct sockaddr *)&him, &len) == SOCKET_ERROR ) {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
			return;
		}
#else
		if (getsockname(fd,(struct sockaddr *)&him, &len) == -1) {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
			return;
		}
#endif
		(*env)->SetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_localport, ntohs(him.sin_port));
	} else {
		(*env)->SetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_localport, port);
	}

	return;
}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketListen
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketListen (JNIEnv *env, jobject this, jint count) {
	int fd;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}

#ifdef _WIN32
	if (listen(fd, count) == SOCKET_ERROR) {
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
		return;
	}
#else
	if (listen(fd, count) < 0) {
		THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
		return;
	}
#endif
}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketAccept
 * Signature: (Lseda/nbio/NonblockingSocketImpl;)V
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketAccept (JNIEnv *env, jobject this, jobject newsocket, jboolean block) {
	int fd, newfd;
	jclass cls;
	struct sockaddr_in him;
	int len, localport;
	jobject sptr_inetaddr, sptr_fdobj;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

	if (newsocket == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "newsocket is NULL");
		return -1;
	}

	/* We expect that the 'fd' field of the newsocket has
	 * been created outside of this method (but not initialized).
	 */
	sptr_fdobj = (*env)->GetObjectField(env, newsocket, FID_seda_nbio_NonblockingSocketImpl_fd);

	if (sptr_fdobj == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "newsocket uninitialized");
		return -1;
	}

	/* XXX MDW This is not threadsafe */
	if (!block) {
		nbio_make_nonblocking(env, fd);
	} else {
		nbio_make_blocking(env, fd);
	}

	DEBUG(fprintf(stderr,"NBIO: Doing accept() on fd=%d\n", fd));

#if defined(linux) || defined(__FreeBSD__)
	len = sizeof(him);
#endif 
#ifdef SOLARIS
	len = sizeof(struct sockaddr); 
#endif
#ifdef _WIN32
	len = sizeof(struct sockaddr);
#endif

	newfd = accept(fd, (struct sockaddr *)&him, &len);
#ifdef _WIN32
	if (newfd == INVALID_SOCKET) {
		if (!block && (WSAGetLastError() == WSAEWOULDBLOCK)) {
			// turn off the accept revents flag (aka, the readable flag)
			DEBUG(fprintf(stderr,"Turning off reading on socked: %d", fd));
			return -1;
		} else {
			DEBUG(fprintf(stderr,"NBACCEPT: returning after failed call to accept: %d", WSAGetLastError()));
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
			return -1;
		}
	}
#else
	if (newfd < 0) {
		if (!block && errno == EWOULDBLOCK)	return -1;
		else {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
			return -1;
		}
	}
#endif

	DEBUG(fprintf(stderr,"NBIO: accept() on fd=%d returned %d\n", fd, newfd));

	nbio_make_nonblocking(env, newfd);
	nbio_disable_nagle(env, newfd);

	(*env)->SetIntField(env, sptr_fdobj, FID_seda_nbio_NBIOFileDescriptor_fd, newfd);

	localport = (*env)->GetIntField(env, this, FID_seda_nbio_NonblockingSocketImpl_localport);

	/* Create empty InetAddress and initialize it */
	DEBUG(fprintf(stderr,"NBIO: accept() creating new InetAddress\n"));
	cls = (*env)->FindClass(env, "java/net/InetAddress");
	if (cls == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "Cannot find java.net.InetAddress class");
		return -1;
	}
	sptr_inetaddr = (*env)->AllocObject(env, cls);

	DEBUG(fprintf(stderr,"NBIO: accept() initializing new InetAddress\n"));

	(*env)->SetIntField(env, sptr_inetaddr, FID_java_net_InetAddress_address, ntohl(him.sin_addr.s_addr));

	// In JDK 1.4 the meaning of 'family' changed in InetAddress, so don't
	// set it here.
	//(*env)->SetIntField(env, sptr_inetaddr, FID_java_net_InetAddress_family, him.sin_family);

	(*env)->SetIntField(env, newsocket, FID_seda_nbio_NonblockingSocketImpl_port, ntohs(him.sin_port));
	(*env)->SetIntField(env, newsocket, FID_seda_nbio_NonblockingSocketImpl_localport, localport);
	(*env)->SetObjectField(env, newsocket, FID_seda_nbio_NonblockingSocketImpl_address, sptr_inetaddr);

	return 0;

}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketAvailable
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketAvailable (JNIEnv *env, jobject this) {
	int fd;
	int bytes;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

#ifdef _WIN32
	if (ioctlsocket(fd, FIONREAD, (u_long*) &bytes) == SOCKET_ERROR) {
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
		return -1;
	}
#else
#if defined(linux) || defined(__FreeBSD__)
	if (ioctl(fd, FIONREAD, &bytes) < 0)
#endif /* linux or __FreeBSD__ */
#ifdef SOLARIS
		if (ioctl(fd, I_NREAD, &bytes) < 0)
#endif /* SOLARIS */
		{
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
			return -1;
		}
#endif
	DEBUG(fprintf(stderr,"NBIO: nbSocketAvailable called, %d bytes available\n", bytes));

	return bytes;

}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSocketClose
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSocketClose (JNIEnv * env, jobject this) {
	int fd; 

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		return;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		return;
	}

#ifdef _WIN32
	if (closesocket(fd) != 0) {
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
		return;
	}
#else
	close(fd);
#endif
	(*env)->SetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd, -1);
}

/* NonblockingSocketInputStream *******************************************/

/*
 * Class:     seda_nbio_NonblockingSocketInputStream
 * Method:    nbSocketRead
 * Signature: ([BII)I
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketInputStream_nbSocketRead (JNIEnv *env, jobject this, jbyteArray b, jint off, jint len) {
	int fd;
	jobject fdobj;
	int datalen;
	jbyte *data;
	int n;

	DEBUG(fprintf(stderr,"NBIO: nbSocketRead called, off=%d len=%d\n", off, len));

	fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketInputStream_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

	if (b == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "null byte array passed to nbSocketRead");
		return -1;
	}
	datalen = (*env)->GetArrayLength(env, b);

	if (len < 0 || len + off > datalen) {
		THROW_EXCEPTION(env, "java/lang/ArrayIndexOutOfBoundsException", "len must be >= 0 and len + off <= array length");
	}

	data = (*env)->GetByteArrayElements(env, b, NULL);

#ifdef _WIN32
	n = recv(fd, (void*) &data[off], len, 0);

	DEBUG(fprintf(stderr, "NBIO: nbSocketRead: off is %d, len is %d, got %d, lasterror is %d", off, len, n, WSAGetLastError()));

	if (n == 0) {
		// Socket was closed
		(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
		return -1; // EOF
	} else if (n == SOCKET_ERROR) {
		int myerrno = WSAGetLastError();

		if ( (myerrno  == WSAEWOULDBLOCK) || (myerrno == WSAEINTR) || (myerrno == WSAEINPROGRESS)) {
			//this turns off the readable revent flag for this selectitem
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return 0;
		} else {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", myerrno);
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return -1; // error
		}
	}
#else
	n = read(fd, data+off, len);

	DEBUG(fprintf(stderr,"NBIO: nbSocketRead: off is %d, len is %d, got %d, errno is %d\n", off, len, n, errno));

	if (n == 0) {
		// Socket was closed 
		(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
		return -1; // EOF
	} else if (n < 0) {
		if (errno == EAGAIN) {
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return 0; // No data returned
		} else {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return -1; // error
		}
	}
#endif

	(*env)->ReleaseByteArrayElements(env, b, data, 0);
	return n;
}

/*
 * Class:     seda_nbio_NonblockingSocketOutputStream
 * Method:    nbSocketWrite
 * Signature: ([BII)I
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketOutputStream_nbSocketWrite (JNIEnv *env, jobject this, jbyteArray b, jint off, jint len) {
	int fd;
	jobject fdobj;
	int datalen;
	jbyte *data;
	int n;

	DEBUG(fprintf(stderr,"NBIO: nbSocketWrite called, off=%d len=%d\n", off, len));

	fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketOutputStream_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

	if (b == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "null byte array passed to nbSocketWrite");
		return -1;
	}
	datalen = (*env)->GetArrayLength(env, b);

	if (len < 0 || len + off > datalen) {
		THROW_EXCEPTION(env, "java/lang/ArrayIndexOutOfBoundsException", "len must be >= 0 and len + off <= array length");
	}

	data = (*env)->GetByteArrayElements(env, b, NULL);

#ifdef _WIN32
	n = send(fd, (void*) &data[off], len, 0);

	if (n == SOCKET_ERROR) {
		int myerrno = WSAGetLastError();
		if ((myerrno == WSAEWOULDBLOCK) || (myerrno == WSAEINTR) || (myerrno == WSAEINPROGRESS)) {
			// turns off the writable revent flag for this selectitem
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return 0; // No data returned
		} else {
			THROW_WIN_EXCEPTION(env, "java/net/SocketException", myerrno);
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return -1; // error      
		}
	}
#else
	n = write(fd, data+off, len);

	DEBUG(fprintf(stderr,"NBIO: nbSocketWrite: off is %d, len is %d, got %d, errno is %d\n", off, len, n, errno));

	if (n < 0) {
		if ((errno == EAGAIN) || (errno == EINTR)) {
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return 0; // No data returned
		} else {
			THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
			(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
			return -1; // error
		}
	}
#endif

	(*env)->ReleaseByteArrayElements(env, b, data, JNI_ABORT);
	return n;
}

/* UDP support code below */

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbReceive
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketImpl_nbReceive(JNIEnv *env, jobject this, jobject packet) {
	jbyteArray data;
	jbyte* recvdata;
	jint length, offset;
	struct sockaddr_in from;
	int fromlength;
	int fd, ret, sz;
	jclass cls; 
	jobject addrobj;
	jobject fdobj;

	DEBUG(fprintf(stderr,"NBIO: nbReceive called\n"));

	fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);

	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	if (packet == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "packet is null in nbReceive");
		return -1;
	}

	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

	data = (*env)->GetObjectField(env, packet, FID_java_net_DatagramPacket_buf);
	if (data == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "data buffer in nbReceive is null");
		return -1;
	}

	offset = (*env)->GetIntField(env, packet, FID_java_net_DatagramPacket_offset);
	length = (*env)->GetIntField(env, packet, FID_java_net_DatagramPacket_length);
	sz = (*env)->GetArrayLength(env, data);   

	DEBUG(fprintf(stderr, "NBIO: nbReceive: offset %d, len %d, sz %d\n", offset, length, sz));

	if ( (length < 0) || (length > sz) ) {
		THROW_EXCEPTION(env, "java/lang/IllegalArgumentException", "length must be >= 0 and length <= array length");
		return -1;
	}
	if ( (offset < 0) || (offset > length) ) {
		THROW_EXCEPTION(env, "java/lang/IllegalArgumentException", "offset must be >=0 and offset <= length");
	}

	fromlength = sizeof(struct sockaddr_in);

	recvdata = (*env)->GetByteArrayElements(env, data, NULL);
	if (recvdata == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "can't access primitive array");
		return -1;
	}
	memset(recvdata, 0, length);

#ifdef _WIN32
	ret = recvfrom(fd, (void*) &recvdata[offset], length, 0, (struct sockaddr*)&from, &fromlength);
	if ( (ret == SOCKET_ERROR) || (ret == 0)) {
		int myerrno = WSAGetLastError();
		DEBUG(fprintf(stderr,"NBIO: recvfrom returned %d\n", ret));
		// Set length to 0
		(*env)->SetIntField(env, packet, FID_java_net_DatagramPacket_length, 0);
		(*env)->ReleaseByteArrayElements(env, data, recvdata, JNI_ABORT);

		// These 2 cases indicate no data ready to be read
		if ((ret == 0) || (myerrno == WSAEWOULDBLOCK)) {
			// turns off the readable revent flag for this selectitem
			return 0; 
		}

		THROW_WIN_EXCEPTION(env, "java/net/SocketException", myerrno);
		return -1;
	}
#else
	if ( (ret = recvfrom(fd, recvdata+offset, length, 0, (struct sockaddr*)&from, &fromlength)) <= 0) {
		// JRVB: added myerrno, to get correct reporting when DEBUG is on
		int myerrno = errno;
		DEBUG(fprintf(stderr,"NBIO: recvfrom returned %d\n", ret));
		// Set length to 0
		(*env)->SetIntField(env, packet, FID_java_net_DatagramPacket_length, 0);
		(*env)->ReleaseByteArrayElements(env, data, recvdata, JNI_ABORT);

		// These 2 cases indicate no data ready to be read
		if (ret == 0) return 0;
		if (myerrno == EAGAIN) return 0;

		THROW_EXCEPTION(env, "java/net/SocketException", strerror(myerrno));
		return -1;
	}
#endif

	DEBUG(fprintf(stderr,"NBIO: recvfrom returned normally %d\n", ret));

	// Set the actual length
	(*env)->SetIntField(env, packet, FID_java_net_DatagramPacket_length, ret);

	// Release the copy of the primitive array
	(*env)->ReleaseByteArrayElements(env, data, recvdata, 0);

	// Set port in packet
	(*env)->SetIntField(env, packet, FID_java_net_DatagramPacket_port, ntohs(from.sin_port));

	// Create empty InetAddress and initialize it 
	DEBUG(fprintf(stderr,"NBIO: nbReceive() creating new InetAddress\n"));
	cls = (*env)->FindClass(env, "java/net/InetAddress");
	if (cls == NULL) {
		THROW_EXCEPTION(env, "java/lang/UnsatisfiedLinkError", "Cannot find java.net.InetAddress class");
		return -1;
	}
	addrobj = (*env)->AllocObject(env, cls);
	if (addrobj == NULL) {
		THROW_EXCEPTION(env, "java/lang/OutOfMemoryError", "Unable to allocate new InetAddress in nbReceive()");
		return -1;
	}

	// Set IP address in addrobj
	(*env)->SetIntField(env, addrobj, FID_java_net_InetAddress_address, ntohl(from.sin_addr.s_addr));
	// Set address in packet
	(*env)->SetObjectField(env, packet, FID_java_net_DatagramPacket_address, addrobj);

	return ret;
}

/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbSendTo
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSendTo(JNIEnv *env, jobject this, jobject packet) {

	jbyte* senddata;
	struct sockaddr_in from;
	int fd, ret, sz;
	jbyteArray data;
	jint offset, length, port;
	jobject addr;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

	data = (*env)->GetObjectField(env, packet, FID_java_net_DatagramPacket_buf);
	if (data == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "data buffer is null in nbSendTo");
		return -1;
	}

	offset = (*env)->GetIntField(env, packet, FID_java_net_DatagramPacket_offset);
	length = (*env)->GetIntField(env, packet, FID_java_net_DatagramPacket_length);
	sz = (*env)->GetArrayLength(env, data);

	if ( (length < 0) || (length > sz) ) {
		THROW_EXCEPTION(env, "java/lang/IllegalArgumentException", "length must be >= 0 and length <= array length");
		return -1;
	}

	senddata = (*env)->GetByteArrayElements(env, data, NULL);
	if (senddata == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "senddata in nbSendTo is null");
		return -1;
	}

	addr = (*env)->GetObjectField(env, packet, FID_java_net_DatagramPacket_address);
	// If address is null then we are connected and are calling send();
	// otherwise we need to call sendto().


	if (addr == NULL) {
		DEBUG(fprintf(stderr,"NBIO: send() called, size %d\n", length));
#ifdef _WIN32

		if ( (ret = send(fd, (void*) &senddata[offset], length, 0)) == SOCKET_ERROR ) {
			(*env)->ReleaseByteArrayElements(env, data, senddata, JNI_ABORT);
			if (WSAGetLastError() == WSAEWOULDBLOCK) {
				//return 0 indicates unable to send b/c it would block	
				// this turns off the writeable flag for this selectitem
				return 0;
			} else {
				THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
				return -1;
			}
		}
#else
		if ( (ret = send(fd, senddata+offset, length, 0)) < 0) {
			(*env)->ReleaseByteArrayElements(env, data, senddata, JNI_ABORT);
			if (errno == EAGAIN) {
				//return 0 indicates unable to send b/c it would block
				return 0;
			} else {
				THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
				return -1;
			}
		}
#endif
	} else {
		// addr is not null
		memset(&from, 0, sizeof(struct sockaddr_in));
		from.sin_family = AF_INET;
		from.sin_addr.s_addr = htonl( (unsigned long)( (*env)->GetIntField(env, addr, FID_java_net_InetAddress_address) ) );

		port = (*env)->GetIntField(env, packet, FID_java_net_DatagramPacket_port);
		if ( (port < 0) || (port > 0xffff) ) {
			THROW_EXCEPTION(env, "java/lang/InvalidArgumentException", "bad port in nbSendTo");
			(*env)->ReleaseByteArrayElements(env, data, senddata, JNI_ABORT);
			return -1;
		}
		from.sin_port = htons((unsigned short)port);

		DEBUG(fprintf(stderr,"NBIO: sendto() called, size %d\n", length));
#ifdef _WIN32
		if ( (ret = sendto(fd, (void*) &senddata[offset], length, 0, (struct sockaddr*)&from, sizeof(struct sockaddr_in))) == SOCKET_ERROR ) {
			(*env)->ReleaseByteArrayElements(env, data, senddata, JNI_ABORT);
			if (WSAGetLastError() == WSAEWOULDBLOCK) {
				//this would block, return 0
				// this turns off teh writeable revent flag for this selectitem
				return 0;
			} else {
				THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
				return -1;
			}
		}
#else
		if ( (ret = sendto(fd, senddata+offset, length, 0, (struct sockaddr*)&from, sizeof(struct sockaddr_in))) <0) {
			(*env)->ReleaseByteArrayElements(env, data, senddata, JNI_ABORT);
			if (errno == EAGAIN) {
				//this would block, return 0
				return 0;
			} else {
				THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
				return -1;
			}
		}
#endif
	}

	(*env)->ReleaseByteArrayElements(env, data, senddata, JNI_ABORT);
	return ret;
}

/*************************************************************
 * Multicast support code below 
 *************************************************************/

/* helper function */
int mcast_get_fd(JNIEnv *env, jobject this) {
	int fd;
	jobject fdobj;

	fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return -1;
	}

	return fd;
}


/* helper function - do a setsockopt for a multicast option */
static void mcast_set_opt(JNIEnv *env, jobject this, jobject address, int opt, char *errmsg) {

	int fd = mcast_get_fd(env,this);
	int addr;
	struct ip_mreq mreq;

	// get the multicast address
	if (address == NULL) {
		THROW_EXCEPTION(env, "java/lang/NullPointerException", "group address is NULL");
		return;
	}
	addr = (*env)->GetIntField(env, address, FID_java_net_InetAddress_address);
	if (opt!=IP_MULTICAST_IF  &&  !IN_MULTICAST(addr)) {
		THROW_EXCEPTION(env, "java/lang/SocketException", "address is not a multicast address");
		return;
	}

	// set the socket option
	mreq.imr_multiaddr.s_addr=htonl(addr);
	mreq.imr_interface.s_addr=htonl(INADDR_ANY); // the value of this field seems to have no effect
	if (setsockopt(fd, IPPROTO_IP, opt, (char *) &mreq, sizeof(mreq)) != 0) {
		THROW_EXCEPTION(env, "java/net/SocketException", errmsg);
		return;
	}
}


/*
 * Join multicast group
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbJoinGroup
(JNIEnv *env, jobject this, jobject address) {
	mcast_set_opt(env, this, address, IP_ADD_MEMBERSHIP, "failed to join multicast group");
}

/*
 * Leave a multicast group
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbLeaveGroup
(JNIEnv *env, jobject this, jobject address) {
	mcast_set_opt(env, this, address, IP_DROP_MEMBERSHIP, "failed to leave multicast group");
}

/*
 * Set the interface used for this multicast socket
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSetInterface
(JNIEnv *env, jobject this, jobject address) {
	mcast_set_opt(env, this, address, IP_MULTICAST_IF, "failed to set multicast interface");
}

/*
 * Get the TTL on multicast packets
 */
JNIEXPORT jint JNICALL Java_seda_nbio_NonblockingSocketImpl_nbGetTimeToLive
(JNIEnv *env, jobject this) {
	int ttl;
	int len;
	int fd = mcast_get_fd(env,this);

	getsockopt(fd,IPPROTO_IP,IP_MULTICAST_TTL, (char *) &ttl, &len);
	return ttl;
}

/*
 * Set the TTL for multicast packets
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSetTimeToLive
(JNIEnv *env, jobject this, jint ttl) {
	int fd = mcast_get_fd(env,this);

	setsockopt(fd,IPPROTO_IP,IP_MULTICAST_TTL, (char *) &ttl, sizeof(ttl));
}


/*
 * turn receiving your own packets on or off
 */
JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbSeeLocalMessages
(JNIEnv *env, jobject this, jboolean loop_state) {
	int fd = mcast_get_fd(env,this);
	int val = (loop_state ? 1 : 0);

	setsockopt(fd,IPPROTO_IP,IP_MULTICAST_LOOP, (char *) &val, sizeof(val));
}



/*
 * Class:     seda_nbio_NonblockingSocketImpl
 * Method:    nbDisconnect
 * Signature: ()V
 */

JNIEXPORT void JNICALL Java_seda_nbio_NonblockingSocketImpl_nbDisconnect
(JNIEnv *env, jobject this) {
	int fd;
	struct sockaddr_in him;

	jobject fdobj = (*env)->GetObjectField(env, this, FID_seda_nbio_NonblockingSocketImpl_fd);
	if (fdobj == NULL) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}
	fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	if (fd == -1) {
		THROW_EXCEPTION(env, "java/net/SocketException", "socket closed");
		return;
	}

#ifdef SOLARIS
	/* to disconnect on Solaris, connect on null address */
	if ( connect(fd, (struct sockaddr*)NULL, 0) < 0) {
		THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
	}
#endif // ifdef SOLARIS

#if defined(linux) || defined(__FreeBSD__)
	/* to disconnect on Linux or FreeBSD, connect to an addr with
	 * family == AF_UNSPEC */
	memset((char *)&him,  0, sizeof(him));
	him.sin_family = AF_UNSPEC;
	if ( connect(fd, (struct sockaddr *)&him, sizeof(struct sockaddr_in)) < 0) {
		THROW_EXCEPTION(env, "java/net/SocketException", strerror(errno));
	}
#endif

#ifdef _WIN32
	/* to disconnect on win32, connect to addr w/ all 0's */
	memset(&him,  0, sizeof(him));
	him.sin_family = AF_INET;
	if ( connect(fd, (struct sockaddr *)&him, sizeof(struct sockaddr_in)) == SOCKET_ERROR) {
		THROW_WIN_EXCEPTION(env, "java/net/SocketException", WSAGetLastError());
	}
#endif
}

/* SelectSetDevPollImpl ******************************************************/

#ifndef HAS_DEVPOLL

JNIEXPORT jboolean JNICALL Java_seda_nbio_SelectSetDevPollImpl_supported(JNIEnv *env, jclass cls) {

	/* Safe setting when compiling on systems where /dev/poll not available */
	return JNI_FALSE;
}

#else  /* HAS_DEVPOLL */

/* Define to use B-Tree to map fd to selitemobj */
	#undef USE_BTREE    
/* Max number of file descriptors if flat table used */
	#define MAX_FDS 32768

typedef struct devpoll_impl_state {
	int devpoll_fd;
	int max_retevents;
	struct pollfd *retevents;
#ifdef USE_BTREE
	btree_node *tree;
#else
	jobject selitems[MAX_FDS];
#endif
  int wakeup_sockets[2];
} devpoll_impl_state;

JNIEXPORT jboolean JNICALL Java_seda_nbio_SelectSetDevPollImpl_supported(JNIEnv *env, jclass cls) {
	struct stat statbuf;

	/* If we were compiled on a system with /dev/poll, but running where
	 * it's not available, return false
	 */
	if (stat("/dev/poll", &statbuf) == 0) {
		return JNI_TRUE;
	} else {
		return JNI_FALSE;
	}
}

JNIEXPORT void JNICALL Java_seda_nbio_SelectSetDevPollImpl_init (JNIEnv * env, jobject this, jint max_retevents) {
	devpoll_impl_state *state;
	int i;

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.init(%d) called\n", max_retevents));

	if (!_nbio_fids_init) {
		if (nbio_init_fids(env) < 0) {
			return;
		}
	}

	// Allocate state
	state = (devpoll_impl_state *)malloc(sizeof(devpoll_impl_state));
	if (state == NULL) {
		THROW_EXCEPTION(env, "java/lang/OutOfMemoryError", "Cannot allocate devpoll_impl_state");
		return;
	}

	// Set state field
	(*env)->SetLongField(env, this, FID_seda_nbio_SelectSetDevPollImpl_native_state, (jlong)state);

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.init set state field\n"));

	state->max_retevents = max_retevents;
	state->devpoll_fd = open("/dev/poll", O_RDWR);
	if (state->devpoll_fd < 0) {
		THROW_EXCEPTION(env, "java/io/IOException", strerror(errno));
		free(state);
		return;
	}

#ifdef USE_BTREE
	state->tree = btree_newnode();
#else
	for (i = 0; i < MAX_FDS; i++) {
		state->selitems[i] = (jobject)NULL;
	}
#endif

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.init opened /dev/poll, fd %d\n", state->devpoll_fd));

	state->retevents = (struct pollfd *)malloc(max_retevents * sizeof(struct pollfd));
	if (state->retevents == NULL) {
		THROW_EXCEPTION(env, "java/lang/OutOfMemoryError", "Cannot allocate state->retevents");
		free(state);
		return;
	}

  // Open and Register wakeup socket for this SelectSet.
  {
      struct pollfd pfd;
      if (socketpair(PF_UNIX, SOCK_STREAM, 0, state->wakeup_sockets) != 0) {
	  char errmsg[160];
	  snprintf(errmsg, sizeof(errmsg), "opening socketpair for "
		   "wakeup: %s", strerror(errno));
	  THROW_EXCEPTION(env, "java/net/SocketException", errmsg);
      }
      if (fcntl(state->wakeup_sockets[0], F_SETFL, O_NONBLOCK) < 0    ||
	  fcntl(state->wakeup_sockets[1], F_SETFL, O_NONBLOCK) < 0) {
	  char errmsg[160];
	  snprintf(errmsg, sizeof(errmsg), "nbio_fids_init: setting wakeup sockets "
		   "nonblocking: %s", strerror(errno));
	  THROW_EXCEPTION(env, "java/net/SocketException", errmsg);
      }
      DEBUG(fprintf(stderr, "NBIO: Wakeup sockets %d and %d opened.\n",
		    state->wakeup_sockets[0], state->wakeup_sockets[1]));
     if (n_pollobjs >= MAX_POLLOBJS) {
	 THROW_EXCEPTION(env, "java/lang/OutOfMemoryError",
			 "No more room for another SelectSet in NBIO pollobjs.");
	 return;
     }
      pollobjs[n_pollobjs].pollobj = this;
      pollobjs[n_pollobjs].wakeup_sockets[0] = state->wakeup_sockets[0];
      pollobjs[n_pollobjs++].wakeup_sockets[1] = state->wakeup_sockets[1];

      pfd.fd = state->wakeup_sockets[1];
      pfd.events = POLLIN;
      if (write(state->devpoll_fd, &pfd, sizeof(struct pollfd))
	  != sizeof (struct pollfd)) {
	  THROW_EXCEPTION(env, "java/io/IOException", strerror(errno));
	  return;
      }
      DEBUG(fprintf(stderr, "NBIO DevPollImpl: Registered wakeup socket %d "
		    "(devpollfd %d)\n",
		    state->wakeup_sockets[1], state->devpoll_fd));
  }
  return;
}

JNIEXPORT void JNICALL Java_seda_nbio_SelectSetDevPollImpl_register (JNIEnv * env, jobject this, jobject selitemobj) {
  devpoll_impl_state *state;
  jobject fdobj;
  short events, realevents;
  struct pollfd pfd;
  jobject selitem;

  DEBUG(fprintf(stderr,"SelectSetDevPollImpl.register called\n"));

  // Get state
  state = (devpoll_impl_state *)(((*env)->GetLongField(env, this, FID_seda_nbio_SelectSetDevPollImpl_native_state)) & 0xffffffff);

  DEBUG(fprintf(stderr,"SelectSetDevPollImpl.register got state, devpoll_fd %d\n", state->devpoll_fd));

  // Get fd
  fdobj = (*env)->GetObjectField(env, selitemobj, FID_seda_nbio_SelectItem_fd);
  pfd.fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
  // Get events
  events = (*env)->GetShortField(env, selitemobj, FID_seda_nbio_SelectItem_events);
  realevents = 0;
  if (events != 0) {
    if (events & SELECTABLE_READ_READY) {
      realevents |= (POLLIN | POLLPRI);
    }
    if (events & SELECTABLE_WRITE_READY) {
      realevents |= POLLOUT;
    }
  }
  pfd.events = realevents;
  DEBUG(fprintf(stderr,"nbio: events was 0x%lx, pfd.events now 0x%lx\n", events, realevents));

  DEBUG(fprintf(stderr,"SelectSetDevPollImpl.register adding (fd=%d,events=0x%x)\n", pfd.fd, pfd.events));

  // Register
  if (write(state->devpoll_fd, &pfd, sizeof(struct pollfd)) != sizeof(struct pollfd)) {
    THROW_EXCEPTION(env, "java/io/IOException", strerror(errno));
    return;
  }

#ifdef USE_BTREE
	data = btree_search(state->tree, pfd.fd);
	if (data == NULL) {
		// Insert into B-tree
		state->tree = btree_insert(state->tree, pfd.fd, (*env)->NewGlobalRef(env, selitemobj));
	}
#else
	if ((pfd.fd < 0) || (pfd.fd >= MAX_FDS)) {
		fprintf(stderr,"Warning: pfd.fd is %d, out of range 0...%d.\n",pfd.fd,MAX_FDS);
		fprintf(stderr,"  You need to recompile nbio.c with a larger MAX_FDS value.\n");
		fprintf(stderr,"  This is part of the NBIO package.\n");
		return;
	}
	selitem = state->selitems[pfd.fd];
	if (selitem == (jobject)NULL) {
		state->selitems[pfd.fd] = (*env)->NewGlobalRef(env, selitemobj);
	}
#endif

	return;
}

JNIEXPORT void JNICALL Java_seda_nbio_SelectSetDevPollImpl_deregister (JNIEnv * env, jobject this, jobject selitemobj) {
	devpoll_impl_state *state;
	jobject fdobj, selitem;
	struct pollfd pfd;

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.deregister called\n"));

	// Get state
	state = (devpoll_impl_state *)(((*env)->GetLongField(env, this, FID_seda_nbio_SelectSetDevPollImpl_native_state)) & 0xffffffff);

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.deregister got state, devpoll_fd %d\n", state->devpoll_fd));

	// Get fd
	fdobj = (*env)->GetObjectField(env, selitemobj, FID_seda_nbio_SelectItem_fd);
	pfd.fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
	pfd.events = POLLREMOVE;

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.deregister removing (fd=%d,events=0x%x)\n", pfd.fd, pfd.events));

	// Deeegister
	if (write(state->devpoll_fd, &pfd, sizeof(struct pollfd)) != sizeof(struct pollfd)) {
		THROW_EXCEPTION(env, "java/io/IOException", strerror(errno));
		return;
	}

#ifdef USE_BTREE
	// XXX NEED TO DELETE NODE FROM BTREE
#else
	if ((pfd.fd < 0) || (pfd.fd >= MAX_FDS)) {
		fprintf(stderr,"Warning: pfd.fd is %d, out of range 0...%d.\n",pfd.fd,MAX_FDS);
		fprintf(stderr,"  You need to recompile nbio.c with a larger MAX_FDS value.\n");
		return;
	}
	selitem = state->selitems[pfd.fd];
	if (selitem != (jobject)NULL) {
		// selitem can be NULL if deregister is not synchronized 
		// (although it should be)
		(*env)->DeleteGlobalRef(env, selitem);
		state->selitems[pfd.fd] = (jobject)NULL;
	}
#endif

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.deregister done\n"));
	return;
}

JNIEXPORT jint JNICALL Java_seda_nbio_SelectSetDevPollImpl_doSelect (JNIEnv * env, jobject this, jint timeout, jint num_fds) {
	devpoll_impl_state *state;
	jobject selitemobj;
	jobjectArray itemarr, retitemarr;
	struct dvpoll dopoll;
	int itemarrlen, retitemarrlen, ret, i, retfd, count;
	struct pollfd *pfd;
	short realevents;
	int wfd = -1;		/* wakeup (interrupt) file descriptor */


	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect called\n"));

	// Get state
	state = (devpoll_impl_state *)(((*env)->GetLongField(env, this, FID_seda_nbio_SelectSetDevPollImpl_native_state)) & 0xffffffff);

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect got state, devpoll_fd=%d\n",state->devpoll_fd));

	// Get itemarray
	itemarr = (jobjectArray)((*env)->GetObjectField(env, this, FID_seda_nbio_SelectSetDevPollImpl_itemarr));
	if (itemarr == NULL) {
		// This can happen if we have an empty SelectSet 
		return 0;
	}
	itemarrlen = (*env)->GetArrayLength(env, itemarr);
	if (itemarrlen <= 0) {
		THROW_EXCEPTION(env, "java/lang/ArrayIndexOutOfBoundsException", "SelectItem[] array has size <= 0");
		return 0;
	}

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect got itemarr, len %d\n",itemarrlen));

	// Get retitemarr
	retitemarr = (jobjectArray)((*env)->GetObjectField(env, this, FID_seda_nbio_SelectSetDevPollImpl_retevents));
	retitemarrlen = (*env)->GetArrayLength(env, retitemarr);
	if (retitemarrlen <= 0) {
		THROW_EXCEPTION(env, "java/lang/ArrayIndexOutOfBoundsException", "SelectItem[] ret array has size <= 0");
		return 0;
	}

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect got retitemarr, length %d\n", retitemarrlen));

	// Fill in dopoll
	dopoll.dp_timeout = timeout;
	dopoll.dp_nfds = (num_fds > state->max_retevents) ? (state->max_retevents) : (num_fds);
	dopoll.dp_fds = state->retevents;

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect (devpollfd %d) doing DP_POLL\n", state->devpoll_fd));

	ret = ioctl(state->devpoll_fd, DP_POLL, &dopoll);

	DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect (devpollfd %d) DP_POLL returned %d\n", state->devpoll_fd, ret));

	if (ret == 0) {
		return 0;
	}
	if (ret < 0) {
		int myerrno = errno;
		// Don't throw an exception if we were interrupted
		if (myerrno != EINTR) {
			THROW_EXCEPTION(env, "java/io/IOException", strerror(myerrno));
		}
		return 0;
	}

	// Need to synchronize in case register/deregister called while
	// we assign SelectItems to retitemarr
	(*env)->MonitorEnter(env, this); 

	wfd = state->wakeup_sockets[1]; // wakeup socket fd.
	count = 0;
	for (i = 0; i < ret; i++) {
		pfd = &(state->retevents[i]);
		DEBUG(fprintf(stderr,"SelectSetDevPollImpl.doSelect ret[%d] fd %d revents 0x%x\n", i, pfd->fd, pfd->revents));
		retfd = pfd->fd;
	
		if (retfd == wfd) {
		  DEBUG(fprintf(stderr,"NBIO DevPollImpl.doSelect: POLLIN on wakeup socket %d "
			    "(devpollfd %d)\n",
			    wfd, state->devpoll_fd));
		  nbio_read_wakeup_socket(wfd);
		  continue;		/* skip rest of this pass. */
		}

#ifdef USE_BTREE
		data = btree_search(state->tree, retfd);
		if (data == NULL) {
			// This can be caused by a socket closing (and being reregistered
			// from the SelectSet) asynchronously with respect to a call to
			// doSelect(). In this case just skip over it
			continue;
		}
		selitemobj = (jobject)data;
#else 
		if ((retfd < 0) || (retfd >= MAX_FDS)) {
			fprintf(stderr,"Warning: retfd is %d, out of range 0...%d.\n",retfd,MAX_FDS);
			fprintf(stderr,"  You need to recompile nbio.c with a larger MAX_FDS value.\n");
			THROW_EXCEPTION(env, "java/lang/ArrayIndexOutOfBoundsException", "retfd out of range");
			(*env)->MonitorExit(env, this); 
			return 0;
		}
		selitemobj = state->selitems[retfd];
		if (selitemobj == (jobject)NULL) {
			// This can be caused by a socket closing (and being reregistered
			// from the SelectSet) asynchronously with respect to a call to
			// doSelect(). In this case just skip over it
			continue;
		}
#endif
		DEBUG(fprintf(stderr,"Got selitemobj 0x%lx\n", (unsigned long)selitemobj));

		realevents = 0;
		if (pfd->revents & (POLLIN | POLLPRI)) {
			realevents |= SELECTABLE_READ_READY;
		}
		if (pfd->revents & POLLOUT) {
			realevents |= SELECTABLE_WRITE_READY;
		}
		if (pfd->revents & (POLLERR | POLLHUP | POLLNVAL)) {
			realevents |= SELECTABLE_SELECT_ERROR;
		}

		(*env)->SetShortField(env, selitemobj, FID_seda_nbio_SelectItem_revents, realevents);
		DEBUG(fprintf(stderr,"Set revents\n"));

		if (count >= retitemarrlen) {
			fprintf(stderr,"WARNING: *** NBIO devPollImpl.doSelect(): count %d max_ret %d -- this is a bug, please contact mdw@cs.berkeley.edu\n", count, retitemarrlen);
		}
		(*env)->SetObjectArrayElement(env, retitemarr, count, selitemobj);

		if ((*env)->ExceptionOccurred(env)) {
			// Possible that the SelectItem was freed before we managed to do
			// the SetShortField/SetObjectArrayElement? Should not happen
			// if we are synchronized with deregister.
			fprintf(stderr,"WARNING: *** NBIO devPollImpl.doSelect() got exception: this is a bug - please contact mdw@cs.berkeley.edu\n");
			(*env)->ExceptionDescribe(env);
			// Clear it and skip this item
			(*env)->ExceptionClear(env);
			continue;
		}

		DEBUG(fprintf(stderr,"Set retitemarr[%d]\n", count));
		count++;
	}

	(*env)->MonitorExit(env, this); 
	return count;
}


JNIEXPORT void JNICALL Java_seda_nbio_SelectSetDevPollImpl_interruptSelect(JNIEnv *env, jobject this) {
	devpoll_impl_state *state;
	// Get state
	state = (devpoll_impl_state *)(((*env)->GetLongField(env, this, FID_seda_nbio_SelectSetDevPollImpl_native_state)) & 0xffffffff);
	nbio_interrupt_select(env, state->wakeup_sockets[0]);
}

#endif /* HAS_DEVPOLL */

/* SelectSetPollImpl *******************************************************/


int* nbio_pollImpl_init_wakeup(JNIEnv *env, jint my_id) {
     int i;
     for (i = 0; i < n_pollobjs; i++) {	/* presuming linear search not awful */
	 if (pollobjs[i].pollobj == my_id)
	     return pollobjs[i].wakeup_sockets;
     }

     if (n_pollobjs >= MAX_POLLOBJS) {
	 THROW_EXCEPTION(env, "java/lang/OutOfMemoryError",
			 "No more room for another SelectSet in nbio pollobjs.");
	 return bum_wakeup_sockets;
     }
     //  This section should be synchronized / mutex'd...
     pollobjs[n_pollobjs].pollobj = my_id;
     if (socketpair(AF_UNIX, SOCK_STREAM, 0,
		    pollobjs[n_pollobjs].wakeup_sockets) != 0) {
	 char errmsg[160];
	 snprintf(errmsg, sizeof(errmsg), "opening socketpair for "
		  "wakeup: %s", strerror(errno));
	 THROW_EXCEPTION(env, "java/net/SocketException", errmsg);
	 return bum_wakeup_sockets;
     }
     return pollobjs[n_pollobjs++].wakeup_sockets;
}

JNIEXPORT jint JNICALL Java_seda_nbio_SelectSetPollImpl_doSelect(JNIEnv *env, jobject this, jint timeout, jint my_id) {
	jobjectArray itemarr;
	jobject selitemobj, fdobj;
	struct pollfd *ufds;
	int *ufds_map;
	int itemarrlen;
	int i, n;
	int ret;
	int num_ufds = 0;
	short events, realevents;
	int readevents = 0;
	int *wfd = NULL;			/* int[2] wakeup socket fd's */

	DEBUG(fprintf(stderr,"NBIO: doSelect called\n"));

	if (!_nbio_fids_init) {
		if (nbio_init_fids(env) < 0) {
			return -1;
		}
	}
	wfd = nbio_pollImpl_init_wakeup(env, my_id);

	itemarr = (jobjectArray)(*env)->GetObjectField(env, this, FID_seda_nbio_SelectSetPollImpl_itemarr);
	if (itemarr == NULL) {
		// This can happen if we have an empty SelectSet 
		return 0;
	}

	DEBUG(fprintf(stderr,"NBIO: doSelect: got itemarr\n"));

	itemarrlen = (*env)->GetArrayLength(env, itemarr);
	if (itemarrlen <= 0) {
		THROW_EXCEPTION(env, "java/lang/ArrayIndexOutOfBoundsException", "SelectItem[] array has size <= 0");
		return 0;
	}

	DEBUG(fprintf(stderr,"NBIO: doSelect: itemarrlen is %d\n", itemarrlen));

	DEBUG(fprintf(stderr,"NBIO: doSelect: got SelectItem class\n"));

	// Only allocate a ufd if events != 0
	for (i = 0; i < itemarrlen; i++) {
		selitemobj = (*env)->GetObjectArrayElement(env, itemarr, i);
		if (selitemobj == NULL) {
			fprintf(stderr,"NBIO: WARNING: itemarr[%d] is NULL! (itemarrlen=%d)\n", i, itemarrlen);
			THROW_EXCEPTION(env, "java/lang/NullPointerException", "SelectItem element is null");
			return 0;
		}
		events = (*env)->GetShortField(env, selitemobj, FID_seda_nbio_SelectItem_events);
		if (events != 0) num_ufds++;
	}

	if (num_ufds == 0) return 0;

	ufds = (struct pollfd *)malloc(sizeof(struct pollfd) * num_ufds);
	if (ufds == NULL) {
		THROW_EXCEPTION(env, "java/lang/OutOfMemoryError", "cannot allocate pollfd array");
		return 0;
	}
	DEBUG(fprintf(stderr,"NBIO: doSelect: allocated %d ufds\n", num_ufds));
	ufds_map = (int *)malloc(sizeof(int) * num_ufds);
	if (ufds_map == NULL) {
		THROW_EXCEPTION(env, "java/lang/OutOfMemoryError", "cannot allocate ufds_map");
		free(ufds);
		return 0;
	}

	n = 0;
	for (i = 0; i < itemarrlen; i++) {
		selitemobj = (*env)->GetObjectArrayElement(env, itemarr, i);
		if (selitemobj == NULL) {
			THROW_EXCEPTION(env, "java/lang/NullPointerException", "SelectItem element is null");
			free(ufds);
			free(ufds_map);
			return 0;
		}

		realevents = 0;
		events = (*env)->GetShortField(env, selitemobj, FID_seda_nbio_SelectItem_events);
		if (events != 0) {
			if (events & SELECTABLE_READ_READY) {
				realevents |= (POLLIN | POLLPRI);
			}
			if (events & SELECTABLE_WRITE_READY) {
				realevents |= POLLOUT;
			}
			ufds[n].events = realevents;
			DEBUG(fprintf(stderr,"NBIO: doSelect: ufds[%d].events is 0x%x\n", n, ufds[n].events));
			ufds[n].revents = 0;
			fdobj = (*env)->GetObjectField(env, selitemobj, FID_seda_nbio_SelectItem_fd);
			ufds[n].fd = (*env)->GetIntField(env, fdobj, FID_seda_nbio_NBIOFileDescriptor_fd);
			DEBUG(fprintf(stderr,"NBIO: doSelect: ufds[%d].fd is %d\n", n, ufds[n].fd));
			ufds_map[n] = i;
			n++;
		}
	}

	 // If there are reads going on, include the wakeup socket.
	if (readevents != 0  &&  wfd[1] > 0) {
		DEBUG(fprintf(stderr,"NBIO: doSelectt: ufds[%d].fd will be wakeup socket %d.\n",
			      n, wfd[1] ));
		ufds[n].events = POLLIN;	/* notice if wakeup socket has s'g to rd. */
		ufds[n].revents = 0;
		ufds[n].fd = wfd[1];
		ufds_map[n] = i;  // danger, this i == itemarrlen.
		n++;
		num_ufds++;
	}

	/* XXX MDW: poll() is interruptible. Under Linux, a signal (say, from 
	 * the GC) might interrupt poll. For now I don't deal with this
	 * properly - I just go ahead and return early from doSelect() if the 
	 * call was interrupted.
	 */
	DEBUG(fprintf(stderr,"NBIO: Doing poll, %d fds, timeout %d\n", n, timeout));

	ret = poll(ufds, num_ufds, timeout);

	DEBUG(fprintf(stderr,"NBIO: doSelect: did poll, timeout %d, ret is %d, errno is %d\n", timeout, ret, errno));

	if (ret == 0) {
		free(ufds);
		free(ufds_map);
		return 0;
	}
	if (ret < 0) {
		// Don't throw an exception if we were interrupted
#if _WIN32
		int myerrno = WSAGetLastError();
		if (myerrno != WSAEINTR) {
			THROW_WIN_EXCEPTION(env, "java/io/IOException", myerrno);
		}
#else
		int myerrno = errno;
		if (myerrno != EINTR) {
			THROW_EXCEPTION(env, "java/io/IOException", strerror(myerrno));
		}
#endif
		free(ufds);
		free(ufds_map);
		return 0;
	}

	for (n = 0; n < num_ufds; n++) {
		DEBUG(fprintf(stderr,"NBIO: doSelect: ufds[%d].revents is 0x%x\n", n, ufds[n].revents));

		if (ufds[n].revents != 0) {

			if (ufds[n].fd == wfd[1] && (ufds[n].revents & POLLIN)) {
				nbio_read_wakeup_socket(wfd[1]);
				continue;		/* skip rest of this pass. */
			}
			i = ufds_map[n];
			selitemobj = (*env)->GetObjectArrayElement(env, itemarr, i);
			if (selitemobj == NULL) {
				THROW_EXCEPTION(env, "java/lang/NullPointerException", "SelectItem element is null");
				free(ufds);
				free(ufds_map);
				return 0;
			}

			realevents = 0;
			if (ufds[n].revents & (POLLIN | POLLPRI)) {
				realevents |= SELECTABLE_READ_READY;
			}
			if (ufds[n].revents & POLLOUT) {
				realevents |= SELECTABLE_WRITE_READY;
			}
			if (ufds[n].revents & (POLLERR | POLLHUP | POLLNVAL)) {
				realevents |= SELECTABLE_SELECT_ERROR;
			}

			DEBUG(fprintf(stderr,"NBIO: doSelect: setting itemarr[%d].revents to 0x%x\n", i, realevents));
			(*env)->SetShortField(env, selitemobj, FID_seda_nbio_SelectItem_revents, realevents);
		}
	}

	free(ufds);
	free(ufds_map);

	DEBUG(fprintf(stderr,"NBIO: doSelect: returning %d\n", ret));
 	return ret;
}

JNIEXPORT void JNICALL Java_seda_nbio_SelectSetPollImpl_interruptSelect(JNIEnv *env, jobject this, jint my_id) {
    int *wfd = nbio_pollImpl_init_wakeup(env, my_id);
    nbio_interrupt_select(env, wfd[0]);
}


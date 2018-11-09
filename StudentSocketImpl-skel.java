import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  //   protected InetAddress address;
  //   protected int port;
  //   protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;
  private int windowSize = 20;
  private int seqNum;
  private int ackNum;
  private enum State {
    CLOSED, LISTEN,ESTABLISHED,CLOSING,TIME_WAIT,SYN_SENT,CLOSE_WAIT,LAST_ACK, SYN_RCVD, FIN_WAIT1, FIN_WAIT2;
  }
  private State state;

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
    state = State.CLOSED;
    seqNum = -1;
    ackNum = -1;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException{
    //intialize state
    seqNum = 100;
    localport = D.getNextAvailablePort();

    // register the socket with demultiplexer
    D.registerConnection(address, localport, port,this);

    //send a syn packet to the waiting server
    TCPPacket synPacket = new TCPPacket(localport,port,100,-2,false,true,false,1,null);
    TCPWrapper.send(synPacket, address);
    System.out.println("send syn packet" + synPacket.toString());
    switchState(State.SYN_SENT);
  }

  public synchronized  void switchState(State s){
    System.out.println("state changed from" + state +" to " + s);
    state = s;
  }

  public synchronized void updateAfterRcv(TCPPacket p){
    seqNum = p.ackNum;
    ackNum = p.seqNum + 1;
    address = p.sourceAddr;
    port = p.sourcePort;
  }

  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){
    System.out.println("======In receivePacket()=======");
    System.out.println("state: " + state);
    System.out.println(p.getDebugOutput());
    System.out.println(p.toString());

    //alert any other threads waiting for this event
    this.notifyAll();

    switch(state){
      case LISTEN:
        //receive SYN packet
        if(p.synFlag && !p.ackFlag){
          state = State.SYN_RCVD;
          seqNum = 200;
          ackNum = p.seqNum + 1;
          address = p.sourceAddr;
          port = p.sourcePort;

          //send out syn ack
          TCPPacket synAckPacket = new TCPPacket(localport,port,seqNum , ackNum, true, true, false,1,null);
          TCPWrapper.send(synAckPacket,address);

          try{
            //listner unregister listening connection and re-register as a regular connection
            D.unregisterListeningSocket(localport,this);
            D.registerConnection(p.sourceAddr, p.destPort, p.sourcePort, this);
          }
          catch(IOException e){
            e.printStackTrace();
          }
          switchState(State.SYN_RCVD);

        }

      case SYN_SENT:
        if (p.synFlag && p.ackFlag) {
          updateAfterRcv(p);
          TCPPacket ackPacket = new TCPPacket(localport, port, seqNum, ackNum, true, false, false, 20, null);
          switchState(State.ESTABLISHED);
          TCPWrapper.send(ackPacket, address);
        }
      case SYN_RCVD:
        if(p.ackFlag && !p.synFlag){
          System.out.println("receive ACK");
          switchState(State.ESTABLISHED);
        }
    }

  }

  /**
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling
   * ServerSocket.accept(), but this method belongs to the Socket object
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
    //register the listening connection with the Demultiplexer
    System.out.println("in studentSocketImp.acceptConnection" + localport);
    D.registerListeningSocket(localport,this);
    switchState(State.LISTEN);
    seqNum = 200;

  }


  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;

  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket.
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
  }

  /**
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return
   * information.
   */
  public synchronized void handleTimer(Object ref){

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;
  }
}

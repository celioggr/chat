import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

enum State{
  init,outside,inside
};

class Client {
  SocketChannel sc;
  String nickname;
  String chatroom;
  State state;

  Client(SocketChannel sc) {
    this.sc = sc;
    this.nickname = null;
    chatroom = null;
    state = State.init;
  }

  public void leaveChatRoom(HashMap<String, ChatRoom> rooms ){
    if (this.state == State.inside) {
      
      rooms.get(chatroom).clients.remove(this.sc);
      if (rooms.get(chatroom).clients.size() == 0)
        rooms.remove(chatroom);
      this.chatroom = null;
      this.state = State.outside;
    }
  }
}


class ChatRoom {

  HashMap<SocketChannel, Client>  clients;
  String chatRoom;


  ChatRoom(String name) {
    chatRoom = name;
    clients = new HashMap<SocketChannel, Client>();
  }

//keySets
  Set<SocketChannel> getUserSockets() {
    return clients.keySet();
  }
}

public class ChatServer{

  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  static private final HashMap<String, ChatRoom>    chatRooms = new HashMap<String, ChatRoom>();
  static private final HashMap<SocketChannel, Client> users   = new HashMap<SocketChannel, Client>();


  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
            SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            sc.register( selector, SelectionKey.OP_READ );

            //Add new clients to the users Hashmap
            users.put(sc, new Client(sc));

          } else if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput( sc );

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }

  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc ) throws IOException {
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString();
    
    message=message.replace("\n","");

    if(message.charAt(0)=='/'){

      if(message.charAt(1)=='/' && message.length() > 1){
        parsemsg( message.substring(0) , sc );
        return true;
      }

      parsecmd( message.substring(1) , sc );
      return true;
    }

    parsemsg( message.substring(0) , sc );
    return true;
  }

  static private void parsemsg(String message, SocketChannel sc){

    Client client = users.get(sc); // get the client

    if(client.state != State.inside){
      sendMsgTo("ERROR", sc);
      return;
    }

    //Let's broadcast this to everyone it's not a private msg
    shareToWorld("MESSAGE "+client.nickname+" "+message , client.chatroom, sc,true);

  }

  static private void parsecmd(String cmd, SocketChannel sc) throws IOException{ 
    String arg1=null;
    String arg2=null;
    Scanner scan = new Scanner(cmd);
  
    cmd=scan.next();

    if(scan.hasNext())
      arg1=scan.next();

    if(scan.hasNext())
      arg2=scan.nextLine();

    if((cmd.equals("nick") && arg1==null) || (cmd.equals("join") && arg1==null) || (cmd.equals("priv") && (arg1==null || arg2==null ))){
      sendMsgTo("ERROR",sc); // to the client with this sc
      return;
    }

    switch(cmd) {
    case "nick":
      newNick(arg1, users.get(sc)); //Use the HashMap to get the Client object that corresponds to this socketChannel
      break;
    case "join":
      join(sc, arg1);
      break;
    case "leave":
      leave(sc);
      break;
    case "bye":
      bye(sc);
      break;
    case "priv":
      sendPM(sc, arg1, arg2);
      break;
    default: sendMsgTo("ERROR", sc);
    }
  }

  static private void sendMsgTo(String msg, SocketChannel sc){
    
    try{
      buffer.clear(); // prepare buffer to read a message (set to write mode)
      msg+='\n';
      buffer.put(msg.getBytes());
      buffer.flip(); // prepare buffer to write into Channel (set buffer to read read mode)
      sc.write(buffer);
    }catch(IOException ie){  System.out.println(ie); }
  }

  static private void shareToWorld(String msg, String room, SocketChannel sender, boolean includeMe){
    Set<SocketChannel> peopleAtRoom = chatRooms.get(room).getUserSockets();
    
    for (SocketChannel sc : peopleAtRoom) {
      if (sc.equals(sender) && !includeMe)
        continue;

      sendMsgTo(msg, sc);
    }
  }

  static private void newNick(String newName, Client client){
    if(isnickAvailable(newName)){

      if(client.state == State.init)
        client.state = State.outside;

      if(client.state == State.inside)
        shareToWorld("NEWNICK "+client.nickname+" "+newName, client.chatroom , client.sc, false);

      client.nickname = newName;
      sendMsgTo("OK",client.sc);     
      return;
    }

    sendMsgTo("ERROR" , client.sc );
    return;
  }

  static private boolean isnickAvailable(String nick){
    //search for a nick named 'nick' return something
    //users.values returns the content contained in the map
    for(Client c : users.values())
      if(c.nickname!=null && c.nickname.equals(nick))
        return false;
    
    return true;
  }

  static private void join(SocketChannel sc, String room){
    Client c = users.get(sc);

    if(c.state == State.init){
      sendMsgTo("ERROR" , sc ); // prevent a user to enter a room without a name
      return;
    }

    if(c.state == State.inside){ //this user wants to go to other room
      leave(sc);
      return;
    }

    if (chatRooms.containsKey(room)) {
      chatRooms.get(room).clients.put(sc, c);
      shareToWorld("JOINED " + c.nickname, room, sc, false);
    } else{
      //create a new ChatRoom
      chatRooms.put(room, new ChatRoom(room));
      chatRooms.get(room).clients.put(sc, c);
    }
    c.state= State.inside;
    c.chatroom = room;
    sendMsgTo("OK", sc);
  }

  static private void leave(SocketChannel sc){
    Client c = users.get (sc);

    if(c.state == State.inside){
  
      sendMsgTo("OK", sc);
      shareToWorld("LEFT "+c.nickname, c.chatroom, sc, false);

      c.leaveChatRoom(chatRooms);
      
    }else
      sendMsgTo("ERROR",sc);    
  }

  static private void bye(SocketChannel sc) throws IOException {
    Client client = users.get(sc);

    if(client.state == State.inside){
      shareToWorld("LEFT " + client.nickname, client.chatroom, sc, false);
      client.leaveChatRoom(chatRooms);
      sendMsgTo("BYE", sc);
      sc.close();
    }else
      sendMsgTo("ERROR" , sc);
  }

  static private void sendPM(SocketChannel me, String target, String msg){
    Client c = users.get(me);

    for (Client i : users.values())
      if (i.nickname != null && i.nickname.equals(target)) {
        sendMsgTo("PRIVATE " + c.nickname + " " + msg, i.sc);
        sendMsgTo("OK", me);
        return;
      }

    sendMsgTo("ERROR", me);
  }
}

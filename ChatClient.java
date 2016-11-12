import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

enum State {
    init, outside, inside
};

class Client {
    SocketChannel   sc;
    String          nickname;
    String          chatroom;
    State           state;

    Client(SocketChannel sc) {
        this.sc = sc;
        this.nickname = null;
        chatroom = null;
        state = State.init; // initial state
    }

    void leaveChatRoom(HashMap<String, ChatRoom> rooms) {
        if (this.state == State.inside) {
            rooms.get(chatroom).clients.remove(this.sc);
            if (rooms.get(chatroom).clients.size() == 0)
                rooms.remove(chatroom);
            this.chatroom = null;
            this.state = State.outside;
            return;
        } else
            return;
    }
}

public class ChatClient {

    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    static private ByteBuffer writeBuffer = ByteBuffer.allocate(16384); //write buffer to server
    static private ByteBuffer readBuffer = ByteBuffer.allocate(16384); //read buffer from server
    static private SocketChannel sc;
    static private Charset charset = Charset.forName("UTF8");
    static private CharsetDecoder decoder = charset.newDecoder();

    
	//to append a message 
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    
    // Construtor
    public ChatClient(String server, int port) throws IOException {

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });
        
	sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(new InetSocketAddress(server, port));
        while(!sc.finishConnect()){};

    }


	//This method is called everytime a user writes a message
    public void newMessage(String message) throws IOException {
            
            try{
                if(sc.isConnected()){
                    byte[] send_msg = message.getBytes();
                    writeBuffer.clear();
                    writeBuffer.put(send_msg);
                    writeBuffer.flip(); // switches from writeMode to readMode 
                    sc.write(writeBuffer);     // writes in socketChannel the content of buffer
                }
            }catch (IOException ie){ System.out.println(ie); }
    }

   
    public void run() throws IOException {
        String messageFromServer;


            while(sc.isConnected()){
                readBuffer.clear(); //ready to write mode
                sc.read(readBuffer); //sc write into readBuffer or readBuffer reads sc
                readBuffer.flip(); // ready to read
                messageFromServer = decoder.decode(readBuffer).toString();
                displayMsg(messageFromServer);
            }

            if(!sc.isConnected())
                printMessage("Connection closed\n");
    }

    public void displayMsg(String message){

        if(message.equals(""))
            return;

        message = message.replaceAll("\n", "");
        Scanner scan = new Scanner(message);
        

        String msg_type = scan.next();
        
        switch(msg_type){
            case "MESSAGE":
                printMessage(scan.next()+": "+scan.nextLine()+"\n");
               break;
            case "JOINED":
                printMessage("JOINED "+scan.next()+"\n");
                break;
            case "LEFT":
                printMessage("LEFT "+scan.next()+"\n");
                break;
            case "NEWNICK":
                printMessage(scan.next()+"mudou de nome para "+scan.next()+"\n");
                break;
            case "BYE":
                printMessage("BYE\n");
                break;
            case "OK":
                printMessage("SUCCESS\n");
                break;
            case "PRIVATE":
                printMessage("PRIVATE "+scan.next()+" "+scan.nextLine()+"\n");
                break;
            default:
                printMessage("ERROR\n");
        }

    }
    
    //Instanciates the ChatClient and calls the run() method 
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}

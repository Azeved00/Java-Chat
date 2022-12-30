import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class ChatClient extends Thread {

    // Vars for enviroment 
    private static final boolean Debug = true;

    // Vars for gui 
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();

    // Buffer and socket vars
    private static final int bufferSize = 16384;
    private final ByteBuffer outBuffer = ByteBuffer.allocate(bufferSize);
    private final ByteBuffer inBuffer = ByteBuffer.allocate(bufferSize);
    private SocketChannel Socket = null;

    // Decoder
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    // Add Message to Chat Area
    public void printMessage(final String message) {
		String splited =  message.split(" ",3);
		String toPrint;
		switch(splited[0]){
			case "MESSAGE":
				toPrint = splited[1] + ": " +splited[2];
				break;
			case "JOINED":
				toPrint = splited[1] + " juntou-se ao grupo"; 
				break;
			case "ERROR":
				toPrint = "Houve um erro no comando";
				break;
			case "NEWNICk":
				toPrint = splited[1] + " mudou de nick para " + splited[2];
				break;
			case "PRIVATE":
				toPrint = "Mensagem Privade de " + splited[1] + ": " +splited[2];
				break;
			case "LEFT":
				toPrint = splited[1] + " saiu do chat";
				break;
			case "BYE":
				toPrint = "Até à proxima :)";
				break;
			case "OK":
				toPrint = "Comando Corrido com sucesso";
				break;
			default:
				toPrint = message;
		}
        chatArea.append(toPrint);
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {
        //Create Socket        
        super("Receiver");

        // Graphical Interface
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
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });

        //give name to thread
        Socket = SocketChannel.open(new InetSocketAddress(server, port));
    }

    // Method called each time user clicks enter
    // Send message to server using socket
    public void newMessage(String message) throws IOException {
        if(Debug) System.out.println(message);

        //add termination chars to string
        message = message + "\n\0";

        //clear the buffer and prepare message for transmition
        outBuffer.clear();
        outBuffer.put(message.getBytes());
        outBuffer.flip();

        //while message is not fully sent, send
        while(outBuffer.hasRemaining())
            Socket.write(outBuffer);
    }


    // Called by start method (from Thread)
    // Handles responses from server
    public void run() {
        if(Debug) System.out.println("Thread " + this.getName() + " - Checking for responses from server");

        while(true){
            try{
                inBuffer.clear();
                Socket.read(inBuffer);
                inBuffer.flip();

                String message = decoder.decode(inBuffer).toString();
                printMessage(message);
            }
            catch(Exception e){
                if(Debug) System.out.println(e.getMessage());
            }
        }
    }


    // Create the ChatClient Object
    // initiate new thread and call run method
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.start();
    }
}
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

class User {
    //static methods and attributes
	// Decoder for incoming text -- assume UTF-8
    static private final Charset charset = Charset.forName("UTF8");
    static public final CharsetDecoder decoder = charset.newDecoder(); // public so that ChatServer Can freely decode
    static private final CharsetEncoder encoder = charset.newEncoder();
    static private Map<SocketChannel,User> users = new HashMap<>();
	
    public static User getUser(SocketChannel s){
        return users.get(s);
    }
    public static boolean putUser(SocketChannel s,User u){
        // if the key s is already mapped to a certain user
        // doing put will replace the user in the map with u
        users.put(s,u);
        return true;
    }

    public static User getUserByName(String name) {
        for(Map.Entry<SocketChannel,User> entry : users.entrySet()) {
            if(entry.getValue().getNick().equals(name)) return entry.getValue();
        }

        return null;
    }
    static private Map<String,List<User>> rooms = new HashMap<>();

    //object methods and attributes
    private String nick;
    private String room;
    // state: 0 - init ; 1 - outside; 2 - inside
    private int state;
    private String buffer;
    private SocketChannel socket;

    User(SocketChannel s){
        nick = "";
        room = "";
        state = 0;
        buffer = "";
        socket = s;

        //add new user directly to the map
        users.put(s,this);
    }

    public String getNick(){return this.nick;}
    public void setNick(String n){this.nick = n;}

    public String getRoom(){return this.room;}
    public boolean setRoom(String newRoom){
		if(this.room == newRoom) return true;
		if(this.state == 0) return false;
		List<User> helper = rooms.get(this.room);

		//remove from old room if he is already in a room
        if(this.state == 2){
			for(int i = 0; i < helper.size(); i++) {
				if(helper.get(i).equals(this)) {
					helper.remove(i);
					rooms.remove(this.room);
					rooms.put(this.room,helper);
                    break;
				}
			}
			this.sendMessageRoom("LEFT " + this.nick);
		}

		//update user statistics
        this.state = 2;
        this.room = newRoom;
        if(this.room == ""){ 
			this.state = 1;
			return true;
		}
		
		//add to new room
        helper = rooms.get(this.room);
        if(helper == null) {
            helper = new ArrayList<>();
        } else {
            rooms.remove(newRoom);
        }
		helper.add(this);
        rooms.put(newRoom,helper);
		
        this.sendMessageRoom("JOINED " + this.nick);
		return true;
    }

    public int getState() {
        return this.state;
    }
    public String getBuffer(){return this.buffer;}
    public void setBuffer(String n){this.buffer = n;}
    public void setState(int n){this.state = n;}
    public SocketChannel getSocket() {return this.socket;}
	
	public void delete(){
		users.remove(this.socket);
		System.gc();
	}	
	
	public boolean sendMessageUser (String message) throws IOException{
        this.socket.write(encoder.encode(CharBuffer.wrap(message + '\n')));
        return true;
    }

    public boolean sendMessageRoom (String message) {
		try{
			List<User> roomUsers = rooms.get(this.room);
			for(User i: roomUsers) {
				i.sendMessageUser(message);
			}
		}
		catch(Exception e){
			System.out.println(e.getMessage());
			return false;
		}
		
        return true;
    }

	public List<User> getRoomUsers(){
		return rooms.get(this.room);
	}
}


public class ChatServer {
    // A pre-allocated buffer for the received data
    static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );
    static private List<String> names = new ArrayList<>();

    static public void main( String args[] ) throws Exception {
        // Parse port from command line
        if(args.length <= 0) throw new Exception("Need first argument, the port number!");

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
                    if (key.isAcceptable()) {
                        // It's an incoming connection.  Register this socket with
                        // the Selector so we can listen for input on it
                        Socket s = ss.accept();
                        System.out.println( "Got connection from "+s );

                        // Make sure to make it non-blocking, so we can use a selector
                        // on it.
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking( false );

                        User u = User.getUser(sc);
                        if(u == null) u = new User(sc);
                        
                        // Register it with the selector, for reading
                        sc.register( selector, SelectionKey.OP_READ );
                    }
                    else if (key.isReadable()) {
                        SocketChannel sc = null;

                        try {
                            // It's incoming data on a connection -- process it
                            sc = (SocketChannel)key.channel();
                            boolean ok = processInput(sc);

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
                        }
                        catch( IOException ie ) {
                            // On exception, remove this channel from the selector
                            key.cancel();

                            try {
                                sc.close();
                            }
                            catch( IOException ie2 ) {
                                System.out.println( ie2 );
                            }

                            System.out.println( "Closed "+sc );
                        }
                    }
                }

                // We remove the selected keys, because we've dealt with them.
                keys.clear();
            }
        }
        catch( IOException ie ) {
            System.err.println( ie );
        }
    }

    // Just read the message from the socket and send it to stdout
    static private boolean processInput( SocketChannel sc ) throws Exception {
        int r = -1;
        // Read the message to the buffer
        buffer.clear();
        sc.read( buffer );
        buffer.flip();
        User u = User.getUser(sc);
        if(u == null) throw new Exception ("User not found");
		
        // If no data, close the connection
        if (buffer.limit()==0) {
			//removing name of user that was closed
			// take him from room and user maps
			u.delete();
			u.setRoom("");
			int index = -1;
			for(int i = 0; i < names.size(); i++) {
				if(u.getNick().equals(names.get(i))) 
					index = i;
			}
			if(index != -1) names.remove(index);
            return false;
        }

        // Decode and print the message to stdout
        String message = User.decoder.decode(buffer).toString();

		message = message.substring(0,message.length()-1);
		message = message.strip();

        String[] splited2 = message.split("\n");

        for(String message1: splited2) {
            System.out.println(message1);
            String[] splited = message1.split(" ");

            if(splited[0] == "") r = 0;

            if((splited[0].charAt(0) == '/') && (splited[0].charAt(1) != '/')){
                if(processCommand(u,splited))
                    r = 0;
            } else {
                processMessage(u,splited);
                r = 0;
            }
        }

        if(r == 0) return true;
								
		//removing name of user that was closed
		// take him from room and user maps
		u.delete();
		u.setRoom("");
		int index = -1;
		for(int i = 0; i < names.size(); i++) {
			if(u.getNick().equals(names.get(i))) 
				index = i;
		}
		if(index != -1) names.remove(index);

        return false;
    }

    static private boolean processMessage (User u, String[] message) throws IOException{
        if((message[0].length() < 1) || (u.getState() != 2)) {
            u.sendMessageUser("ERROR");
            return false;
        }

        if(message[0].charAt(0) == '/') message[0] = message[0].substring(1);
		String msg = String.join(" ", message);
		System.out.println("New message from " + u.getNick() +" to " + u.getRoom()+ " -> " + msg);
        //process message
        u.sendMessageRoom("MESSAGE " + u.getNick() +" " + msg);

        return true;
    }

    static private boolean processCommand (User u, String[] message) throws IOException{
		System.out.println("Nick: " + u.getNick() + " Group: " + u.getRoom() + " State:  " + u.getState()+ " ran command: " + message[0]);
        switch(message[0]) {
            case "/nick":
                //System.out.println("<" + message[1] + ">");
                if((message.length != 2) || (message[1].length() < 1) || (message[1].charAt(0) == '\n')) {
                    u.sendMessageUser("ERROR");
                    break;
                }

                String old = u.getNick();

                int index = -1;
				boolean br = true;
                for(int i = 0; i < names.size(); i++) {
					if(u.getNick().equals(names.get(i))) index = i;
                    if(message[1].equals(names.get(i))) {
                        u.sendMessageUser("ERROR");
                        br = false;
						break;
                    }
                }
				if(!br) break;
                if(index != -1) names.remove(index);

                if(u.getState() == 0) u.setState(1);
                names.add(message[1]);
                u.setNick(message[1]);
                User.putUser(u.getSocket(),u);

                 switch(u.getState()) {
                     case 2:
                         u.sendMessageRoom("NEWNICK " + old + " " + u.getNick());
                     case 0:
                     case 1:
                         u.sendMessageUser("OK");
                         break;
                 }
                break;

            case "/join":
                if((message.length != 2) || (message[1].length() < 1) || (message[1].charAt(0) == '\n')) {
                    u.sendMessageUser("ERROR");
                    break;
                    //return false;
                }
				
				if(u.setRoom(message[1])) 
					u.sendMessageUser("OK");
				else 
					u.sendMessageUser("ERROR");

                break;

            case "/leave":
                //System.out.println("antes do getState");
                if((message.length != 1) || (u.getState()!=2)) {
                    //System.out.println("depois do getState");
                    u.sendMessageUser("ERROR");
                    break;
                    //return false;
                }

                if(u.setRoom("")) 
					u.sendMessageUser("OK");
				else 
					u.sendMessageUser("ERROR");

                break;

            case "/bye":

                if(message.length != 1) {
                    u.sendMessageUser("ERROR");
                    return false;
                }
				
				
                switch(u.getState()) {
                    case 2:
						u.setRoom("");
                        u.sendMessageRoom("LEFT " + u.getNick());
                    case 0:
                    case 1:
                        u.sendMessageUser("BYE");
                        return false;
                }

                break;

            case "/priv":
                if(message.length < 3) {
                    u.sendMessageUser("ERROR");
                    break;
                }

                User dest = User.getUserByName(message[1]);
                if(dest == null) {
                    u.sendMessageUser("ERROR");
                    break;
                }

                System.out.println(":" + message[1] + ":");
                System.out.println(":" + dest.getNick() + ":");

                if(dest == null) u.sendMessageUser("ERROR");
                else {
                    List<String> m = Arrays.asList(message);
                    String fin = String.join(" ",m.subList(2,m.size()));

                    dest.sendMessageUser("PRIVATE " + u.getNick() + " " + fin);
                    u.sendMessageUser("OK");
                }

                break;

            default:
                String msg = String.join(" ", message);
                msg = msg.substring(1,msg.length());
				System.out.println("Unknown command " + msg);
                processMessage(u, msg.split(" "));
        }

        return true;
    }
}


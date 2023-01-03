# Java-Chat
Java application for chatting

# How to Start
## Sever
Start the server on a machine by running `javac src/ChatServer.java && java ChatServer port` where port is the number of the port you want to open the server

## Client
Start the client app by running `javac src/ChatClient.java && java ChatClient machine port` where machine is the ip of the server (if it is your own pc then localhost will do) and port is the port in which the server is running

# Commands

| Command | Description |
| :---: | :--- |
| `/nick n` | Change your nick to `n` |
| `/join s` | Join the room `s` |
| `/leave` | Leave the room |
| `/bye` | Close the connection to the server |
| `/priv u message` | Send a private message to user `u` |

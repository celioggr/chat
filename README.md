### Chat
A simple yet functional chat written in Java

# Abstract
Project carried out in late 2015 under the class of Communication Networks with guidance from our professor Rui Prior.

The main purpose was understanding the baseline development of a communication network over TCP.

# Table of Contents

1. [Compiling]()

2. [Command line]()

3. [Protocol]()

4. [Authors]()



# Compiling

 To compile the files just type the following command:
 
 `javac ChatServer.java && javac ChatClient.java`



# Command line

- The server is implemented in a class named ChatServer and accept as command line argument the TCP port number where it will be listening, for example:
`java ChatServer 8000`

- The client is implemented in a classe named ChatClient and accept as command line arguments the DNS name of the server to connect to and the respective TCP port number, for example:
`java ChatClient localhost 8000`


# Protocol

The communication protocol is text-line-oriented, i.e., each message sent by the client to the server or by the server to the client must end with a newline, and messages themselves cannot contain newlines.
Messages sent by the client to the server may be commands of simple messages.
Commands have the format `/comand`, sometimes with additional arguments separated by spaces. Simple messages can only be sent when the user is inside a chat room.

* The server must support the following commands:
  
  
    * `/nick name`
       
       Used for choosing a name or changing it.
  
  
    * `/join room`
       
       Used for entering a chat room or switching to a different one. If the room does not yet exist, it will be created.
  
  
    * `/leave`
       
       Used for leaving the current chat room.
  
  
    * `/bye` 
       
       Used for leaving the chat.
    
  
* The server can sent the following messages:

    * `OK`
    
        Used for signalling success of the command sent by the client.
    * `ERROR`
    
        Used for signalling insuccess of the command sent by the client.
    * `MESSAGE name message`
    
        Used to broadcast to users in a room the (simple) message sent by user name in the same room.
    * `NEWNICK old_name new_name`
    
      Used for notifying all users in a room that user old_name in the same room switched name to new_name.
    * `JOINED name`
    
      Used for notifying all users in a room that a new user, name, just entered the room.
    * `LEFT name` 
    
      Used to notify all users in a room that user name, previously in the room, has just left.
    * `BYE`
    
      Used for acknowledgement of the /bye command.


* The server maintains, associated with each client, state information:

      * `init`
      
     Initial state for a user that has just established a connection to the server and, therefore, does not yet have a nickname.

    * `outside`
  
      The user already has a nickname, but is not yet inside a chat room.
  
    * `inside`

      The user is inside a chat room, and can send simple messages (to that room) and should receive all messages sent by the other users in that room.

# Authors

* Célio Rodrigues celioggr@gmail.com @github/celioggr
* Iúri Tavares Pena @github/iuritpena

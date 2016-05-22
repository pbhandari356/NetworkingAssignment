1) 
Dhirajkumar Patel - dpatel87@gatech.edu
Prashant Bhandari - pbhandari8@gatech.edu

2)
CS 3251, Section A
11/26/15
Programming Assignment 2 - Reliable Transport Protocol

3)
Files submitted:
FxAClient.java - File application client
FxAServer.java - File application server
RxPHeader.java - Header class file for transport protocol
RxPPacket.java - Packet class file for transport protocol
RxPAPI.java - Class file for reliable transport protocol
NetEmu.py - Provided file to help simulate data corruptions
README.txt - this readme file
Sample.txt - sample output from running the project
cs.txt - sample file to use in file transfer


4)
Instructions for compiling:

Using a Windows platform, make sure you have the Java SDK installed.
Run the following command in the command line to compile each java file:
	javac *java

Instructions for running:

Have three command prompts open at once. 
On one command prompt start the NetEmu.py simulator as such: 
	python NetEmu.py <port1> 
where port can something like 5000. 

On the second command prompt, run the file application server as such: 
	java FxAServer <port2> <IP> <port1>
where port2 is an odd port such as 8081, IP can be the local host address 127.0.0.1 and port1 is the same one used when running NetEmu.

On the third command prompt, run the file application client as such:
	java FxAClient <port3> <IP> <port1>
where port3 is port2 - 1 (such as 8080 if port2 was 8081), IP can be the local host address 127.0.0.1 and port1 is the same one used when running NetEmu.  



5)
Updated Protocol and API Description:

A connection between the sender and receiver is established using a 4-way handshake. The sender initiates the communication by sending a SYN packet to the receiver. The receiver responds by generating a random challenge string and sending it to the sender. The sender constructs an MD5 hash on the challenge string and sends it to the receiver. The receiver also constructs a hash and compares to the one received from the sender. If they match, the connection is established, and the receiver sends an ACK.

For the stop-and-wait protocol, the client sends a request to download a file from the server. The server sends one packet at a time (each packet has 28 header bytes and 255 data bytes) and waits until an ACK from the client is received with the same sequence number as the sent packet. It continues doing this until all packets are sent and acknowledged. Packets are checked for corruption by checking the checksum each time they are received. The sequence numbers are used for lost and duplicate packets. For stop-and-wait, packets cannot arrive out-of-order.

For the sliding window protocol, the client program provides a command to change the window size (the default size is 1, in which case a stop-and-wait protocol is used). If the window size is changed to more than 1, the sliding window protocol will be used for each file download. The server will send consecutive packets equal to the window size in one thread. In another thread, it will receive ACK packets from the client. It will resend any packets in the window whose ACKs were not received. Once all packets in the current window are received, the window is shifted and the process continues until the entire file is sent.

The client can run the disconnect command to end the connection with the server. Meanwhile, the server program also listens for input such as "terminate" to shut down the server. 


API:

The following methods are used by the client file transfer program:

createConnection():
	The client calls this method of the API when it wants to connect to the server. The method handles the 4-way handshake with the server. A message is displayed indicating whether the connection was successful.

recvFrom(String filename):
	The client calls this method when it wants to download a file from the server. 

updateWindow(String win):
	The client calls this method when it wants to configure the flow window size of the file transfer.

close();
	The client calls this method when it wants to disconnect from the server.


There is just one method used by the server program:

listen():
	The server calls this method when it starts. It continually listens for clients trying to communicate with it.


6)
Known bugs/limitations:

- When trying to download files one after another on the same connection, it seems to take longer to download. To test different conditions in the NetEmu, the best results are obtained by restarting the client and server each time.
- The post (upload file) command is not supported.





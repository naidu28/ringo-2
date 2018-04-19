Name/Email: Sai Naidu, sai@gatech.edu
Name/Email: Andrew Ray, aray40@gatech.edu

Files:
  * App.java - initial execution begins here (contains main method)
  * Ringo.java - class containing all network functionality
  * RingoPacket.java - packet class for all network communication
  * Role.java - enum describing role of node
  * PacketType.java - enum describing type of packet

Instructions:
  * Have Java 8 installed on your machine
  * Run "java -jar ringo.jar <flag> <local-port> <PoC-name> <PoC-port> <N>"

Bugs/Limitations:
  * Network initialization usually takes around 30 seconds
  * Network initialization happens much more quickly if nodes are started in order of Point-of-Contacts
  * Occasionally (< 5% of the time) is prone to hang if all nodes are started out of Point-of-Contact order
      - In these cases it may take up to 1 minute per phase (e.g. "Starting peer discovery...", "Starting RTT Vector creation...", "Starting RTT Matrix convergence...")
      - If taking longer than that, restart all N nodes (extremely rare occurrence)
  * File with a specific filename (example: "test.txt") can only be sent once on network successfully
      - If you would like to repeat a file transmission, put RECEIVER node into churn (disconnect) and delete the received file
      - If the above doesn't work, restart the entire network
  * When a file is received, it is saved as [original filename] + "received." + [extension]
      - This was a specific design decision to allow for easily identifying original file and received file if located within the same directory

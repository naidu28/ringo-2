Name/Email: Sai Naidu, sai@gatech.edu
Name/Email: Andrew Ray, aray40@gatech.edu

Files:
  * App.java - initial execution begins here (contains main method)
  * Ringo.java - class containing all network functionality
  * RingoPacket.java - packet class for all network communication
  * Role.java - enum describing role of node
  * PacketType.java - enum describing type of packet
  * ArgumentChecker.java - Small helper class that checks and parses arguments
  * HostInformation.java - Information Holder class that holds the state of a Ringo
  * HostState.java - Enum that describes the uptime status of a Ringo
  * KeepAlive.java - Class that responds to KeepAlive requests,
                     and has a schedulable update method that notifies the RingTracker
                     of each Ringo's uptime state
  * KeepAliveTimerTask.java - Class that runs every 4 seconds, calling KeepAlive's update method
  * Pair.java - Simple information holding class that keeps two coupled objects together
  * RingoPacketFactory.java - Simple helper class that create packets that originate from *this* Ringo
  * RingTracker.java - Class that keeps and maintains the Ring structure for the Ringo.

Instructions:
  * Have Java 8 installed on your machine
  * Run "java -jar ringo.jar <flag> <local-port> <PoC-name> <PoC-port> <N>"
  * If no jarfile is available, run "java App.java <flag> <local-port> <PoC-name> <PoC-port> <N>"

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

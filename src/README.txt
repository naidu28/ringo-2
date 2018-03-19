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

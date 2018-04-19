import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.Thread;
import java.util.Scanner;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Date;

import java.io.IOException;

/**
 * The Ringo class represents a network node on the Ringo network.
 * It runs in a child thread of the main process, which belongs to App.java.
 * It's main functions are: providing a user interface, managing child threads
 * to coordinate network input and network output, and keeping connections with
 * it's peers alive. It is essentially the backbone of the Ringo protocol.
 * 
 * @author sainaidu
 * @author andrewray
 */
public class Ringo implements Runnable {
	DatagramSocket socket;
	private final Role role;
	private String localName;
	private final int localPort;
	private String pocName;
	private final int pocPort;
	private final int ringSize;
	private LinkedBlockingQueue<String> userCommandList;

	private Hashtable<String, Integer> lsa;
	private Hashtable<String, Integer> rttIndex;
	private Hashtable<Integer, String> indexRtt;
	private long [][] rtt;
	private LinkedBlockingQueue<RingoPacket> recvQueue;
	private LinkedBlockingQueue<RingoPacket> sendQueue;
	private LinkedBlockingQueue<String> sendFileList;
	private LinkedBlockingQueue<String> outputQueue;
	private ArrayList<String> ringRoute;
	private LinkedBlockingQueue<RingoPacket> keepAliveQueue;
	private RingTracker tracker;
	private RingoPacketFactory factory;
	private KeepAlive keepalive;
	private Thread keepAliveThread;
	private boolean initialized;

	/**
	 * The constructor accepts all of the command-line arguments specified in the
	 * reference material
	 */
	public Ringo(Role role, int localPort, String pocName, int pocPort, int ringSize, DatagramSocket socket, LinkedBlockingQueue<String> userCommandList) {		
		this.userCommandList = userCommandList;
		this.socket = socket;
		this.role = role;
		this.localName = "";
		try {
			this.localName = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.localPort = localPort;
		this.pocName = pocName;
		if (this.pocName != null) {
			try {
				if (this.pocName.equals("localhost")) {
					this.pocName = InetAddress.getLocalHost().getHostAddress();
				} else {
					this.pocName = InetAddress.getByName(pocName).getHostAddress();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		this.pocPort = pocPort;
		this.ringSize = ringSize;
		this.lsa = new Hashtable<String, Integer>();
		this.rttIndex = new Hashtable<String, Integer>();
		this.indexRtt = new Hashtable<Integer, String>();
		this.rtt = new long[ringSize][ringSize];
		for (int i = 0; i < this.rtt.length; i++) {
			for (int j = 0; j < this.rtt.length; j++) {
				this.rtt[i][j] = -1;
			}
		}
		this.recvQueue = new LinkedBlockingQueue<RingoPacket>();
		this.sendQueue = new LinkedBlockingQueue<RingoPacket>();
		this.sendFileList = new LinkedBlockingQueue<String>();
		this.outputQueue = new LinkedBlockingQueue<String>();
		this.ringRoute = null;
		this.keepAliveQueue = new LinkedBlockingQueue<RingoPacket>();
		this.factory = new RingoPacketFactory(localName, localPort, role, ringSize);
		this.initialized = false;
	}

	/**
	 * This function is the first step in thread execution.
	 * 
	 * Since this class is used as a Thread, when it is instantiated
	 * it immediately invokes this function.
	 * 
	 * This function acts as the initializer for this Ringo's network
	 * activities and contains the logic for the user interface.
	 * 
	 * It is the single-point-of-failure in this class and is thus
	 * the "most important" function overall.
	 */
	public void run() {
		LinkedBlockingQueue<RingoPacket> recvQueue = this.recvQueue;
		LinkedBlockingQueue<RingoPacket> sendQueue = this.sendQueue;
		LinkedBlockingQueue<RingoPacket> keepAliveQueue = this.keepAliveQueue;
		

		Thread netIn = new Thread(new ReceiverThread(recvQueue, keepAliveQueue, sendQueue));
		Thread netOut = new Thread(new SenderThread(sendQueue));
		netIn.start();
		netOut.start();
		
		if (this.pocName != null) {
			RingoPacket responseIn = null;
			RingoPacket packet = new RingoPacket(this.localName, this.localPort, this.pocName, this.pocPort, 0, 0, PacketType.PING_REQ, this.role, this.ringSize);
			sendQueue.add(packet);
			responseIn = this.takeType(recvQueue, PacketType.PING_RES);
			if (responseIn == null) {
				System.out.println("\nPoint of contact not reachable currently. Continuing to attempt connection...");
			}
			
			while(responseIn == null) {
				packet = new RingoPacket(this.localName, this.localPort, this.pocName, this.pocPort, 0, 0, PacketType.PING_REQ, this.role, this.ringSize);
				sendQueue.add(packet);
				responseIn = this.takeType(recvQueue, PacketType.PING_RES);
				try {
					Thread.sleep(200);
				} catch (Exception e) {
					
				}
				
				// System.out.println("recvqueue: " +this.recvQueue);
				// System.out.println("sendqueue: " +this.sendQueue);
			}
		}
		
		recvQueue.clear();
		sendQueue.clear();
		
		boolean skip = false;
		if (this.pocName != null) {
			System.out.println("\nAsking for Initialization state from PoC");
			skip = checkInit();
			flushType(recvQueue, PacketType.INIT_RES);
			flushType(sendQueue, PacketType.INIT_RES);
		}
		
		if (!skip) {
			System.out.println("\nStarting peer discovery...");
			peerDiscovery(recvQueue, sendQueue);
		  /*try {
		  	Thread.sleep(500);
		  } catch (Exception e) {
		  	e.printStackTrace();
		  }*/
			System.out.println("Peer discovery complete!\n");
			flushType(recvQueue, PacketType.LSA);
			flushType(recvQueue, PacketType.LSA_COMPLETE);
			System.out.println("Starting RTT Vector creation...");
			rttVectorGeneration(recvQueue, sendQueue);
		  /*try {
		  	Thread.sleep(6000);
		  } catch (Exception e) {
		  	e.printStackTrace();
		  }*/
			System.out.println("RTT Vector creation complete!\n");
			flushType(recvQueue, PacketType.LSA);
			flushType(recvQueue, PacketType.LSA_COMPLETE);
			flushType(recvQueue, PacketType.PING_RES);
			flushType(recvQueue, PacketType.PING_COMPLETE);
		  /*try {
		  	Thread.sleep(3000);
		  } catch (Exception e) {
		  	e.printStackTrace();
		  }*/
			flushType(recvQueue, PacketType.PING_RES);
			flushType(recvQueue, PacketType.PING_COMPLETE);
			System.out.println("Starting RTT Matrix convergence...");
			rttConvergence(recvQueue, sendQueue);
		  flushType(recvQueue, PacketType.PING_COMPLETE);
		  flushType(recvQueue, PacketType.RTT_RES);
		  flushType(recvQueue, PacketType.RTT_COMPLETE);
		  /*try {
		  	Thread.sleep(3000);
		  } catch (Exception e) {
		  	e.printStackTrace();
		  }*/
		}

		System.out.println("RTT Matrix convergence complete!");
		System.out.println("Network is ready to use.\n");
		Scanner scanner = new Scanner(System.in);
		
		// (String me, long[][] rtt, Hashtable<Integer, String> indexRTT)
		
		tracker = new RingTracker(this.localName + ":" + this.localPort, rtt, indexRtt);
		keepalive = new KeepAlive(keepAliveQueue, sendQueue, factory, tracker);
		keepAliveThread = new Thread(keepalive);
		keepAliveThread.start();

		this.ringRoute = generateOptimalRing();
		executionLoop(netIn, netOut, tracker, keepalive);
		// System.out.println(this.lsa);
	}
	
	private void executionLoop(Thread netIn, Thread netOut, RingTracker tracker, KeepAlive keepalive) {
		WorkerThread workerObject = new WorkerThread(this.role, this.sendQueue, this.recvQueue, this.ringRoute, this.localName, this.localPort, this.sendFileList, this.outputQueue, tracker);
		Thread worker = new Thread(workerObject);
		worker.start();
		
		while (true) {
			System.out.println("Enter any of the following commands: show-matrix, show-ring, show-next, disconnect");
			String command = "";
			
			Scanner scanner = new Scanner(System.in);
			//while (scanner.hasNext()) {
			command = scanner.nextLine();
			//}
			//if (!this.userCommandList.isEmpty()) {
			/*try {
				command = this.userCommandList.take();
			} catch (Exception e) {
				e.printStackTrace();
			}*/
			
			// System.out.println("results of le split: " +command.split(" ")[0]);
			if (command.equals("")) {
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			if (command.split(" ")[0].equalsIgnoreCase("offline")) {
				System.out.println("\n");
			} else if (command.split(" ")[0].equalsIgnoreCase("send")) {
				if (this.role != Role.SENDER) {
					System.out.println("Unfortunately this is not a SENDER ringo; Try again from the SENDER ringo");
				} else {
					if (command.split(" ").length > 1) {
						System.out.println("reached here");
						this.sendFileList.add(command.split(" ")[1]);
					} else {
						System.out.println("You did not provide enough arguments for SEND.");
					}
				}
			} else if (command.equalsIgnoreCase("show-matrix")) {
				  System.out.println(tracker.getMatrix());
			} else if (command.equalsIgnoreCase("show-ring")) {
				  ArrayList<String> output = tracker.getRoute();

				System.out.println("\t");

				for (int i = 0; i < output.size(); i++) {
					if (i != output.size() - 1)
						System.out.print(output.get(i)+" -> ");
					else
						System.out.println(output.get(i));
				}
				System.out.println("");
			} else if (command.equalsIgnoreCase("show-next")) {
				System.out.println(tracker.getNextRingo().hostString());
			} else if (command.equalsIgnoreCase("disconnect")) {
				netIn.interrupt();
				netOut.interrupt();
				return;
			} else {
				System.out.println("Sorry, but your input was invalid. Try again.");
			}
			//}
		}
	}

	private boolean checkInit() {
		boolean skip = false;
		boolean done = false;
		while (!done) {
			RingoPacket req = factory.makePacket(pocName, pocPort, 0, 0, PacketType.INIT_REQ);
			System.out.println("init request packet: " +req);
			RingoPacket res = null;
			try {
				sendQueue.put(req);
				res = recvQueue.take();
			} catch (InterruptedException e) {
				// nah
			}
			
			if (res.getType() == PacketType.INIT_RES) {
				done = true;
				skip = res.getInitSkip();
				if (skip) {
					this.rtt = res.getRtt();
					this.lsa = res.getLsa();
					this.rttIndex = res.getRttIndex();
					this.indexRtt = res.getIndexRtt();
				}
			}
			
			try {
				Thread.sleep(300);
			} catch (Exception e) {
				
			}
			// if not, drop the packet
		}
		flushType(recvQueue, PacketType.INIT_REQ);
		flushType(sendQueue, PacketType.INIT_RES);
		return skip;
	}

	/**
	 * Performs peer discovery using two distinct phases to promote
	 * a higher likelihood of success across an unreliable network.
	 * 
	 * Utilizes two types of packets - ordinary LSA packets which
	 * are used to communicate LSA table entries, and LSA_COMPLETE
	 * packets which are broadcasted across the network to indicate
	 * that this Ringo has successfully discovered all N-1 peers.
	 * 
	 * LSA packets are sent continuously across the network until
	 * a completed LSA table is created, and peer discovery at this 
	 * node is finished.
	 * 
	 * Once discovery is completed for this node, consensus must be 
	 * established to exit this function. This ensures that all nodes
	 * will synchronize all of their network initialization procedures.
	 * Consensus is reached when this node receives an LSA_COMPLETE
	 * packet from all N neighbors. This node simultaneously sends
	 * LSA_COMPLETE packets to all its N-1 neighbors continuously.
	 * 
	 * @param recvQueue - concurrency-safe queue that holds all packets received from the network buffer
	 * @param sendQueue - concurrency-safe queue that holds all packets waiting to be sent from the network buffer
	 */
	private void peerDiscovery(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		Hashtable<String, Boolean> converged = new Hashtable<String, Boolean>();
		this.lsa.put(this.localName+":"+this.localPort, 1);
		if (this.lsa.size() < ringSize) {
			converged.put(this.localName+":"+this.localPort, false);
		} else {
			converged.put(this.localName+":"+this.localPort, true);
		}
		
		// System.out.println("ay");
		
		if (this.pocName != null) {
			this.lsa.put(this.pocName+":"+this.pocPort, 1);
			converged.put(this.pocName+":"+this.pocPort, false);

			RingoPacket packet = new RingoPacket(this.localName, this.localPort, this.pocName, this.pocPort, 0, 0, PacketType.LSA, this.role, this.ringSize);
			packet.setLsa(this.lsa);
			sendQueue.add(packet);
		}

		// System.out.println("bee");
		while (!isLsaConverged(converged)) {
			// System.out.println("recvqueue: " +this.recvQueue);
			// System.out.println("sendqueue: " +this.sendQueue);
			// listen for responses from all nodes
			// and respond with corresponding LSA vectors
			if (!converged.get(this.localName+":"+this.localPort)) {
				try {
					RingoPacket request = recvQueue.take();
					this.lsa.putAll(request.getLsa());

					if (request.getType() != PacketType.LSA_COMPLETE) {
						if (!converged.containsKey(request.getSourceIP()+":"+request.getSourcePort()))
							converged.put(request.getSourceIP()+":"+request.getSourcePort(), false);
					} else {
						converged.put(request.getSourceIP()+":"+request.getSourcePort(), true);
					}

					try {
						Thread.sleep(500);
					} catch (Exception e) {
						System.out.println(e);
					}
					
					System.out.println(this.lsa);
					// System.out.println("send queue: " +sendQueue);
					// System.out.println("recv queue: " +recvQueue);
					Iterator iter = this.lsa.keySet().iterator();

					while (iter.hasNext()) {
						String key = (String) iter.next();
						//System.out.println("stuff: " +request.getDestIP());
						RingoPacket response = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.LSA, this.role, this.ringSize);
						response.setLsa(this.lsa);
						
						sendQueue.add(response);
					}

				} catch (Exception e) {
					// handle later
					System.out.println("this exception is e: " +e);
				}
			} else {
				try {
					RingoPacket request = recvQueue.take();
					this.lsa.putAll(request.getLsa());

					if (request.getType() != PacketType.LSA_COMPLETE) {
						converged.put(request.getSourceIP()+":"+request.getSourcePort(), false);
					} else {
						converged.put(request.getSourceIP()+":"+request.getSourcePort(), true);
					}

					try {
						Thread.sleep(500);
					} catch (Exception e) {
						System.out.println(e);
					}
					
					System.out.println(converged);
					
					Iterator iter = this.lsa.keySet().iterator();

					while (iter.hasNext()) {
						String key = (String) iter.next();
						if (!key.equals(this.localName+":"+this.localPort)) {
							RingoPacket packet = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.LSA_COMPLETE, this.role, this.ringSize);
							packet.setLsa(this.lsa);
							
							sendQueue.add(packet);
						}
					}
				} catch (Exception e) {
					System.out.println(e);
				}
			}
		}
		
		for (int i = 0; i < 5; i++) {
			Iterator iter = this.lsa.keySet().iterator();
			
			while (iter.hasNext()) {
				String key = (String) iter.next();
				
				RingoPacket packet = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.LSA_COMPLETE, this.role, this.ringSize);
				packet.setLsa(this.lsa);
				sendQueue.add(packet);
			}
		}
	}

	/**
	 * Helper function for consensus phase of peer discovery. Compares 
	 * the elements of the parameter "converged" with this node's LSA
	 * table. If both contain all identical elements, peer discovery
	 * can be considered complete.
	 * 
	 * 
	 * @param converged - data structure containing all nodes that have sent LSA_COMPLETE nodes to this Ringo.
	 * @return true if peer discovery is complete, false otherwise
	 */
	private boolean isLsaConverged(Hashtable<String, Boolean> converged) {
		if (this.lsa.size() >= ringSize) {
			converged.put(this.localName+":"+this.localPort, true);
		} else {
			return false;
		}

		Iterator iter = this.lsa.keySet().iterator();

		while (iter.hasNext()) {
			String key = (String) iter.next();
			// if it's not the localhost
			// if it hasn't converged
			// then we return false
			if (converged.containsKey(key)) {
				if (!key.equals(this.localName+":"+this.localPort) && !converged.get(key)) {
					return false;
				}
			} else {
				return false;
			}
		}

		return true;
	}
	
	/**
	 * Generates an RTT vector between this node and all its N peers.
	 * Uses the PING_REQ and PING_RES type packets to find the RTT value
	 * for a given node. A PING_RES packet always contains a timestamp
	 * indicating when its corresponding PING_REQ request was first sent 
	 * and a timestamp indicating when it was first received at this node. 
	 * The difference of these values results in our RTT value.
	 * 
	 * As with peer discovery, consensus must be established across
	 * the network to finish the RTT vector generation process. This is 
	 * performed in the same way as peer discovery, utilizing
	 * PING_COMPLETE packets to communicate completion.
	 * 
	 * @param recvQueue - concurrency-safe queue that holds all packets received from the network buffer
	 * @param sendQueue - concurrency-safe queue that holds all packets waiting to be sent from the network buffer
	 */
	private void rttVectorGeneration(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		HashSet<String> converged = new HashSet<String>();
		String localkey = this.localName+":"+this.localPort;

		int n = 0;
		this.rttIndex.put(localkey, n);
		this.indexRtt.put(n, localkey);
		this.rtt[this.rttIndex.get(localkey)][this.rttIndex.get(localkey)] = 0;
		n++;

		// ping requests
		//while (!converged.containsAll(this.lsa.keySet())) {
		while(!isRttVectorComplete()) {
			// send packets to all nodes
			Iterator iter = this.lsa.keySet().iterator();

			while (iter.hasNext()) {
				String key = (String) iter.next();
				//System.out.println("rttIndex: " +this.rttIndex);
				if (!this.rttIndex.containsKey(key) && !key.equals(localkey)) {
					RingoPacket requestOut = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING_REQ, this.role, this.ringSize);
					sendQueue.add(requestOut);

					RingoPacket responseIn = null;
					responseIn = takeType(recvQueue, PacketType.PING_RES);

					if (responseIn != null && !this.rttIndex.containsKey(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
						assignRtt(responseIn, n, responseIn.getStopTime() - responseIn.getStartTime());
						n++;
					}
					//System.out.println(sendQueue);
				}

				try {
					Thread.sleep(400);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		converged.add(localkey);

		while(!converged.containsAll(this.lsa.keySet())) {
			Iterator iter = this.lsa.keySet().iterator();

			while (iter.hasNext()) {
				String key = (String) iter.next();
				RingoPacket request = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING_COMPLETE, this.role, this.ringSize);
				sendQueue.add(request);

				if (!key.equals(localkey) && !converged.contains(key)) {
					RingoPacket response = null;
					response = takeType(recvQueue, PacketType.PING_COMPLETE);
					if (response != null && !converged.contains(response.getSourceIP()+":"+response.getSourcePort())) {
						converged.add(response.getSourceIP()+":"+response.getSourcePort());
					}
				}

				try {
					Thread.sleep(200);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			System.out.println("converged data structure: " +converged);
		}

		for (int i = 0; i < 5; i++) {
			Iterator iter = this.lsa.keySet().iterator();

			while (iter.hasNext()) {
				String key = (String) iter.next();
				RingoPacket request = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING_COMPLETE, this.role, this.ringSize);
				sendQueue.add(request);
			}
			try {
				Thread.sleep(200);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Helper function to identify completion of RTT vector. Checks 
	 * the elements in this node's RTT vector to see if any default values
	 * (in this project they are -1) still exist.
	 * 
	 * @return true if RTT vector is complete, false otherwise
	 */
	private boolean isRttVectorComplete() {
		for (int j = 0; j < this.rtt[0].length; j++) {
			//System.out.println("value in rtt at: " +j+ " is " +this.rtt[0][j]);
			if (this.rtt[0][j] == -1) {
				// not converged for these hosts
				return false;
			}
		}

		return true;
	}

	/**
	 * Multiple data structures are used to maintain the RTT matrix.
	 * These data structures must be used cleanly, at risk of 
	 * damaging this Ringo's network capabilities.
	 * 
	 * This function isolates all code related to RTT vector writing
	 * into a single location.
	 * 
	 * @param packet - packet from which we are writing into our RTT matrix
	 * @param index - index this packet's RTT vector will be assigned in our RTT matrix.
	 * @param rtt - value for RTT from this node and this packet's source node
	 */
	private void assignRtt(RingoPacket packet, int index, long rtt) {
		try {
			InetAddress src = InetAddress.getLocalHost();
			String localkey = src.getHostAddress()+":"+this.localPort;

			this.rttIndex.put(packet.getSourceIP()+":"+packet.getSourcePort(), index);
			this.indexRtt.put(index, packet.getSourceIP()+":"+packet.getSourcePort());

			//this.rtt[rttIndex.get(packet.getSourceIP()+":"+packet.getSourcePort())][rttIndex.get(localkey)] = rtt;
			this.rtt[rttIndex.get(localkey)][rttIndex.get(packet.getSourceIP()+":"+packet.getSourcePort())] = rtt;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Final phase of network initialization - convergence of RTT matrices
	 * across the network.
	 * 
	 * The logic is modeled in exactly the same way as RTT vector generation.
	 * First we send and receive RTT_REQ and RTT_RES packets to construct
	 * a complete RTT matrix, and then we handle consensus in the same way
	 * as previous phases of initialization.
	 * 
	 * To handle consensus for RTT convergence, we use the RTT_COMPLETE type
	 * packet to signify to peers that our matrix is complete. Consensus
	 * is effectively reached when we receive a unique RTT_COMPLETE packet
	 * from all N-1 peers.
	 * 
	 * @param recvQueue - concurrency-safe queue that holds all packets received from the network buffer
	 * @param sendQueue - concurrency-safe queue that holds all packets waiting to be sent from the network buffer
	 */
	private void rttConvergence(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		HashSet<String> converged = new HashSet<String>();
		String localkey = this.localName+":"+this.localPort;

		HashSet<String> addedToMatrix = new HashSet<String>();
		addedToMatrix.add(this.localName+":"+this.localPort);

		while (!isRttConverged()) {
			Iterator iter = this.lsa.keySet().iterator();

			while (iter.hasNext()) {
				String key = (String) iter.next();

				if (!key.equals(this.localName+":"+this.localPort) && !addedToMatrix.contains(key)) {
					RingoPacket requestOut = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.RTT_REQ, Ringo.this.role, this.ringSize);
					sendQueue.add(requestOut);

					//System.out.println("rtt index: " +this.rttIndex);

					RingoPacket responseIn = null;
					responseIn = takeType(recvQueue, PacketType.RTT_RES);

					if (responseIn != null && !addedToMatrix.contains(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
						addRttVectorToMatrix(responseIn);
						addedToMatrix.add(responseIn.getSourceIP()+":"+responseIn.getSourcePort());
					}
				}

				try {
					Thread.sleep(400);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		converged.add(localkey);

		while(!converged.containsAll(this.lsa.keySet())) {
			Iterator iter = this.lsa.keySet().iterator();

			while (iter.hasNext()) {
				String key = (String) iter.next();
				RingoPacket request = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.RTT_COMPLETE, this.role, this.ringSize);
				sendQueue.add(request);

				if (!key.equals(localkey) && !converged.contains(key)) {
					RingoPacket response = null;
					response = takeType(recvQueue, PacketType.RTT_COMPLETE);
					if (response != null && !converged.contains(response.getSourceIP()+":"+response.getSourcePort())) {
						converged.add(response.getSourceIP()+":"+response.getSourcePort());
					}
				}

				try {
					Thread.sleep(200);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			System.out.println("converged data structure: " +converged);
		}
		
		for (int i = 0; i < 3; i++) {
			Iterator iter = this.lsa.keySet().iterator();
	
			while (iter.hasNext()) {
				String key = (String) iter.next();
				RingoPacket request = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.RTT_COMPLETE, this.role, this.ringSize);
				sendQueue.add(request);
	
				if (!key.equals(localkey) && !converged.contains(key)) {
					RingoPacket response = null;
					response = takeType(recvQueue, PacketType.RTT_COMPLETE);
					if (response != null && !converged.contains(response.getSourceIP()+":"+response.getSourcePort())) {
						converged.add(response.getSourceIP()+":"+response.getSourcePort());
					}
				}
	
				/*try {
					Thread.sleep(200);
				} catch (Exception e) {
					e.printStackTrace();
				}*/
			}
		}
	}

	/**
	 * Helper function to identify completion of RTT matrix. Checks 
	 * the elements in this node's RTT matrix to see if any default values
	 * (in this project they are -1) still exist.
	 * 
	 * @return true if RTT matrix is complete, false otherwise
	 */
	private boolean isRttConverged() {
		for (int i = 0; i < this.rtt.length; i++) {
			for (int j = 0; j < this.rtt[0].length; j++) {
				//System.out.println("value in rtt at: " +j+ " is " +this.rtt[0][j]);
				if (this.rtt[i][j] == -1) {
					// not converged for these hosts
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Multiple data structures are used to maintain the RTT matrix.
	 * These data structures must be used cleanly, at risk of 
	 * damaging this Ringo's network capabilities.
	 * 
	 * This function isolates all code related to RTT matrix writing
	 * into a single location.
	 * 
	 * @param packet - packet from which we are writing into our RTT matrix
	 */
	private void addRttVectorToMatrix(RingoPacket packet) {
		String packetKey = packet.getSourceIP()+":"+packet.getSourcePort();
		long [][] packetRtt = packet.getRtt();
		//System.out.println(packetRtt);

		for (int i = 0; i < packetRtt.length; i++) {
			String otherKey = packet.getIndexRtt().get(i);
			if (otherKey == null) {
				return;
			}

			long rttVal = packetRtt[0][i];
			this.rtt[this.rttIndex.get(packetKey)][this.rttIndex.get(otherKey)] = rttVal;
		}

	}

	/**
	 * Used to flush the "queue" parameter of all packets of a specific
	 * type. Helpful when queue is clogged after any network initialization
	 * phase, such as peer discovery.
	 * 
	 * @param queue - concurrency-safe queue that holds all packets for sending or receiving
	 * @param type - type of packet to flush from this queue
	 */
	private void flushType(LinkedBlockingQueue<RingoPacket> queue, PacketType type) {
		Iterator iter = queue.iterator();

		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == type) {
				iter.remove();
			}
		}
	}
	
	private void flushSpecific(LinkedBlockingQueue<RingoPacket> queue, PacketType type, String hostname, int port) {

		Iterator iter = queue.iterator();

		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == type && packet.getSourceIP().equals(hostname) && packet.getSourcePort() == port) {
				iter.remove();
			}
		}
	}
	
	private void flushData(LinkedBlockingQueue<RingoPacket> queue, String hostname, int port, int seqNumber) {
		Iterator iter = queue.iterator();

		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == PacketType.DATA && packet.getSourceIP().equals(hostname) && packet.getSourcePort() == port && packet.getSequenceNumber() < seqNumber) {
				iter.remove();
			}
		}
	}

	/**
	 * Since the "queue" parameter contains many different types of packets,
	 * it can be inconvenient when trying to access packets of a specific type
	 * due to the FIFO nature of this data structure. We can bypass this 
	 * characteristic of our queue by providing the type we are looking for.
	 * Returns the first packet of PacketType type in the queue.
	 * 
	 * @param queue - concurrency-safe queue that holds all packets for sending or receiving
	 * @param type - type of packet to take from this queue
	 * @return RingoPacket if found, null otherwise
	 */
	private RingoPacket takeType(LinkedBlockingQueue<RingoPacket> queue, PacketType type) {
		Iterator iter = queue.iterator();

		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == type) {
				iter.remove();
				return packet;
			}
		}

		return null;
	}

	/**
	 * Since the "queue" parameter contains many different types of packets,
	 * it can be inconvenient when trying to access a specific packet
	 * due to the FIFO nature of this data structure. We can bypass this 
	 * characteristic of our queue by providing basic information of what
	 * we are looking for.
	 * 
	 * Returns the first packet matching all parameters in this queue.
	 * 
	 * @param queue - concurrency-safe queue that holds all packets for sending or receiving
	 * @param type - type of packet to take from this queue
	 * @param hostname - source hostname of the packet to take from this queue
	 * @param port - source port of the packet to take from this queue
	 * @return RingoPacket if found, null otherwise
	 */
	private RingoPacket takeSpecific(LinkedBlockingQueue<RingoPacket> queue, PacketType type, String hostname, int port) {
		Iterator iter = queue.iterator();
		int maxAck = -1;
		
		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == type && packet.getSourceIP().equals(hostname) && packet.getSourcePort() == port) {
				if (packet.getType() == PacketType.DATA_ACK && maxAck < packet.getSequenceNumber()) {
					maxAck = packet.getSequenceNumber();
				} else {
					iter.remove();
					/*if (packet.getType() == PacketType.DATA) {
						System.out.println("data packet: " +packet.getSequenceNumber());
					}*/
					return packet;
				}
			}
		}
		
		if (type == PacketType.DATA_ACK) {
			iter = queue.iterator();
			
			while (iter.hasNext()) {
				RingoPacket packet = (RingoPacket) iter.next();
				
				if (packet.getType() == type && packet.getSourceIP().equals(hostname) && packet.getSourcePort() == port) {
					if (packet.getType() == PacketType.DATA_ACK && maxAck == packet.getSequenceNumber()) {
						iter.remove();
						/*if (packet.getType() == PacketType.DATA) {
							System.out.println("data packet: " +packet.getSequenceNumber());
						}*/
						return packet;
					}
				}
			}
		}

		return null;
	}
	
	private RingoPacket takeNextDataPacket(LinkedBlockingQueue<RingoPacket> queue, String hostname, int port, int seqNum) {
		Iterator iter = queue.iterator();
		int maxAck = -1;
		
		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == PacketType.DATA && packet.getSourceIP().equals(hostname) && packet.getSourcePort() == port && packet.getSequenceNumber() > seqNum) {
				iter.remove();
				/*if (packet.getType() == PacketType.DATA) {
					System.out.println("data packet: " +packet.getSequenceNumber());
				}*/
				return packet;
			}
		}

		return null;
	}

	/**
	 * Contains the function call to a recursive Traveling Salesman Problem
	 * solution. Used to find the "fastest" or optimal path in our ring
	 * network.
	 * 
	 * Converts a list of RTT matrix indices returned from the "recurseNetwork"
	 * into a corresponding list of hostname:port combinations
	 * 
	 * @return ArrayList<String> containing the optimal ring path in "[hostname]:[port]" format
	 */
	public ArrayList<String> generateOptimalRing() {
		Iterator iter = this.lsa.keySet().iterator();
		ArrayList<Long> currRoute = new ArrayList<Long>();
		ArrayList<Long> minRoute = new ArrayList<Long>();

		while (iter.hasNext()) {
			String start = (String) iter.next();
			Set<String> unvisited = new HashSet<String>(this.lsa.keySet());
			Set<String> visited = new HashSet<String>();
			currRoute = recurseNetwork(start, unvisited, visited);

			if (minRoute.size() > 0) {
				if (currRoute.get(0) < minRoute.get(0)) {
					minRoute = currRoute;
				}
			} else {
				minRoute = currRoute;
			}
		}

		ArrayList<String> toReturn = new ArrayList<String>();

		for (int i = 1; i < minRoute.size(); i++) {
			toReturn.add(this.indexRtt.get(minRoute.get(i).intValue()));
		}
		
		this.ringRoute = toReturn;
		return toReturn;
	}

	/**
	 * Custom Traveling Salesman Problem implementation. Recurses
	 * through network using neighbor list to find the "fastest" route
	 * beginning from the "curr" parameter.
	 * 
	 * @return ArrayList<Long> containing the optimal ring path represented as indices of the RTT matrix
	 */
	private ArrayList<Long> recurseNetwork(String curr, Set<String> unvisited, Set<String> visited) {
		ArrayList<ArrayList<Long>> paths = new ArrayList<ArrayList<Long>>();
		long currIndex = (long) this.rttIndex.get(curr);
		visited.add(curr);

		if (visited.size() == unvisited.size()) {
			ArrayList<Long> toReturn = new ArrayList<Long>();
			toReturn.add((long) 0);
			toReturn.add(currIndex);
			return toReturn;
		}

		Iterator iter = unvisited.iterator();

		// remove curr from unvisited nodes and brute-force search at every neighbor
		while (iter.hasNext()) {
			String neighbor = (String) iter.next();

			if (!visited.contains(neighbor)) {
				//System.out.println("gotta recurse");
				ArrayList<Long> path = recurseNetwork(neighbor, unvisited, visited);
				paths.add(path);
			}
		}

		//System.out.println(paths);
		ArrayList<Long> minPath = paths.get(0);

		for (int i = 1; i < paths.size(); i++) {
			if (paths.get(i).get(0) < minPath.get(0)) {
				minPath = paths.get(i);
			}
		}

		long prevIndex = minPath.get(minPath.size() - 1);
		long rtt = this.rtt[(int) currIndex][(int) prevIndex];
		minPath.set(0, minPath.get(0) + rtt);
		minPath.add(currIndex);

		return minPath;

	}
	
	/**
	 * This Thread handles all inbound network functions.
	 * Puts all received and serialized packets into parent class
	 * field "recvQueue", a multithreaded data structure.
	 * 
	 * @author sainaidu
	 * @author andrewray
	 */
	private class ReceiverThread implements Runnable {
		LinkedBlockingQueue<RingoPacket> packetQueue;
		LinkedBlockingQueue<RingoPacket> keepAliveQueue;
		LinkedBlockingQueue<RingoPacket> outQueue;

		private ReceiverThread(LinkedBlockingQueue<RingoPacket> dataQueue, 
				LinkedBlockingQueue<RingoPacket> keepAliveQueue,
				LinkedBlockingQueue<RingoPacket> outQueue) {
			this.packetQueue = dataQueue;
			this.keepAliveQueue = keepAliveQueue;
			this.outQueue = outQueue;
		}

		public void run() {
			// loop to track received packets
			while(true) {
				// receiving datagram packets
				try {
					DatagramPacket UDPpacket = receive();
					String sentence = new String(UDPpacket.getData());
					deserializeAndEnqueue(UDPpacket.getData());
				} catch (IOException e) {
					// handle later
				}
			}
		}

		private DatagramPacket receive() throws IOException {
			byte[] data = new byte[20000];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			Ringo.this.socket.receive(packet);
			return packet;
		}

		private void deserializeAndEnqueue(byte [] data) {
			try {
				RingoPacket packet = RingoPacket.deserialize(data);
				packet.setStopTime(System.currentTimeMillis());
				if (packet != null) {
					replaceDuplicates(packet);
					if (packet.getType() == PacketType.PING_REQ) {
						RingoPacket responseOut = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, packet.getSourceIP(), packet.getSourcePort(), 0, 0, PacketType.PING_RES, Ringo.this.role, Ringo.this.ringSize);
						responseOut.setStartTime(packet.getStartTime()); // to generate RTT we have to use other packet's start time
						//System.out.println("this is the response I'm returning back boys " +responseOut);
						Ringo.this.sendQueue.add(responseOut);
					} else if (packet.getType() == PacketType.RTT_REQ){
						RingoPacket responseOut = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, packet.getSourceIP(), packet.getSourcePort(), 0, 0, PacketType.RTT_RES, Ringo.this.role, Ringo.this.ringSize);
						responseOut.setRtt(Ringo.this.rtt);
						responseOut.setRttIndex(Ringo.this.rttIndex);
						responseOut.setIndexRtt(Ringo.this.indexRtt);
						Ringo.this.sendQueue.add(responseOut);
					} else if (packet.getType() == PacketType.DATA) {
						// System.out.println("Received DATA packet sequence number: " +packet.getSequenceNumber());
						this.packetQueue.add(packet);
					} else if (packet.getType() == PacketType.DATA_ACK) {
						// System.out.println("Received DATA_ACK packet sequence number: " +packet.getSequenceNumber());
						this.packetQueue.add(packet);
				  } else if (packet.getType() == PacketType.KEEPALIVE) {
				  	this.keepAliveQueue.add(packet);
				  } else if (packet.getType() == PacketType.INIT_REQ) {
				  	RingoPacket res = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, packet.getSourceIP(), packet.getSourcePort(), 0, 0, PacketType.INIT_RES, Ringo.this.role, Ringo.this.ringSize);
				  	if (Ringo.this.initialized) {
				  		res.setIndexRtt(Ringo.this.indexRtt);
				  		res.setRttIndex(Ringo.this.rttIndex);
				  		res.setRtt(Ringo.this.rtt);
				  		res.setLsa(Ringo.this.lsa);
				  		res.setInitSkip(true);
				  	} else {
				  		res.setInitSkip(false);
				  	}
				  	
				  	try {
				  		sendQueue.put(res);
				  	} catch (InterruptedException e) {
				  		e.printStackTrace();
				  	}
				  } else {
				  	this.packetQueue.add(packet);
				  }
				}
			} catch (Exception e) {
				// stream corruption etc
			}
		}

		private void replaceDuplicates(RingoPacket packet) {
			Iterator iter = this.packetQueue.iterator();

			while (iter.hasNext()) {
				RingoPacket entry = (RingoPacket) iter.next();

				if (entry.equals(packet)) {
					entry.replace(packet);
					break;
				}
			}

			while (iter.hasNext()) {
				RingoPacket entry = (RingoPacket) iter.next();

				if (entry.equals(packet)) {
					iter.remove();
				}
			}
		}
	}

	/**
	 * This Thread handles all outbound network functions.
	 * Puts all received and serialized packets into parent class
	 * field "recvQueue", a multithreaded data structure.
	 * 
	 * @author sainaidu
	 * @author andrewray
	 */
	private class SenderThread implements Runnable {
		LinkedBlockingQueue<RingoPacket> packetQueue;

		private SenderThread(LinkedBlockingQueue<RingoPacket> packetQueue) {
			this.packetQueue = packetQueue;
		}

		public void run() {
			while(true) {
				RingoPacket packet = dequeue();
				if (packet != null) {
					// System.out.println("current time start: " +System.currentTimeMillis());
					if (packet.getType() == PacketType.DATA || packet.getType() == PacketType.DATA_ACK) {
						// System.out.println("Sent DATA packet sequence number: " +packet.getSequenceNumber());
					} else {
						// replaceDuplicates(packet);
					}
					
					packet.setStartTime(System.currentTimeMillis());
					byte [] data = RingoPacket.serialize(packet);
					DatagramPacket udpPacket = createDatagram(data, packet);
					if (udpPacket != null) {
						try {
							Ringo.this.socket.send(udpPacket);
						} catch (Exception e) {
							// handle later
						}
					} else {

					}
				} else {

				}
			}
		}

		private DatagramPacket createDatagram(byte [] data, RingoPacket ringoPacket) {
			try {
				InetAddress dst = InetAddress.getByName(ringoPacket.getDestIP());
				int port = ringoPacket.getDestPort();
				DatagramPacket udppacket = new DatagramPacket(data, data.length, dst, port);
				return udppacket;
			} catch(Exception e) { // if host is unknown
				// handle later
				System.out.println("one exception at this locaiton: " +e);
				return null;
			}
		}

		private RingoPacket dequeue() {
			if (!this.packetQueue.isEmpty()) {
				try {
					RingoPacket packet = this.packetQueue.take();
					return packet;
				} catch (InterruptedException e) {
					// handle this later
					return null;
				}
			} else {
				return null;
			}
		}

		private void replaceDuplicates(RingoPacket packet) {
			Iterator iter = this.packetQueue.iterator();

			while (iter.hasNext()) {
				RingoPacket entry = (RingoPacket) iter.next();

				if (entry.equals(packet)) {
					packet.replace(entry);
					iter.remove();
				}
			}
		}
	}
	
	private class WorkerThread implements Runnable {
		private Role role;
		private LinkedBlockingQueue<RingoPacket> sendQueue;
		private LinkedBlockingQueue<RingoPacket> recvQueue;
		private ArrayList<String> route;
		private String localName;
		private int localPort;
		private RingoPacket [] window;
		private RingoPacket [] file;
		private String fileName;
		private RingTracker tracker;
		
		
		private boolean [] accepted;
		private RingoPacket [] acks;
		private int highestSequenceAccepted;
		private LinkedBlockingQueue<String> sendFileList;
		private LinkedBlockingQueue<String> outputQueue;
		
		public WorkerThread(Role role, LinkedBlockingQueue<RingoPacket> sendQueue, LinkedBlockingQueue<RingoPacket> recvQueue, ArrayList<String> route, String localName, int localPort, LinkedBlockingQueue<String> sendFileList, LinkedBlockingQueue<String> outputQueue, RingTracker tracker) {
			this.role = role;
			this.sendQueue = sendQueue;
			this.recvQueue = recvQueue;
			this.route = route;
			this.localName = localName;
			this.localPort = localPort;
			this.window = new RingoPacket[50];
			this.accepted = new boolean[window.length];
			this.acks = new RingoPacket[window.length];
			this.sendFileList = sendFileList;
			this.outputQueue = outputQueue;
			this.fileName = "";
			this.tracker = tracker;
		}
		
		public void run() {
			// System.out.println("this code should be printed out continuously");
			while (true) {
				// System.out.println("I assume you've been reached");
				// starts sending a file if this node is SENDER and this node has a file to send
				if (this.role == Role.SENDER) {
					// System.out.println("yo");
					flushType(this.sendQueue, PacketType.DATA);
					if (!this.sendFileList.isEmpty()) {
						// System.out.println("2");
						sendFile(this.sendFileList.poll());
					}
					flushType(this.sendQueue, PacketType.DATA);
					flushType(this.recvQueue, PacketType.DATA_ACK);
				}
				
				// transfer packets from receive queue to send queue
				try {
					flushType(this.recvQueue, PacketType.DATA);
					flushType(this.sendQueue, PacketType.DATA_ACK);
					transportFile();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		// used by SENDER
		private void sendFile(String filepath) {
			File file = new File(filepath);
			// Hashtable<String, Boolean> windowAckList = new Hashtable<String, Boolean>();
			// System.out.println("file: " + file);
			
			try {
				FileInputStream fileReader;
				fileReader = new FileInputStream(file);
				this.route = tracker.getRoute();
				
				int seqNumber = 0;
				byte [] data = new byte[10000];
				Long seqLength = (long)Math.ceil(file.length()/10000.0);
				
				this.window = new RingoPacket[seqLength.intValue()];
				
				while (fileReader.read(data) != -1) {
					RingoPacket toSend = this.createSendPacket(data, seqNumber, seqLength);
					toSend.setFileName(filepath);
					toSend.setRoute(this.route);
					this.window[seqNumber%this.window.length] = toSend;
					/*if (seqNumber%this.window.length == this.window.length - 1) {
						try {
							if (transmitWindow(this.window, this.window.length - 1) != this.window[this.window.length - 1].getSequenceNumber()) {
								System.out.println("y this happen");
								if (true) {
									seqNumber = seqNumber - window.length;
								}
							} else {
								System.out.println("it actually worked out");
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}*/
					
					seqNumber++;
					if (seqNumber == seqLength.intValue() - 1) {
						Long arrSize = file.length()%10000;
						data = new byte[arrSize.intValue()];
					} else {
						data = new byte[10000];
					}
				}
				
				try {
					int highestAcked = transmitWindow(this.window, seqLength.intValue());
					while (highestAcked < seqLength.intValue() - 1) {
						// if churn not occurring
						if (!tracker.isOnline(this.window[0].getDestIP()+":"+this.window[0].getDestPort())) {
							System.out.println("next node is churning");
							for (int i = 0; i < this.window.length; i++) {
								String destRingo = getPrevRingo();
								RingoPacket filePacket = this.window[i];
								filePacket.setSourceIP(this.localName);
								filePacket.setSourcePort(this.localPort);
								filePacket.setDestIP(destRingo.substring(0, destRingo.indexOf(":")));
								filePacket.setDestPort(Integer.parseInt(destRingo.substring(destRingo.indexOf(":") + 1)));
							}
						}
						
						System.out.println("one possible outcome");
						highestAcked = transmitWindow(this.window, seqLength.intValue());
					}
					
					System.out.println("Sent the whole file");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
				
		private RingoPacket createSendPacket(byte [] data, int seqNumber, long seqLength) {
			// need to define a keep-alive method that returns the next host name and port
			// RingoPacket toSend = new RingoPacket(this.localName, this.localPort, this.keepAlive.nextHost, this.keepAlive.nextPort, 0, seqNumber, PacketType.DATA, this.role, this.ringSize);
			String next = getNextRingo();
			
			RingoPacket toSend;
			
			if (data == null) {
				toSend = new RingoPacket(this.localName, this.localPort, next.substring(0, next.indexOf(":")), Integer.parseInt(next.substring(next.indexOf(":") + 1)), seqLength, seqNumber, PacketType.DATA, this.role, 0);
			} else {
				toSend = null;
				try {
					toSend = new RingoPacket(this.localName, this.localPort, next.substring(0, next.indexOf(":")), Integer.parseInt(next.substring(next.indexOf(":") + 1)), seqLength, seqNumber, PacketType.DATA, this.role, 0);
				} catch (Exception e) {
					e.printStackTrace();
				}
				toSend.setPayload(data);
			}
			
			return toSend;
		}
		
		private void transportFile() {
			// if there is a DATA packet in the receiveQueue
			// finishing FORWARDING or RECEIVING entire file
			// if ANY:
				// every time a DATA packet is received, return the "highestSequenceAck"
				// stop when highestSequenceAck is >= seqLength - 1
			// if RECEIVING:
				// receive DATA packets and store in file object
			// if FORWARDING:
				// once enough DATA packets to fill up window are acquired, call transmitWindow
			// System.out.println("z");
			
			String lastRingo = getPrevRingo();
			RingoPacket filePacket = takeSpecific(this.recvQueue, PacketType.DATA, lastRingo.substring(0, lastRingo.indexOf(":")), Integer.parseInt(lastRingo.substring(lastRingo.indexOf(":") + 1)));
			if (filePacket == null) {
				lastRingo = getNextRingo();
				filePacket = takeSpecific(this.recvQueue, PacketType.DATA, lastRingo.substring(0, lastRingo.indexOf(":")), Integer.parseInt(lastRingo.substring(lastRingo.indexOf(":") + 1)));
			}
			
			if (filePacket != null && !this.fileName.equals(filePacket.getFileName())) {
				String hostName = filePacket.getSourceIP();
				int hostPort = filePacket.getSourcePort();
				int ackNum = filePacket.getSequenceNumber();
				Long seqLength = filePacket.getSequenceLength();
				this.route = filePacket.getRoute();
				
				boolean [] accepted = new boolean[seqLength.intValue()];
				this.window = new RingoPacket[seqLength.intValue()];
				this.file = new RingoPacket[seqLength.intValue()];
				this.file[filePacket.getSequenceNumber()] = filePacket;
				System.out.println("packet # " +filePacket.getSequenceNumber() + " saved to file");
				
				String fileName = filePacket.getFileName();
				
				while (ackNum < seqLength - 1) {
					//Role-specific logic
					// if receiver:
					/*if ((this.role == Role.RECEIVER || this.role == Role.FORWARDER) && accepted[filePacket.getSequenceNumber()] == false) {
						this.file[filePacket.getSequenceNumber()] = filePacket;
						// this.file[filePacket.getSequenceNumber()] = filePacket;
					}*/
					
					// create an ack for this data packet
					RingoPacket ack = createAck(filePacket, ackNum);
					this.sendQueue.add(ack);
					
					// update data structures to show that we've obtained a DATA packet
					accepted[filePacket.getSequenceNumber()] = true;
					
					/*String destRingo = getNextRingo();
					filePacket.setSourceIP(this.localName);
					filePacket.setSourcePort(this.localPort);
					filePacket.setDestIP(destRingo.substring(0, destRingo.indexOf(":")));
					filePacket.setDestPort(Integer.parseInt(destRingo.substring(destRingo.indexOf(":") + 1)));
					this.window[filePacket.getSequenceNumber()%this.window.length] = filePacket;
					
					// if receiver or forwarder:
					// send full window of packets to next in route
					// use transmitWindow
					if (this.role == Role.RECEIVER || this.role == Role.FORWARDER) {
						if (ackNum == this.window.length - 1) {
							while (transmitWindow(this.window, this.window.length - 1) != window.length - 1) {
								// check for churn
								// else try again
								System.out.println("not receiving all acks during forwarding");
							}
							
							System.out.println("All acks received");
						}
					}*/
					
					// update ack
					ackNum = getAckNum(accepted);
					if (ackNum == 35) {
						System.out.println("34: " +accepted[34]);
						System.out.println("35: " +accepted[35]);
						System.out.println("36: " +accepted[36]);
					}
					// flushData(this.recvQueue, hostName, hostPort, ackNum);
					
					// get the next data packet in the sequence
					if (ackNum < seqLength - 1) {
						filePacket = takeSpecific(this.recvQueue, PacketType.DATA, lastRingo.substring(0, lastRingo.indexOf(":")), Integer.parseInt(lastRingo.substring(lastRingo.indexOf(":") + 1)));
						while (filePacket == null) {
							filePacket = takeSpecific(this.recvQueue, PacketType.DATA, lastRingo.substring(0, lastRingo.indexOf(":")), Integer.parseInt(lastRingo.substring(lastRingo.indexOf(":") + 1)));
						}
						
						if ((this.role == Role.RECEIVER || this.role == Role.FORWARDER) && accepted[filePacket.getSequenceNumber()] == false) {
							System.out.println("packet # " +filePacket.getSequenceNumber() + " saved to file");
							this.file[filePacket.getSequenceNumber()] = filePacket;
							// this.file[filePacket.getSequenceNumber()] = filePacket;
						}
					}
					
					System.out.println("ackNum: " +ackNum);
					System.out.println("seqLength: " +seqLength);
				}
				
				flushType(this.recvQueue, PacketType.DATA);
				
				/*if (this.role == Role.RECEIVER || this.role == Role.FORWARDER) {
					this.file[filePacket.getSequenceNumber()] = filePacket;
				}*/
				
				System.out.println("finished receiving file");
				
				if (this.role == Role.RECEIVER) {
					writeFile(fileName);
					this.fileName = fileName;
				}
				
				for (int i = 0; i < 5; i++) {
					RingoPacket ack = createAck(filePacket, ackNum);
					this.sendQueue.add(ack);
				}
				
				if (this.role == Role.RECEIVER || this.role == Role.FORWARDER) {
					for (int i = 0; i < this.file.length; i++) {
						String destRingo = getNextRingo();
						filePacket = this.file[i];
						filePacket.setSourceIP(this.localName);
						filePacket.setSourcePort(this.localPort);
						filePacket.setDestIP(destRingo.substring(0, destRingo.indexOf(":")));
						filePacket.setDestPort(Integer.parseInt(destRingo.substring(destRingo.indexOf(":") + 1)));
						this.window[i] = filePacket;
					}
					
					int highestAck = transmitWindow(this.window, this.window.length - 1);
					while (highestAck < this.window.length - 1) {
						// check for churn
						if (!tracker.isOnline(this.window[0].getDestIP()+":"+this.window[0].getDestPort())) {
							System.out.println("next node is churning");
							for (int i = 0; i < this.file.length; i++) {
								String destRingo = getPrevRingo();
								filePacket = this.file[i];
								filePacket.setSourceIP(this.localName);
								filePacket.setSourcePort(this.localPort);
								filePacket.setDestIP(destRingo.substring(0, destRingo.indexOf(":")));
								filePacket.setDestPort(Integer.parseInt(destRingo.substring(destRingo.indexOf(":") + 1)));
								this.window[i] = filePacket;
							}
						}
						// else try again
						System.out.println("not receiving all acks during forwarding");
						highestAck = transmitWindow(this.window, this.window.length - 1);
					}
					
					System.out.println("All acks received");
				}
				
				
				flushSpecific(this.recvQueue, PacketType.DATA, hostName, hostPort);
				System.out.println(this.recvQueue);
			}
		}
		
		private void writeFile(String fileName) {
			FileOutputStream fop = null;
			File writeFile;
			
			try {
				writeFile = new File(fileName.substring(0, fileName.indexOf(".")) + "-received" + fileName.substring(fileName.indexOf(".")));
				fop = new FileOutputStream(writeFile, true);

				// if file doesnt exists, then create it
				if (!writeFile.exists()) {
					writeFile.createNewFile();
				}
				
				for (int i = 0; i < this.file.length; i++) {
					System.out.println("sequence number: " +this.file[i].getSequenceNumber());
					fop.write(this.file[i].getPayload());
				}
				
				fop.flush();
				fop.close();

				System.out.println("Done writing");
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (fop != null) {
						fop.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		private int getAckNum(boolean [] accepted) {
			/*while (ackNum < accepted.length && accepted[ackNum] != false) {
				ackNum++;
			}*/
			
			for (int i = 0; i < accepted.length; i++) {
				if (accepted[i] == false) {
					return i - 1;
				}
			}
			
			return accepted.length - 1;
		}
		
		private RingoPacket createAck(RingoPacket base, int ackNum) {
			RingoPacket ack = new RingoPacket(this.localName, this.localPort, base.getSourceIP(), base.getSourcePort(), base.getSequenceLength(), ackNum, PacketType.DATA_ACK, this.role, 0);
			return ack;
		}
		
		// gets previous ringo in route arraylist
		private String getPrevRingo() {
			if (this.route.get(0).equals(this.localName + ":" + this.localPort)) {
				return this.route.get(this.route.size() - 1);
			}
			
			for (int i = 1; i < this.route.size(); i++) {
				if (this.route.get(i).equals(this.localName + ":" + this.localPort)) {
					return this.route.get(i - 1);
				}
			}
			
			return "";
		}
		
		// gets next ringo in route arraylist
		private String getNextRingo() {			
			for (int i = 0; i < this.route.size() - 1; i++) {
				if (this.route.get(i).equals(this.localName + ":" + this.localPort)) {
					return this.route.get(i + 1);
				}
			}
			
			if (this.route.get(this.route.size() - 1).equals(this.localName + ":" + this.localPort)) {
				return this.route.get(0);
			}
			
			return "";
		}
		
		/*private void transferFile() {
			System.out.println("1");
			String prevHost = "";
			int prevPort = -1;
			
			String nextHost = "";
			int nextPort = -1;
			
			System.out.println("2");
			for (int i = 1; i < this.route.size(); i++) {
				if (this.route.get(i) == this.localName +":"+ this.localPort) {
					prevHost = this.route.get(i - 1).substring(0, this.route.get(i - 1).indexOf(":"));
					prevPort = Integer.parseInt(this.route.get(i).substring(this.route.get(i - 1).indexOf(":")));
					
					nextHost = this.route.get((i + 1)%this.route.size()).substring(0, this.route.get((i + 1)%this.route.size()).indexOf(":"));
					nextPort = Integer.parseInt(this.route.get((i + 1)%this.route.size()).substring(this.route.get((i + 1)%this.route.size()).indexOf(":") + 1));
				}
			}
			
			System.out.println("3");
			// System.out.println(this.window[0]);
			int highestAck = 0;
			
			try {
				if (this.accepted[0] == false) {
					System.out.println("20");
					int lastIndex = receiveWindow(prevHost, prevPort, nextHost, nextPort);
					System.out.println("30");
					if (this.accepted[0] == true) 
						highestAck = transmitWindow(this.window, lastIndex);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			System.out.println("4");
			
			
			while (accepted[0] == true && highestAck < this.window[0].getSequenceLength() - 1) {
				int lastIndex = receiveWindow(prevHost, prevPort, nextHost, nextPort);
				highestAck = transmitWindow(this.window, lastIndex);
			}
			
			System.out.println("5");
		}*/
		
		/*private int receiveWindow(String prevHost, int prevPort, String nextHost, int nextPort) {
			try {
				RingoPacket filePacket = takeSpecific(this.recvQueue, PacketType.DATA, prevHost, prevPort);
				
				if (filePacket != null) {
					this.highestSequenceAccepted = filePacket.getSequenceNumber();
					while (this.highestSequenceAccepted < filePacket.getSequenceLength() && this.highestSequenceAccepted - filePacket.getSequenceNumber() < this.window.length) {
						filePacket.setSourceIP(prevHost);
						filePacket.setSourcePort(prevPort);
						filePacket.setDestIP(nextHost);
						filePacket.setDestPort(nextPort);
						this.window[filePacket.getSequenceNumber()%this.window.length] = filePacket;
						this.acks[filePacket.getSequenceNumber()%this.window.length] = filePacket;
						this.accepted[filePacket.getSequenceNumber()%this.window.length] = true;
						
						int ackSeqCount = 0;
						while (ackSeqCount < this.accepted.length && this.accepted[ackSeqCount] == true) {
							this.highestSequenceAccepted = this.acks[ackSeqCount].getSequenceNumber();
						}
						
						while (filePacket == null) {
							filePacket = takeSpecific(this.recvQueue, PacketType.DATA, prevHost, prevPort);
						}
					}
					
					return this.highestSequenceAccepted - filePacket.getSequenceNumber();
				} else {
					this.highestSequenceAccepted = -1;
					return this.highestSequenceAccepted;
				}
				
				
			} catch (Exception e) {
				e.printStackTrace();
				return -1;
			}
		}*/
	
		// used by SENDER, FORWARDER, and RECEIVER
		private int transmitWindow(RingoPacket [] window, int lastIndex) {
			// implement go-back-N with cumulative ACK approach and window-timeout
			int trials = 0;
			int highestAck = -1;
			LinkedBlockingQueue<Boolean> done = new LinkedBlockingQueue<Boolean>(); 
			
			while (trials < 10) {
				Timer windowTimer = new Timer();
				windowTimer.schedule(new WindowTimerTask("some task", done), 500L);
				
				for (int i = (highestAck + 1); i <= lastIndex; i++) {
					try {
						// System.out.println("Ringo data packet: " +window[i]);
						this.sendQueue.put(window[i]);
					} catch (Exception e) {
						// System.out.println("do something");
					}
				}
				
				RingoPacket ack = takeSpecific(this.recvQueue, PacketType.DATA_ACK, window[0].getDestIP(), window[0].getDestPort());
				
				while (done.isEmpty()) {
					// System.out.println("highestAck: " +highestAck);
					if (ack != null && ack.getSequenceNumber() > highestAck + window[0].getSequenceNumber()) {
						System.out.println("highest ack: "+highestAck);
						System.out.println("THIS IS THE ACK YOU'VE BEEN WAITING FOR AHAHAHAHAHAHAH ack " +ack.getSequenceNumber());
						highestAck = ack.getSequenceNumber() - window[0].getSequenceNumber();
						// System.out.println("highest ack: " +highestAck);
						if (highestAck > lastIndex) {
							// System.out.println("reached here");
							break;
						}
					}
					
					ack = takeSpecific(this.recvQueue, PacketType.DATA_ACK, window[0].getDestIP(), window[0].getDestPort());
				}
				
				if (highestAck > lastIndex) {
					break;
				}
				
				try {
					// System.out.println("Timer is complete: " +done.take());
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				trials++;
			}
			
			// System.out.println("this is where I got");
			return highestAck;
		}
		
		private class WindowTimerTask extends TimerTask {
			private String name;
			private LinkedBlockingQueue<Boolean> done;
			
		    public WindowTimerTask(String name, LinkedBlockingQueue<Boolean> done) {
		        this.name = name;
		        this.done = done;
		    }
		    
		    public void run() {
		        /*System.out.println(Thread.currentThread() + " executing " +
		                           this.name + " [" +
		                           new Date() + "]");*/
		        done.add(true);
		    }
		}
	}
}

	/**
	// might need a DATA_END packet
	private void sendFile(String filepath) {
		File file = new File(filepath);
		Hashtable<String, Boolean> windowAckList = new Hashtable<String, Boolean>();
		
		try {
			FileInputStream fileReader;
			fileReader = new FileInputStream(file);
			
			int seqNumber = 0;
			RingoPacket [] window = new RingoPacket[10];
			byte [] data = new byte[10000];
			
			while (fileReader.read(data) != -1) {
				RingoPacket toSend = createSendPacket(data, seqNumber, file.length()/10000);
				window[seqNumber%50] = toSend;
				if (seqNumber%50 == 49) {
					if (!transmitWindow(window)) {
						// if (!this.keepAlive.isAlive(window[0].getDestIP(), window[0].getDestPort())) {
						if (true) {
							fileReader = new FileInputStream(file);
							seqNumber = seqNumber - seqNumber%50;
						}
					}
				}
				seqNumber++;
			}
		} catch (Exception e) {
			
		}
	}
	
	private boolean transmitWindow(RingoPacket [] window) {
		// implement go-back-N with cumulative ACK approach and window-timeout
		int trials = 0;
		int highestAck = window[0].getSequenceNumber() - 1;
		final boolean [] done = new boolean[1];
		
		while (highestAck != window[window.length - 1].getSequenceNumber() && trials < 3) {
			Timer windowTimer = new Timer();
			windowTimer.schedule(new WindowTimerTask("some task", done), 3000L);
			
			for (int i = (highestAck + 1 - window[0].getSequenceNumber()); i < window.length; i++) {
				try {
					this.sendQueue.put(window[i]);
				} catch (Exception e) {
					System.out.println("do something");
				}
			}
			
			RingoPacket ack = this.takeSpecific(this.recvQueue, PacketType.DATA_ACK, window[0].getDestIP(), window[0].getDestPort());
			
			while (!done[0]) {
				if (ack.getSequenceNumber() > highestAck) {
					highestAck = ack.getSequenceNumber();
				}
				
				ack = this.takeSpecific(this.recvQueue, PacketType.DATA_ACK, window[0].getDestIP(), window[0].getDestPort());
			}
			
			trials++;
		}
		
		if (highestAck != window[window.length].getSequenceNumber()) {
			return false;
		} else {
			return true;
		}
	}
	
	private RingoPacket createSendPacket(byte [] data, int seqNumber, long seqLength) {
		// need to define a keep-alive method that returns the next host name and port
		// RingoPacket toSend = new RingoPacket(this.localName, this.localPort, this.keepAlive.nextHost, this.keepAlive.nextPort, 0, seqNumber, PacketType.DATA, this.role, this.ringSize);
		ArrayList<String> route = this.ringRoute;
		boolean takeNext = false;
		String next = "";
		for (int i = 0; i < route.size(); i++) {
			if (takeNext) {
				next = route.get(i);
			}
			
			if (route.get(i) == this.localName + ":" + this.localPort) {
				takeNext = true;
			}
		}
		
		RingoPacket toSend;
		
		if (data == null) {
			toSend = new RingoPacket(this.localName, this.localPort, next.substring(0, next.indexOf(":")), Integer.parseInt(next.substring(next.indexOf(":"), next.length())), seqLength, seqNumber, PacketType.DATA, this.role, this.ringSize);
		} else {
			toSend = new RingoPacket(this.localName, this.localPort, next.substring(0, next.indexOf(":")), Integer.parseInt(next.substring(next.indexOf(":"), next.length())), seqLength, seqNumber, PacketType.DATA, this.role, this.ringSize);
			toSend.setPayload(data);
		}
		
		return toSend;
	}
	
	private class WindowTimerTask extends TimerTask {
		private String name;
		private final boolean [] done;
		
	    public WindowTimerTask(String name, final boolean [] done) {
	        this.name = name;
	        this.done = done;
	    }
	    
	    public void run() {
	        System.out.println(Thread.currentThread() + " executing " +
	                           this.name + " [" +
	                           new Date() + "]");
	        this.done[0] = true;
	    }
	}
}**/

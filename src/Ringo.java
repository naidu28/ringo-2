import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Base64;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.lang.Thread;

import java.io.IOException;

public class Ringo implements Runnable {
	DatagramSocket socket;
	private final Role role;
	private String localName;
	private final int localPort;
	private String pocName;
	private final int pocPort;
	private final int ringSize;

	private Hashtable<String, Integer> lsa;
	private Hashtable<String, Integer> rttIndex;
	private Hashtable<Integer, String> indexRtt;
	private long [][] rtt;
	private LinkedBlockingQueue<RingoPacket> recvQueue;
	private LinkedBlockingQueue<RingoPacket> sendQueue;

	public Ringo(Role role, int localPort, String pocName, int pocPort, int ringSize) {
		this.role = role;
		this.localName = "";
		try {
			this.localName = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.localPort = localPort;
		this.pocName = pocName;
		try {
			this.pocName = InetAddress.getByName(pocName).getHostAddress();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.pocPort = pocPort;
		this.ringSize = ringSize;
		// hostname, port
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

		/*try {
			this.lsa.put(InetAddress.getLocalHost().getHostName()+":"+this.localPort, 1);
		} catch (Exception e) { }*/

		System.out.println("poc port: " +this.pocPort);

		try {
			this.socket = new DatagramSocket(this.localPort);
			System.out.println(this.socket);
		} catch(SocketException e) {
			// handle this later
		}
	}

	public Ringo(Role role, int localPort, int ringSize) {
		this.role = role;
		this.localPort = localPort;
		this.ringSize = ringSize;

		this.pocName = "";
		this.pocPort = -1;
		try {
			this.socket = new DatagramSocket(this.localPort);
		} catch(SocketException e) {
			// handle this
		}
	}

	public byte [] serialize(Object obj) {
		ByteArrayOutputStream bo = new ByteArrayOutputStream();
		byte [] serializedObj = null;

		try {
			ObjectOutputStream so = new ObjectOutputStream(bo);
			so.writeObject(obj);
			so.flush();
			serializedObj = bo.toByteArray();
		} catch (IOException e) {
			// handle later
		}

		return serializedObj;
	}

	public RingoPacket deserialize(byte [] b) {
		RingoPacket obj = null;
		for (int i = 1180; i < 1190; i++) {
			// System.out.println(b[i]);
		}
		ByteArrayInputStream bi = new ByteArrayInputStream(b);

		try {
			ObjectInputStream si = new ObjectInputStream(bi);
			obj = (RingoPacket) si.readObject();
		} catch (Exception e) {
			// handle later
			System.out.println("this exception: " +e);
		}

		return obj;
	}

	public void run() {
		System.out.println("hey");
		LinkedBlockingQueue<RingoPacket> recvQueue = this.recvQueue;
		LinkedBlockingQueue<RingoPacket> sendQueue = this.sendQueue;

		Thread netIn = new Thread(new ReceiverThread(recvQueue));
		Thread netOut = new Thread(new SenderThread(sendQueue));
		netIn.start();
		netOut.start();

		peerDiscovery(recvQueue, sendQueue);
		try {
			Thread.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
		flushType(recvQueue, PacketType.LSA);
		flushType(recvQueue, PacketType.LSA_COMPLETE);
		System.out.println("I think I made it here");
		rttVectorGeneration(recvQueue, sendQueue);
		flushType(recvQueue, PacketType.PING_REQ);
		flushType(recvQueue, PacketType.PING_RES);
		flushType(recvQueue, PacketType.PING_COMPLETE);
		System.out.println(this.rttIndex);
		// rttConvergence(recvQueue, sendQueue);

		while(true) {
			for (int i = 0; i < this.rtt.length; i++) {
				for (int j = 0; j < this.rtt.length; j++) {
					System.out.print(" " + this.rtt[i][j]);
				}
				System.out.println();
			}
		}

		// System.out.println(this.lsa);
	}

	private void peerDiscovery(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		Hashtable<String, Boolean> converged = new Hashtable<String, Boolean>();
		this.lsa.put(this.localName+":"+this.localPort, 1);
		if (this.lsa.size() < ringSize) {
			converged.put(this.localName+":"+this.localPort, false);
		} else {
			System.out.println("is some code touching this spot");
			converged.put(this.localName+":"+this.localPort, true);
		}

		if (this.pocName != "0" && this.pocPort != 0) {
			this.lsa.put(this.pocName+":"+this.pocPort, 1);
			converged.put(this.pocName+":"+this.pocPort, false);

			RingoPacket packet = new RingoPacket(this.localName, this.localPort, this.pocName, this.pocPort, 0, 0, PacketType.LSA, this.role, this.ringSize);
			packet.setLsa(this.lsa);
			sendQueue.add(packet);
		}

		while (!isLsaConverged(converged)) {
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

					Iterator iter = this.lsa.keySet().iterator();

					while (iter.hasNext()) {
						String key = (String) iter.next();
						//System.out.println("stuff: " +request.getDestIP());
						RingoPacket response = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.LSA, this.role, this.ringSize);
						response.setLsa(this.lsa);
						sendQueue.add(response);
					}

					System.out.println("size of recvqueue: " +recvQueue.size());
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

		System.out.println("3");

		System.out.println("who da fuck are you");
	}

	private boolean isLsaConverged(Hashtable<String, Boolean> converged) {
		if (this.lsa.size() >= ringSize) {
			System.out.println("size of my lsa: " +this.lsa.size());
			System.out.println("my lsa: " +this.lsa);
			converged.put(this.localName+":"+this.localPort, true);
		} else {
			return false;
		}

		Iterator iter = this.lsa.keySet().iterator();
		System.out.println(converged);

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
			while(!isRttVectorConverged()) {
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

						System.out.println("rtt index: " +this.rttIndex);
						System.out.println(recvQueue);

						if (responseIn != null && !this.rttIndex.containsKey(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
							System.out.println("we're adding this new thing");
							System.out.println("rtt index: " +this.rttIndex);
							assignRtt(responseIn, n, responseIn.getStopTime() - responseIn.getStartTime());
							n++;
						}
						//System.out.println(sendQueue);
					}
				}
			}
		//}
			converged.add(this.localName+":"+this.localPort);
			
			/*while (!converged.containsAll(this.lsa.keySet())) {
				Iterator iter = this.lsa.keySet().iterator();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					if (!converged.contains(key) && !key.equals(localkey)) {
						RingoPacket request = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING_COMPLETE, this.role, this.ringSize);
						sendQueue.add(request);
					}
				}
				
				try {
					Thread.sleep(500);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				RingoPacket response = takeType(recvQueue, PacketType.PING_COMPLETE);
				if (response != null && !converged.contains(response.getSourceIP()+":"+response.getSourcePort())) {
					converged.add(response.getSourceIP()+":"+response.getSourcePort());
				}
			}*/
			// receive one response
			/*boolean receivedReq = false;
			boolean receivedRes = false;

			RingoPacket responseIn = null;

			while (responseIn == null) {
				responseIn = takeType(recvQueue, PacketType.PING_RES);
				while (responseIn != null && this.rttIndex.containsKey(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
					break;
				}
			}

			System.out.println("general response location " + responseIn.toString() + "\n");
			if (!this.rttIndex.containsKey(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
				// System.out.println("new response location " + responseIn.toString());
				// System.out.println("ping time: " +(responseIn.getStopTime() - responseIn.getStartTime()));
				// System.out.println();
				assignRtt(responseIn, n, responseIn.getStopTime() - responseIn.getStartTime());
				n++;
			}*/
				/*RingoPacket requestIn = takeType(recvQueue, PacketType.PING_REQ);

				if (requestIn != null) {
					if (!rttIndex.contains(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
						assignRtt(responseIn, n, responseIn.getStopTime() - responseIn.getStartTime());
						System.out.println("received: " +rttIndex);
						receivedRe = true;
					}
				}*/

			// receive one request
			// respond to one request

		/*while (i < ringSize - 1) {
			boolean isReceived = false;

			while(!isReceived) {
				RingoPacket responseIn = takeType(recvQueue, PacketType.PING_RES);

				// System.out.println("response in source: " + responseIn.getSourceIP());
				if (responseIn != null) {
					if (!received.contains(responseIn.getSourceIP()+":"+responseIn.getSourcePort())) {
						assignRtt(responseIn, n, responseIn.getStopTime() - responseIn.getStartTime());
						received.add(responseIn.getSourceIP()+":"+responseIn.getSourcePort());
						System.out.println("received: " +received);
						i++;
						isReceived = true;
					}
				}

				Iterator iter = this.lsa.keySet().iterator();

				while (iter.hasNext()) {
					String key = (String) iter.next();
					if (!received.contains(key)) {
						if (!key.equals(localkey)) {
							RingoPacket requestOut = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING_REQ, this.role);
							sendQueue.add(requestOut);
						}
					}
				}
			}
		}*/
			/*
			while () {
				String key = (String) iter.next();

				if (!key.equals(localkey)) {
					RingoPacket requestOut = new RingoPacket(this.localName, this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING_REQ, this.role);
					sendQueue.add(requestOut);
					RingoPacket responseIn = null;
					RingoPacket requestIn = null;
					while (responseIn == null || requestIn == null) {
						responseIn = takeType(recvQueue, PacketType.PING_RES);
						requestIn = takeType(recvQueue, PacketType.PING_REQ);
					}

					//System.out.println("start time: " +requestOut.getStartTime());
					//System.out.println("stop time: " +responseIn.getStopTime());
					//System.out.println("time difference: " + (responseIn.getStopTime() - requestOut.getStartTime()));
					assignRtt(responseIn, n, responseIn.getStopTime() - requestOut.getStartTime());
					//System.out.println("6");
					n++;

					RingoPacket responseOut = new RingoPacket(this.localName, this.localPort, requestIn.getSourceIP(), requestIn.getSourcePort(), 0, 0, PacketType.PING_RES, this.role);
					sendQueue.add(responseOut);
				}
			}*/
	}


	private boolean isRttVectorConverged() {
		for (int j = 0; j < this.rtt[0].length; j++) {
			//System.out.println("value in rtt at: " +j+ " is " +this.rtt[0][j]);
			if (this.rtt[0][j] == -1) {
				// not converged for these hosts
				return false;
			}
		}

		return true;
	}

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

	private void rttConvergence(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		//Hashtable<String, Boolean> encountered = new Hashtable<String, Boolean>();
		while (!isRttConverged()) {
			Iterator iter = this.lsa.keySet().iterator();

			while (iter.hasNext()) {
				String key = (String) iter.next();

				if (!key.equals(this.localName+":"+this.localPort)) {
					RingoPacket requestOut = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.RTT_REQ, Ringo.this.role, this.ringSize);
					sendQueue.add(requestOut);

					RingoPacket responseIn = null;
					responseIn = takeType(recvQueue, PacketType.RTT_RES);

					//System.out.println("rtt index: " +this.rttIndex);
					//System.out.println(recvQueue);
					try {
						Thread.sleep(200);
					} catch (Exception e) {
						e.printStackTrace();
					}

					if (responseIn != null) {
						addRttVectorToMatrix(responseIn);
					}
				}
			}
		}
	}

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
			System.out.println("this packet's key index: " +this.rttIndex.get(packetKey));
			System.out.println("other packet's key index: " +otherKey);
			this.rtt[this.rttIndex.get(packetKey)][this.rttIndex.get(otherKey)] = rttVal;
		}
	}
	
	private void flushType(LinkedBlockingQueue<RingoPacket> queue, PacketType type) {
		Iterator iter = queue.iterator();

		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == type) {
				iter.remove();
			}
		}
	}

	private RingoPacket takeType(LinkedBlockingQueue<RingoPacket> queue, PacketType type) {
		Iterator iter = queue.iterator();

		while (iter.hasNext()) {
			RingoPacket packet = (RingoPacket) iter.next();
			if (packet.getType() == type) {
				iter.remove();
			}
			return packet;
		}

		return null;
	}

	private class ReceiverThread implements Runnable {
		LinkedBlockingQueue<RingoPacket> packetQueue;

		private ReceiverThread(LinkedBlockingQueue<RingoPacket> packetQueue) {
			this.packetQueue = packetQueue;
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
			byte[] data = new byte[10000];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			Ringo.this.socket.receive(packet);
			return packet;
		}

		private void deserializeAndEnqueue(byte [] data) {
			RingoPacket packet = deserialize(data);
			packet.setStopTime(System.currentTimeMillis());
			if (packet != null) {
				replaceDuplicates(packet);
				if (packet.getType() == PacketType.PING_REQ) {
					RingoPacket responseOut = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, packet.getSourceIP(), packet.getSourcePort(), 0, 0, PacketType.PING_RES, Ringo.this.role, Ringo.this.ringSize);
					responseOut.setStartTime(packet.getStartTime()); // to generate RTT we have to use other packet's start time
					//System.out.println("this is the response I'm returning back boys " +responseOut);
					Ringo.this.sendQueue.add(responseOut);
				} else if (packet.getType() == PacketType.RTT_REQ){
					RingoPacket responseOut = new RingoPacket(Ringo.this.localName, Ringo.this.localPort, packet.getSourceIP(), packet.getSourcePort(), 0, 0, PacketType.PING_RES, Ringo.this.role, Ringo.this.ringSize);
					responseOut.setRtt(Ringo.this.rtt);
					responseOut.setRttIndex(Ringo.this.rttIndex);
					responseOut.setIndexRtt(Ringo.this.indexRtt);
					Ringo.this.sendQueue.add(responseOut);
				} else {
					this.packetQueue.add(packet);
				}
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
					replaceDuplicates(packet);
					packet.setStartTime(System.currentTimeMillis());
					byte [] data = serialize(packet);
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
}

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
import java.util.Iterator;
import java.lang.Thread;

import java.io.IOException;

public class Ringo implements Runnable {
	DatagramSocket socket;
	private final Role role;
	private final int localPort;
	private final String pocName;
	private final int pocPort;
	private final int ringSize;

	private Hashtable<String, Integer> lsa;
	private Hashtable<String, Integer> rttIndex;
	private Hashtable<Integer, String> indexRtt;
	private long [][] rtt;

	public Ringo(Role role, int localPort, String pocName, int pocPort, int ringSize) {
		this.role = role;
		this.localPort = localPort;
		this.pocName = pocName;
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
		LinkedBlockingQueue<RingoPacket> recvQueue = new LinkedBlockingQueue<RingoPacket>();
		LinkedBlockingQueue<RingoPacket> sendQueue = new LinkedBlockingQueue<RingoPacket>();

		Thread netIn = new Thread(new ReceiverThread(recvQueue));
		Thread netOut = new Thread(new SenderThread(sendQueue));
		netIn.start();
		netOut.start();

		peerDiscovery(recvQueue, sendQueue);
		flushType(recvQueue, PacketType.LSA);
		System.out.println("I think I made it here");
		rttMatrixGeneration(recvQueue, sendQueue);
		flushType(recvQueue, PacketType.RTT);
		System.out.println("anticos");
		// rttConvergence(recvQueue, sendQueue);

		/*while(true) {
			for (int i = 0; i < this.rtt.length; i++) {
				for (int j = 0; j < this.rtt.length; j++) {
					System.out.print(" " + this.rtt[i][j]);
				}
				System.out.println();
			}
		}*/
		
		System.out.println(this.lsa);
	}

	private void peerDiscovery(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		if (this.pocName != "0" && this.pocPort != 0)
			this.lsa.put(this.pocName+":"+this.pocPort, 1);
		try {
			this.lsa.put(InetAddress.getLocalHost().getHostAddress()+":"+this.localPort, 1);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Hashtable<String, Boolean> converged = new Hashtable<String, Boolean>();
		
		// initial broadcast to all network nodes
		Iterator iter = this.lsa.keySet().iterator();

		try {
			InetAddress src = InetAddress.getLocalHost();
			
			if (this.lsa.size() < ringSize)
				converged.put(src.getHostAddress()+":"+this.localPort, false);
			else
				converged.put(src.getHostAddress()+":"+this.localPort, true);
			
			while (iter.hasNext()) {
				String key = (String) iter.next();
				RingoPacket packet = new RingoPacket(src.getHostAddress(), this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.LSA, this.role);
				packet.setLsa(this.lsa);
				sendQueue.add(packet);
			}
		} catch (Exception e) {
			System.out.println(e);
		}
		
		while (converged.containsValue(false)) {
			// listen for responses from all nodes
			// and respond with corresponding LSA vectors
			try {
				RingoPacket request = recvQueue.take();
				this.lsa.put(request.getSourceIP()+":"+request.getSourcePort(), 1);
				this.lsa.putAll(request.getLsa());
				if (request.getLsa().size() < ringSize)
					converged.put(request.getSourceIP()+":"+request.getSourcePort(), false);
				else
					converged.put(request.getSourceIP()+":"+request.getSourcePort(), true);
					
				
				RingoPacket response = new RingoPacket(request.getDestIP(), request.getDestPort(), request.getSourceIP(), request.getSourcePort(), 0, 0, PacketType.LSA, this.role);
				response.setLsa(this.lsa);
				sendQueue.add(response);
			} catch (Exception e) {
				// handle later
				System.out.println("this exception is e: " +e);
			}
		}
	}

	private void rttMatrixGeneration(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		Iterator iter = this.lsa.keySet().iterator();

		try {
			InetAddress src = InetAddress.getLocalHost();
			while (iter.hasNext()) {
				String key = (String) iter.next();
				if (key.substring(0, key.indexOf(":")) != src.getHostAddress()) {
					RingoPacket packet = new RingoPacket(src.getHostAddress(), this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.PING, this.role);
					sendQueue.add(packet);
					takeType(recvQueue);
				}
			}
		} catch (Exception e) {
			// handle this later - unknown host error
			System.out.println(e);
		}

		try {
			InetAddress src = InetAddress.getLocalHost();
			String localkey = src.getHostAddress()+":"+this.localPort;
			this.rttIndex.put(localkey, 0);
			this.indexRtt.put(0, localkey);
			this.rtt[this.rttIndex.get(localkey)][this.rttIndex.get(localkey)] = 0;
			int n = 1;

			while (this.rttIndex.keySet().size() != ringSize) {
				while (!recvQueue.isEmpty()) {
					try {
						RingoPacket packet = recvQueue.take();
						if (!this.rttIndex.containsKey(packet.getSourceIP()+":"+packet.getSourcePort())) {

							this.rttIndex.put(packet.getSourceIP()+":"+packet.getSourcePort(), n);
							this.indexRtt.put(n, packet.getSourceIP()+":"+packet.getSourcePort());
							n++;

							this.rtt[rttIndex.get(packet.getSourceIP()+":"+packet.getSourcePort())][rttIndex.get(localkey)] = packet.getStopTime() - packet.getStartTime();
							this.rtt[rttIndex.get(localkey)][rttIndex.get(packet.getSourceIP()+":"+packet.getSourcePort())] = packet.getStopTime() - packet.getStartTime();
						}
					} catch (Exception e) {
						// handle later
						System.out.println("this exception is e: " +e);
					}
				}
			}


		} catch (Exception e) {

		}

		// System.out.println(this.rtt);
	}

	private void rttConvergence(LinkedBlockingQueue<RingoPacket> recvQueue, LinkedBlockingQueue<RingoPacket> sendQueue) {
		int n = 0;
		Hashtable<String, Boolean> encountered = new Hashtable<String, Boolean>();
		Iterator iterator = this.lsa.keySet().iterator();
		
		try {
			InetAddress src = InetAddress.getLocalHost();
			String localkey = src.getHostAddress()+":"+this.localPort;
			
			while (iterator.hasNext()) {
				String key = (String) iterator.next();
				if (key.equals(localkey)) {
					encountered.put(key, true);
				} else {
					encountered.put(key, false);
				}
			}
			
			while (encountered.containsValue(false)) {
				// listen for responses from all nodes
				while (!recvQueue.isEmpty()) {
					try {
						RingoPacket packet = recvQueue.take();
						System.out.println(packet.toString());
						if (encountered.get(packet.getSourceIP()+":"+packet.getSourcePort()))
							continue;
						else
							encountered.put(packet.getSourceIP()+":"+packet.getSourcePort(), true);
						
						long [][] packetRtt = packet.getRtt();
						if (packetRtt == null)
							continue;
						else
							n++;
						
						Hashtable<String, Integer> packetRttIndex = packet.getRttIndex();
						Hashtable<Integer, String> packetIndexRtt = packet.getIndexRtt();
						// Object [] keys = packetRttIndex.keySet().toArray();
	
						for (int i = 0; i < packetRtt.length; i++) {
							for (int j = 0; j < packetRtt.length; j++) {
								if (packetRtt[i][j] != -1) {
									String key1 = packetIndexRtt.get(i);
									String key2 = packetIndexRtt.get(j);
									System.out.println("another number " +packetRtt[i][j]);
									System.out.println("key1 " +key1+ " " +this.rttIndex.get(key1)+ " key2 " +key2+ " " +this.rttIndex.get(key2));
									this.rtt[this.rttIndex.get(key1)][this.rttIndex.get(key2)] = packetRtt[i][j];
									this.rtt[this.rttIndex.get(key2)][this.rttIndex.get(key1)] = packetRtt[i][j];
								}
							}
						}
					} catch (Exception e) {
						// handle later
						System.out.println("this exception is ello: ");
						e.printStackTrace();
					}
				}
	
				/*try {
					Thread.sleep(500);
				} catch (Exception e) {
					System.out.println(e);
				}*/
	
				// broadcast to all nodes
				Iterator iter = this.lsa.keySet().iterator();
	
				try {
					while (iter.hasNext()) {
						String key = (String) iter.next();
						System.out.println("location: " +key);
						RingoPacket packet = new RingoPacket(src.getHostAddress(), this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.RTT, this.role);
						packet.setRtt(this.rtt);
						packet.setRttIndex(this.rttIndex);
						packet.setIndexRtt(this.indexRtt);
						sendQueue.add(packet);
						Thread.sleep(50);
					}
				} catch (Exception e) {
					// handle this later - unknown host error
					System.out.println(e);
				}
	
	
				// System.out.println(this.lsa);
	
				//if (n == ringSize)
					//return;
			}
			
			while (!recvQueue.isEmpty()) {
				try {
					RingoPacket packet = recvQueue.take();
					if (encountered.get(packet.getSourceIP()+":"+packet.getSourcePort()))
						continue;
					else
						encountered.put(packet.getSourceIP()+":"+packet.getSourcePort(), true);
					
					long [][] packetRtt = packet.getRtt();
					if (packetRtt == null)
						continue;
					else
						n++;
					
					Hashtable<String, Integer> packetRttIndex = packet.getRttIndex();
					Hashtable<Integer, String> packetIndexRtt = packet.getIndexRtt();
					// Object [] keys = packetRttIndex.keySet().toArray();

					for (int i = 0; i < packetRtt.length; i++) {
						for (int j = 0; j < packetRtt.length; j++) {
							if (packetRtt[i][j] != -1) {
								String key1 = packetIndexRtt.get(i);
								String key2 = packetIndexRtt.get(j);
								System.out.println("another number " +packetRtt[i][j]);
								System.out.println("key1 " +key1+ " " +this.rttIndex.get(key1)+ " key2 " +key2+ " " +this.rttIndex.get(key2));
								this.rtt[this.rttIndex.get(key1)][this.rttIndex.get(key2)] = packetRtt[i][j];
								this.rtt[this.rttIndex.get(key2)][this.rttIndex.get(key1)] = packetRtt[i][j];
							}
						}
					}
				} catch (Exception e) {
					// handle later
					System.out.println("this exception is ello: ");
					e.printStackTrace();
				}
			}

			/*try {
				Thread.sleep(500);
			} catch (Exception e) {
				System.out.println(e);
			}*/

			// broadcast to all nodes
			Iterator iter = this.lsa.keySet().iterator();

			try {
				while (iter.hasNext()) {
					String key = (String) iter.next();
					System.out.println("location: " +key);
					RingoPacket packet = new RingoPacket(src.getHostAddress(), this.localPort, key.substring(0, key.indexOf(":")), Integer.parseInt(key.substring(key.indexOf(":") + 1)), 0, 0, PacketType.RTT, this.role);
					packet.setRtt(this.rtt);
					packet.setRttIndex(this.rttIndex);
					packet.setIndexRtt(this.indexRtt);
					sendQueue.add(packet);
					Thread.sleep(50);
				}
			} catch (Exception e) {
				// handle this later - unknown host error
				System.out.println(e);
			}
		} catch (Exception e) {
			e.printStackTrace();
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
				this.packetQueue.add(packet);
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
	}
}

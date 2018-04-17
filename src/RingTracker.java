import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

public class RingTracker {
	
	private ArrayList<HostInformation> hosts;
	private Hashtable<Pair<HostInformation, HostInformation>, Long> rtt;
	private int n;
	private ArrayList<HostInformation> ring;
	
	public RingTracker(String me, long[][] rtt, Hashtable<Integer, String> indexRTT) {
		hosts = new ArrayList<HostInformation>();
		this.rtt = new Hashtable<Pair<HostInformation, HostInformation>, Long>();
		n = rtt[0].length;
		this.ring = new ArrayList<>();
		
		for (Integer i : indexRTT.keySet()) {
			String host = indexRTT.get(i);
			String ip = host.split(":")[0];
			int port = Integer.parseInt(host.split(":")[1]);
			boolean active = true; // each host is assumed to be active at the start
			boolean local = host.equalsIgnoreCase(me);
			// public HostInformation(boolean active, boolean local, String host, int port)
			hosts.add(new HostInformation(active, local, ip, port));
		}
		
		// build up RTT information
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {				
				Long rttdelay = rtt[i][j];
				HostInformation a = getInfoByHoststring(indexRTT.get(i));
				HostInformation b = getInfoByHoststring(indexRTT.get(j));
				
				System.out.println(a.toString() + "\t" + b.toString() + "\t" + rttdelay);
				Pair<HostInformation, HostInformation> pair = new Pair<>(a, b);
				this.rtt.put(pair, rttdelay);
			}
		}
		
		// build Ring
		makeRingFromHosts();
	}
	
	public void makeRingFromHosts() {
		synchronized (hosts) {
			generateOptimalRing(hosts);
		}
	}
	
	public void generateOptimalRing(ArrayList<HostInformation> activeHosts) {
		// generate every possible path
		List<Pair<Long, ArrayList<HostInformation>>> costs = new ArrayList<>();
		permute(activeHosts, 0, costs);
		for (Pair<Long, ArrayList<HostInformation>> path : costs) {
			System.out.println(path.getA() + "\t" + Arrays.toString(path.getB().toArray()));
		}
		
		Pair<Long, ArrayList<HostInformation>> lowestCost = null;
		for (Pair<Long, ArrayList<HostInformation>> path : costs) {
			if (lowestCost == null) {
				lowestCost = path;
			} else if (path.getA().longValue() < lowestCost.getA().longValue()) {
				lowestCost = path;
			}
		}
		
		System.out.println("OPTIMAL RING COST:\t" + lowestCost.getA().longValue());
		
		synchronized (ring) {
			ring = lowestCost.getB();
		}
	}
	
	public HostInformation getNextRingo() {
		synchronized (ring) {
			int i = 0;
			for (; i < ring.size(); i++) {
				if (ring.get(i).isLocal()) {
					return ring.get((i + 1) % ring.size());
				}
			}
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	public synchronized ArrayList<HostInformation> getHosts() {
		return (ArrayList<HostInformation>) hosts.clone();
	}
	
	public synchronized void setHosts(ArrayList<HostInformation> hosts) {
		this.hosts = hosts;
	}
	
	public ArrayList<String> getRoute() {
		synchronized (ring) {
			return ring
					.stream()
					.map(r -> r.hostString())
					.collect(Collectors.toCollection(ArrayList::new));
		}
	}
	
	public void updateHost(HostInformation host, boolean active) {
		if (host == null) 
			throw new IllegalArgumentException("Cannot call updateHost with null host");
		synchronized (hosts) {
			if (!hosts.contains(host)) {
				throw new IllegalArgumentException(host.hostString() + " is not in hosts!");
			}
			
			int idx = hosts.indexOf(host);
			HostInformation updated = hosts.get(idx);
			updated.setActive(active);
			hosts.set(idx, updated);
		}
	}
	
	public boolean isOnline(String hostString) {
		if (hostString == null || hostString.isEmpty())
			return false;
		
		String host = hostString.split(":")[0];
		int port = Integer.parseInt(hostString.split(":")[1]);
		synchronized (hosts) {
			for (HostInformation ringo : hosts) {
				if (ringo.getHost().equals(host) && ringo.getPort() == port)
					return true;
			}
			return false;
		}
	}
	
	private long findCost(List<HostInformation> path) {
		long cost = 0;
		// convert HostInformations to list of pairs
		List<Pair<HostInformation, HostInformation>> pairs = new ArrayList<>();
		int i = 0;
		int j = 1;
		
		while (i < path.size()) {
			HostInformation a = path.get(i % path.size());
			HostInformation b = path.get(j % path.size());
			Pair<HostInformation, HostInformation> pair = new Pair<>(a, b);
			pairs.add(pair);
			i++;
			j = (j + 1) % path.size();
		}
		
		for (Pair<HostInformation, HostInformation> pair : pairs) {
			cost += rtt.get(pair).longValue();
		}
		
		return cost;
	}
	
	private void permute(ArrayList<HostInformation> curr, int k, List<Pair<Long, ArrayList<HostInformation>>> dst) {
		for (int i = k; i < curr.size(); i++) {
			Collections.swap(curr,  i,  k);
			permute(curr, k + 1, dst);
			Collections.swap(curr,  k,  i);
		}
		
		if (k == curr.size() - 1) {
			long cost = findCost(curr);
			@SuppressWarnings("unchecked")
			Pair<Long, ArrayList<HostInformation>> costpair = 
				new Pair<Long, ArrayList<HostInformation>>(new Long(cost), (ArrayList<HostInformation>) curr.clone());
			dst.add(costpair);
		}
	}
	
	/**
	 * Returns the status of the requested host
	 * 
	 * @param host A string with the concatenated hostname:port syntax
	 * @return true if the node is currently considered active; false otherwise
	 */
	public boolean isActiveByHoststring(String host) throws IllegalArgumentException {
		HostInformation requested = getInfoByHoststring(host);
		return requested.isActive();
	}
	
	private HostInformation getInfoByHoststring(String key) {
		String host = key.split(":")[0];
		int port = Integer.parseInt(key.split(":")[1]);
		for (HostInformation h : hosts) {
			if (h.getHost().equalsIgnoreCase(host) && h.getPort() == port) {
				return h;
			}
		}
		
		throw new IllegalArgumentException("Invalid key used: Given Host doesn't exist");
	}

}

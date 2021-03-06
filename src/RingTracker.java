import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class RingTracker {
	
	private ArrayList<HostInformation> hosts;
	private Hashtable<Pair<HostInformation, HostInformation>, Long> rtt;
	private int n;
	private ArrayList<HostInformation> ring;
	
	/**
	 * Tracks the Ring structure, Ringo statuses, and RTTs for all Ringos in the Ring.
	 * @param me The host:port for THIS Ringo. Equivalent to Ringo.localname:Ringo.localport
	 * @param rtt rtt array from Ringo
	 * @param indexRTT 
	 */
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
			hosts.add(new HostInformation(HostState.UP, local, ip, port));
		}
		
		// build up RTT information
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {				
				Long rttdelay = rtt[i][j];
				HostInformation a = getInfoByHoststring(indexRTT.get(i));
				HostInformation b = getInfoByHoststring(indexRTT.get(j));
				
				Pair<HostInformation, HostInformation> pair = new Pair<>(a, b);
				this.rtt.put(pair, rttdelay);
			}
		}
		
		// build Ring
		makeRingFromHosts();
	}
	
	/**
	 * Takes the entire hosts ArrayList, and generates a Ring from that.
	 * Only do this when you are confident that every host in the hosts list is valid!
	 */
	public void makeRingFromHosts() {
		synchronized (hosts) {
			generateOptimalRing(hosts);
		}
	}
	
	/**
	 * Generate Ring from only the active nodes in the hosts List.
	 * This is a cleaner version of makeRingFromHosts()
	 */
	public void makeRingFromFilteredHosts() {
		ArrayList<HostInformation> clean = new ArrayList<>();
		synchronized (hosts) {
			Iterator<HostInformation> it = hosts.iterator();
			while (it.hasNext()) {
				HostInformation host = it.next();
				if (host.isActive())
					clean.add(host);
			}
			
			generateOptimalRing(clean);
		}
	}
	
	/**
	 * Updates the Ring structure from some list of active hosts
	 * @param activeHosts List of active Ringos to use for Ring generation
	 */
	public void generateOptimalRing(ArrayList<HostInformation> activeHosts) {
		// generate every possible path
		List<Pair<Long, ArrayList<HostInformation>>> costs = new ArrayList<>();
		permute(activeHosts, 0, costs);
		
		Pair<Long, ArrayList<HostInformation>> lowestCost = null;
		for (Pair<Long, ArrayList<HostInformation>> path : costs) {
			if (lowestCost == null) {
				lowestCost = path;
			} else if (path.getA().longValue() < lowestCost.getA().longValue()) {
				lowestCost = path;
			}
		}
		
		 // System.out.println("OPTIMAL RING COST:\t" + lowestCost.getA().longValue());
		
		synchronized (ring) {
			ring = lowestCost.getB();
		}
	}
	
	/**
	 * Fetches the next Ringo in the Ring, if there is one.
	 * @return might return null if there is no valid local Ringo.
	 */
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
	
	/**
	 * Returns a clone of the current Hosts list.
	 * It's important that this is a clone, or you might run into concurrency issues
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public synchronized ArrayList<HostInformation> getHosts() {
		return (ArrayList<HostInformation>) hosts.clone();
	}
	
	/**
	 * Assigns the hosts array from KeepAlive
	 * @param hosts
	 */
	public synchronized void setHosts(ArrayList<HostInformation> hosts) {
		this.hosts = hosts;
	}
	
	/**
	 * Returns the Ring as a list of hostname:port Strings
	 * @return
	 */
	public ArrayList<String> getRoute() {
		synchronized (ring) {
			return ring
					.stream()
					.map(r -> r.hostString())
					.collect(Collectors.toCollection(ArrayList::new));
		}
	}
	
	/**
	 * Updates a single Host in hosts. Not for adding new hosts!
	 * @param host
	 * @param state
	 */
	public void updateHost(HostInformation host, HostState state) {
		if (host == null) 
			throw new IllegalArgumentException("Cannot call updateHost with null host");
		synchronized (hosts) {
			if (!hosts.contains(host)) {
				throw new IllegalArgumentException(host.hostString() + " is not in hosts!");
			}
			
			int idx = hosts.indexOf(host);
			HostInformation updated = hosts.get(idx);
			updated.setState(state);
			hosts.set(idx, updated);
		}
	}
	
	/*
	 * Prints the RTT matrix
	 */
	public String getMatrix() {
		int entrylen = 12;
		String ret = null;
		synchronized (hosts) {
			String[][] matrix = new String[hosts.size() + 1][hosts.size() + 1];
			
			char[] spaces = new char[entrylen];
			Arrays.fill(spaces, ' ');
			matrix[0][0] = new String(spaces);
			
			for (int i = 0; i < hosts.size(); i++) {
				String host = hosts.get(i).hostString();
				matrix[0][i + 1] = host.substring(host.length() - 12, host.length());
			}
			
			for (int i = 0; i < hosts.size(); i++) {
				String host = hosts.get(i).hostString();
				matrix[i + 1][0] = host.substring(host.length() - 12, host.length());
			}
			
			String format = "%" + entrylen + "d";
			
			for (Pair<HostInformation, HostInformation> pair : rtt.keySet()) {
				HostInformation a = pair.getA();
				int idxA = hosts.indexOf(a);
				HostInformation b = pair.getB();
				int idxB = hosts.indexOf(b);
				
				if (idxA < 0 || idxB < 0)
					throw new NullPointerException("Invalid Indices in getMatrix()");
				
				matrix[idxA + 1][idxB + 1] = String.format(format, rtt.get(pair).longValue());
			}
			
			ret = "";
			for (int i = 0; i <= hosts.size(); i++) {
				for (int j = 0; j <= hosts.size(); j++) {
					ret += matrix[i][j] + " ";
				}
				ret += "\n";
			}
		}
		return ret;
	}
	
	/**
	 * Checks to see if some host is online or not
	 * @param hostString
	 * @return
	 */
	public boolean isOnline(String hostString) {
		if (hostString == null || hostString.isEmpty())
			return false;
		
		String host = hostString.split(":")[0];
		int port = Integer.parseInt(hostString.split(":")[1]);
		synchronized (hosts) {
			for (HostInformation ringo : hosts) {
				if (ringo.getHost().equals(host) && ringo.getPort() == port)
					return ringo.isActive();
			}
			return false;
		}
	}
	
	/**
	 * Only call from within a synchronized block! Finds the cost of a TSP path
	 * @param path
	 * @return
	 */
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
	
	/**
	 * Only call from within a synchronized block! Finds all possible TSP paths,
	 * and updates ring with the cheapest one 
	 * 
	 * @param curr List to permute
	 * @param k    Index to start permuting from
	 * @param dst  A list of Cost-Path Pairs. Pair element A is the cost, Pair element B is the path
	 */
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
	
	/**
	 * Only call from a synchronized block! Searches hosts for an element that matches the param
	 * @param key hostname:port String
	 * @return Will return host, or error out
	 * @throws IllegalArgumentException HostInformation with given key not found
	 */
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

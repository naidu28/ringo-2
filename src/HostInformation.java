public class HostInformation {
	
	private boolean active;
	private boolean local;
	private String host;
	private int port;

	public HostInformation(boolean active, boolean local, String host, int port) {
		this.active = active;
		this.local = local;
		this.host = host; 
		this.port = port;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isLocal() {
		return local;
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}
	
	public String hostString() {
		return host + ":" + port;
	}
	
	public String toString() {
		String activeString = (active) ? "active" : "inactive";
		String localString = (local) ? "local" : "remote";
		
		return "["
			+ hostString() + ",\t"
			+ activeString + ",\t"
			+ localString + "]";
	}
	
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null || o.getClass() != this.getClass())
			return false;
		
		@SuppressWarnings("unchecked")
		HostInformation other = (HostInformation) o;
		if (active == other.isActive()
			&& local == other.isLocal()
			&& host.equalsIgnoreCase(other.getHost())
			&& port == other.getPort()) {		
			return true;
		}
		
		return false;
	}
	
	public int hashCode() {
		return toString().hashCode();
	}

}

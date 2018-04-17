public enum HostState {
	UP,
	MAYBE_DOWN,
	DOWN;
		
	public static HostState promote(HostState original) {
		if (original == HostState.DOWN)
			return HostState.MAYBE_DOWN;
		else
			return HostState.UP;
	}
	
	public static HostState demote(HostState original) {
		if (original == HostState.UP)
			return HostState.MAYBE_DOWN;
		else
			return HostState.DOWN;
	}
}

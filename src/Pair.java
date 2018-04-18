
public class Pair<T, U> {

	private T a;
	private U b;
	
	/**
	 * Constructs a pair of two non-null items. Immutable.
	 * @param a
	 * @param b
	 */
	public Pair(T a, U b) {
		this.a = a;
		this.b = b;
		
		if (a == null || b == null)
			throw new IllegalArgumentException("Cannot construct a Pair object with null members");
	}
	
	public String toString() {
		return "Pair(" + a.toString() + ", " + b.toString() + ")";
	}
	
	public T getA() {
		return a;
	}
	
	public U getB() {
		return b;
	}
	
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (o == null || o.getClass() != this.getClass())
			return false;
		
		@SuppressWarnings("unchecked")
		Pair<T, U> other = (Pair<T, U>) o;
		if (   a != null 
			&& b != null
			&& a.equals(other.getA())
			&& b.equals(other.getB())) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * Two prime numbers were chosen for A and B.
	 */
	public int hashCode() {
		return 56 + (a.hashCode() * 17) + (b.hashCode() * 41); 
 	}
}

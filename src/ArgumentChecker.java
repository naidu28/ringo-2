import java.util.function.Function;

/**
 * Checks an argument by first changing the tested value to something checkable, and then running the final test
 * @author andrewray
 *
 * @param <T> Type of argument
 * @param <R> Type of mutated argument
 */
class ArgumentChecker<T, R> {
	
	Function<T, R> mutator;
	
	public ArgumentChecker(Function<T, R> mutator) {
		this.mutator = mutator;
	}
	
	public R check(T arg, String errmsg, Function<R, Boolean> checker) throws IllegalArgumentException {
		try {
			R converted = mutator.apply(arg);
			boolean sane = checker.apply(converted);
			if (!sane)
				throw new IllegalArgumentException();
			return converted;
		} catch (Throwable e) {
			String extraInfo = (e.getMessage() != null) ? ": " + e.getMessage() : "";
			throw new IllegalArgumentException(errmsg + extraInfo);
		}
	}
}

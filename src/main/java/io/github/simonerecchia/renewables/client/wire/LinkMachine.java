package io.github.simonerecchia.renewables.client.wire;

import javax.annotation.Nullable;

/**
 * The two-click state machine of joining two fittings: nothing pending, or one fitting picked and waiting for the other.
 *
 * <ul>
 * <li>nothing pending, click a fitting: it becomes the pending one ({@link Step#STARTED});</li>
 * <li>click the pending fitting again: the pending one is dropped and nothing is sent ({@link Step#CANCELLED});</li>
 * <li>click another fitting: the pending one is dropped at once - whatever the server then says - and the pair is
 *     handed back so the caller can ask for the span ({@link Step#COMPLETED}).  The second fitting does not become
 *     a new pending one: there is no chaining.</li>
 * </ul>
 * "The same fitting" is whatever {@code equals} says, so the caller decides: by block and index, not by position of
 * the click.  A type parameter so the logic is tested with plain strings.
 */
public final class LinkMachine<T> {
	/** What a click did. */
	public enum Step {
		STARTED, CANCELLED, COMPLETED
	}

	/** The outcome of one click: what happened, and the first fitting when it completed a pair. */
	public record Click<T>(Step step, @Nullable T first) {
	}

	@Nullable
	private T pending;

	public Click<T> click(T fitting) {
		if (pending == null) {
			pending = fitting;
			return new Click<>(Step.STARTED, null);
		}

		T first = pending;
		pending = null;
		if (first.equals(fitting)) return new Click<>(Step.CANCELLED, null);

		return new Click<>(Step.COMPLETED, first);
	}

	@Nullable
	public T pending() {
		return pending;
	}

	public boolean isPending() {
		return pending != null;
	}

	public void clear() {
		pending = null;
	}
}

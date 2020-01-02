package com.shouldis.bitset;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Implementation of {@link BitSet} in which all methods capable of modifying
 * the state of bits are performed as atomic operations. The use of atomic
 * operations allows concurrent modification of this {@link ConcurrentBitSet}
 * without any external synchronization at the cost of processing time.
 * <p>
 * All methods have behavior as specified by {@link BitSet}.
 * 
 * @author Aaron Shouldis
 * @see BitSet
 */
public final class ConcurrentBitSet extends BitSet {

	/**
	 * A handle on the long array methods for direct, atomic operations.
	 */
	private static final VarHandle HANDLE = MethodHandles.arrayElementVarHandle(long[].class);

	/**
	 * Creates a {@link ConcurrentBitSet} with the specified <b>size</b>.
	 * 
	 * @param size the number of indices that this {@link BitSet} will hold.
	 * @see BitSet#BitSet(int)
	 */
	public ConcurrentBitSet(final int size) {
		super(size);
	}

	/**
	 * Creates a {@link ConcurrentBitSet} which is a clone of the specified
	 * <b>set</b>.
	 * 
	 * @param set the {@link BitSet} to copy.
	 * @see BitSet#BitSet(BitSet)
	 */
	public ConcurrentBitSet(final BitSet set) {
		super(set);
	}

	@Override
	public boolean add(final int index) {
		final int wordIndex = divideSize(index);
		final long mask = bitMask(index);
		long expected, word;
		do {
			expected = words[wordIndex];
			if ((expected & mask) != 0L) {
				return false;
			}
			word = expected | mask;
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		return true;
	}

	@Override
	public boolean remove(final int index) {
		final int wordIndex = divideSize(index);
		final long mask = bitMask(index);
		long expected, word;
		do {
			expected = words[wordIndex];
			if ((expected & mask) == 0L) {
				return false;
			}
			word = expected & ~mask;
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		return true;
	}

	@Override
	public void set(final int index) {
		atomicOr(divideSize(index), bitMask(index));
	}

	@Override
	public void set(final int from, final int to) {
		if (from >= to) {
			return;
		}
		final int start = divideSize(from);
		final int end = divideSize(to - 1);
		final long startMask = MASK << from;
		final long endMask = MASK >>> -to;
		if (start == end) {
			atomicOr(start, startMask & endMask);
		} else {
			atomicOr(start, startMask);
			for (int i = start + 1; i < end; i++) {
				setWord(i, MASK);
			}
			atomicOr(end, endMask);
		}
	}

	@Override
	public void clear(final int index) {
		atomicAnd(divideSize(index), ~bitMask(index));
	}

	@Override
	public void clear(final int from, final int to) {
		if (from >= to) {
			return;
		}
		final int start = divideSize(from);
		final int end = divideSize(to - 1);
		final long startMask = MASK << from;
		final long endMask = MASK >>> -to;
		if (start == end) {
			atomicAnd(start, ~(startMask & endMask));
		} else {
			atomicAnd(start, ~startMask);
			for (int i = start + 1; i < end; i++) {
				setWord(i, 0L);
			}
			atomicAnd(end, ~endMask);
		}
	}

	@Override
	public void toggle(final int index) {
		atomicXOr(divideSize(index), bitMask(index));
	}

	@Override
	public void toggle(final int from, final int to) {
		if (from >= to) {
			return;
		}
		final int start = divideSize(from);
		final int end = divideSize(to - 1);
		final long startMask = MASK << from;
		final long endMask = MASK >>> -to;
		if (start == end) {
			atomicXOr(start, startMask & endMask);
		} else {
			atomicXOr(start, startMask);
			for (int i = start + 1; i < end; i++) {
				atomicXOr(i, MASK);
			}
			atomicXOr(end, endMask);
		}
	}

	@Override
	public void setWord(final int wordIndex, final long word) {
		HANDLE.setVolatile(words, wordIndex, word);
	}

	@Override
	public void randomize(final XOrShift random, final int from, final int to) {
		if (from >= to) {
			return;
		}
		final int start = divideSize(from);
		final int end = divideSize(to - 1);
		final long startMask = MASK << from;
		final long endMask = MASK >>> -to;
		long expected, word, randomized;
		if (start == end) {
			long combinedMask = startMask & endMask;
			randomized = random.nextLong();
			do {
				expected = words[start];
				word = (randomized & combinedMask) | (words[start] & ~combinedMask);
			} while (!HANDLE.compareAndSet(words, start, expected, word));
		} else {
			randomized = random.nextLong();
			do {
				expected = words[start];
				word = (randomized & startMask) | (words[start] & ~startMask);
			} while (!HANDLE.compareAndSet(words, start, expected, word));
			for (int i = start + 1; i < end; i++) {
				setWord(i, random.nextLong());
			}
			randomized = random.nextLong();
			do {
				expected = words[end];
				word = (randomized & endMask) | (words[end] & ~endMask);
			} while (!HANDLE.compareAndSet(words, end, expected, word));
		}
	}

	@Override
	public void randomize(final XOrShift random) {
		for (int i = 0; i < words.length; i++) {
			setWord(i, random.nextLong());
		}
	}

	@Override
	public void xOrRandomize(final XOrShift random, final int from, final int to) {
		if (from >= to) {
			return;
		}
		final int start = divideSize(from);
		final int end = divideSize(to - 1);
		final long startMask = MASK << from;
		final long endMask = MASK >>> -to;
		if (start == end) {
			atomicXOr(start, startMask & endMask & random.nextLong());
		} else {
			atomicXOr(start, startMask & random.nextLong());
			for (int i = start + 1; i < end; i++) {
				atomicXOr(i, random.nextLong());
			}
			atomicXOr(end, endMask & random.nextLong());
		}
	}

	@Override
	public void xOrRandomize(final XOrShift random) {
		for (int i = 0; i < words.length; i++) {
			atomicXOr(i, random.nextLong());
		}
	}

	@Override
	public void and(final BitSet set) {
		compareSize(set);
		for (int i = 0; i < words.length; i++) {
			atomicAnd(i, set.words[i]);
		}
	}

	@Override
	public void or(final BitSet set) {
		compareSize(set);
		for (int i = 0; i < words.length; i++) {
			atomicOr(i, set.words[i]);
		}
	}

	@Override
	public void xor(final BitSet set) {
		compareSize(set);
		for (int i = 0; i < words.length; i++) {
			atomicXOr(i, set.words[i]);
		}
	}

	@Override
	public void not() {
		for (int i = 0; i < words.length; i++) {
			atomicXOr(i, MASK);
		}
	}

	@Override
	protected void cleanLastWord() {
		final int hangingBits = modSize(-size);
		if (hangingBits > 0 && words.length > 0) {
			atomicAnd(words.length - 1, MASK >>> hangingBits);
		}
	}

	/**
	 * Atomically changes the long word at <b>wordIndex</b> within {@link #words} to
	 * the result of an {@code AND} operation between the current value at the
	 * specified <b>wordIndex</b> within {@link #words} and the specified
	 * <b>mask</b>. <br>
	 * {@code words[wordIndex] &= mask;}
	 * 
	 * @param wordIndex the index within {@link #words} to perform the {@code AND}
	 *                  operation upon.
	 * @param mask      the mask to use in the {@code AND} operation on the current
	 *                  value at the specified <b>wordIndex</b>.
	 */
	private void atomicAnd(final int wordIndex, final long mask) {
		HANDLE.getAndBitwiseAnd(words, wordIndex, mask);
	}

	/**
	 * Atomically changes the long word at <b>wordIndex</b> within {@link #words} to
	 * the result of an {@code OR} operation between the current value at the
	 * specified <b>wordIndex</b> within {@link #words} and the specified
	 * <b>mask</b>. <br>
	 * {@code words[wordIndex] |= mask;}
	 * 
	 * @param wordIndex the index within {@link #words} to perform the {@code OR}
	 *                  operation upon.
	 * @param mask      the mask to use in the {@code OR} operation on the current
	 *                  value at the specified <b>wordIndex</b>.
	 */
	private void atomicOr(final int wordIndex, final long mask) {
		HANDLE.getAndBitwiseOr(words, wordIndex, mask);
	}

	/**
	 * Atomically changes the long word at <b>wordIndex</b> within {@link #words} to
	 * the result of an {@code XOR} operation between the current value at the
	 * specified <b>wordIndex</b> within {@link #words} and the specified
	 * <b>mask</b>. <br>
	 * {@code words[wordIndex] ^= mask;}
	 * 
	 * @param wordIndex the index within {@link #words} to perform the {@code XOR}
	 *                  operation upon.
	 * @param mask      the mask to use in the {@code XOR} operation on the current
	 *                  value at the specified <b>wordIndex</b>.
	 */
	private void atomicXOr(final int wordIndex, final long mask) {
		HANDLE.getAndBitwiseXor(words, wordIndex, mask);
	}

}
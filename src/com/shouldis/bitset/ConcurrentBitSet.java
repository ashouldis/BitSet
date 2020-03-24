package com.shouldis.bitset;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Implementation of {@link BitSet} in which all methods capable of reading or
 * writing the state of bits such as {@link #setWord(int, long)},
 * {@link #andWord(int, long)}, {@link #orWord(int, long)},
 * {@link #xorWord(int, long)} are delegated to atomic-operations.
 * {@link #getWord(int)} is also performed by the same semantics.
 * <p>
 * The use of atomic operations allows concurrent modification of this
 * {@link ConcurrentBitSet} without any external synchronization at the cost of
 * processing time. These operations are done by the semantics of
 * {@link VarHandle#setVolatile(Object...)}.
 * <p>
 * All methods have behavior as specified by {@link BitSet}.
 * 
 * @author Aaron Shouldis
 * @see BitSet
 */
public final class ConcurrentBitSet extends BitSet {

	private static final long serialVersionUID = 1L;

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
			expected = getWord(wordIndex);
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
			expected = getWord(wordIndex);
			if ((expected & mask) == 0L) {
				return false;
			}
			word = expected & ~mask;
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		return true;
	}

	@Override
	public long getWord(final int wordIndex) {
		return (long) HANDLE.getVolatile(words, wordIndex);
	}

	@Override
	public void setWord(final int wordIndex, final long word) {
		HANDLE.setVolatile(words, wordIndex, word);
	}

	@Override
	public void setWordSegment(final int wordIndex, final long word, final long mask) {
		long expected, newWord;
		do {
			expected = getWord(wordIndex);
			newWord = (mask & word) | (~mask & expected);
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, newWord));
	}

	@Override
	public void andWord(final int wordIndex, final long mask) {
		HANDLE.getAndBitwiseAnd(words, wordIndex, mask);
	}

	@Override
	public void orWord(final int wordIndex, final long mask) {
		HANDLE.getAndBitwiseOr(words, wordIndex, mask);
	}

	@Override
	public void xorWord(final int wordIndex, final long mask) {
		HANDLE.getAndBitwiseXor(words, wordIndex, mask);
	}

	@Override
	public void notAndWord(final int wordIndex, final long mask) {
		long expected, word;
		do {
			expected = getWord(wordIndex);
			word = ~(expected & mask);
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
	}

	@Override
	public void notOrWord(final int wordIndex, final long mask) {
		long expected, word;
		do {
			expected = getWord(wordIndex);
			word = ~(expected | mask);
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
	}

	@Override
	public void notXOrWord(final int wordIndex, final long mask) {
		long expected, word;
		do {
			expected = getWord(wordIndex);
			word = ~(expected ^ mask);
		} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
	}

	@Override
	public void shiftWordRight(final int wordIndex, final int distance) {
		long expected, word;
		if (wordIndex == wordCount - 1 && hanging != 0) {
			do {
				expected = getWord(wordIndex);
				word = (hangingMask & expected) >>> distance;
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		} else {
			do {
				expected = getWord(wordIndex);
				word = expected >>> distance;
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		}
	}

	@Override
	public void shiftWordLeft(final int wordIndex, final int distance) {
		long expected, word;
		if (wordIndex == wordCount - 1 && hanging != 0) {
			do {
				expected = getWord(wordIndex);
				word = hangingMask & (expected << distance);
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		} else {
			do {
				expected = getWord(wordIndex);
				word = expected << distance;
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		}
	}

	@Override
	public void rotateWordRight(final int wordIndex, int distance) {
		long expected, word;
		if (wordIndex == wordCount - 1 && hanging != 0) {
			if (distance > 0) {
				do {
					expected = getWord(wordIndex);
					word = hangingMask & ((expected >>> distance) | (expected << -(hanging + distance)));
				} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
			} else if (distance < 0) {
				distance = Math.abs(distance);
				do {
					expected = getWord(wordIndex);
					word = hangingMask & ((expected << distance) | (expected >>> -(hanging + distance)));
				} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
			}
		} else {
			do {
				expected = getWord(wordIndex);
				word = Long.rotateRight(getWord(wordIndex), distance);
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		}
	}

	@Override
	public void rotateWordLeft(final int wordIndex, int distance) {
		long expected, word;
		if (wordIndex == wordCount - 1 && hanging != 0) {
			if (distance > 0) {
				do {
					expected = getWord(wordIndex);
					word = hangingMask & ((expected << distance) | (expected >>> -(hanging + distance)));
				} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
			} else if (distance < 0) {
				distance = Math.abs(distance);
				do {
					expected = getWord(wordIndex);
					word = hangingMask & ((expected >>> distance) | (expected << -(hanging + distance)));
				} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
			}
		} else {
			do {
				expected = getWord(wordIndex);
				word = Long.rotateLeft(getWord(wordIndex), distance);
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		}
	}

	@Override
	public void reverseWord(final int wordIndex) {
		long expected, word;
		if (wordIndex == wordCount - 1 && hanging != 0) {
			do {
				expected = getWord(wordIndex);
				word = hangingMask & (Long.reverse(expected) >>> hanging);
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		} else {
			do {
				expected = getWord(wordIndex);
				word = Long.reverse(expected);
			} while (!HANDLE.compareAndSet(words, wordIndex, expected, word));
		}
	}

}
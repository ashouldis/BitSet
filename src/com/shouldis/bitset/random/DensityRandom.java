package com.shouldis.bitset.random;

import com.shouldis.bitset.BitSet;

/**
 * Implementation of {@link Random} which generates bits with a specified
 * Hamming-weight ratio using a sequence of bitwise {@code AND} and {@code OR}
 * operations. This class combines multiple random words to create words with
 * each bit having an expected average density, within some tolerance to the
 * desired density.
 * <p>
 * {@link #nextInt()}, {@link #nextLong()}, and {@link #nextBoolean()} will use
 * this algorithm to generate bits with the specified density, while the other
 * methods retain the behavior of {@link Random}.
 * <p>
 * In uniformly distributed random fields of bits, each bit will have a 50%
 * chance of being in the <i>live</i> state. This class derives a sequence of
 * {@code AND} and {@code OR} operations such that operating
 * uniformly-distributed fields of bits against each-other in that sequence
 * produces a field of bits with density within 2<sup>-(n +1)</sup> of the
 * desired value in the range [2<sup>-n</sup>, 1 -2<sup>-n</sup>], where n is
 * the number of operations, or {@link #depth}. Between a uniformly distributed
 * field of bits, and a second field of bits with a density d, an {@code AND}
 * operation will result in density d/2, and an {@code OR} operation will result
 * in density (d +1)/2.
 * <p>
 * Given a desired density and depth, an estimation can be created in the form
 * of a fraction, wherein the numerator is round(2<sup>depth</sup>density), and
 * the denominator is 2<sup>depth</sup>. A depth can be derived from a tolerance
 * value by -log<sub>2</sub>tolerance. Reductions can be found using
 * floor(log<sub>2</sub>numerator); that number of reductions can be safely
 * subtracted from depth, and divided from the numerator, resulting in an
 * equivalent fraction. The estimated density can then be reconstructed as
 * numerator/2<sup>depth</sup>. The sequence of operations used to generate bits
 * with the specified density is then equal to the numerator bit-shifted to the
 * right by 1. Traversing the sequence one bit at a time, from least significant
 * toward most significant, stopping after {@link #depth} number of bits.
 * Starting with a uniformly-distributed field of bits, when a <i>live</i> bit
 * is encountered, an {@code OR} operation is performed against another
 * uniformly-distributed field of bits, and when a <i>dead</i> bit is
 * encountered, an {@code AND} operation is performed.
 * <p>
 * The underlying pseudo-random number generator {@link Random} has a period of
 * 2<sup>64</sup> -1. This generator consumes multiple elements of that sequence
 * with each call to {@link #nextInt()}, {@link #nextLong()}, and every 64th
 * call to {@link #nextBoolean()}. As a side-effect of consuming multiple
 * elements of the sequence with each call, it is possible to reduce the period
 * of the sequence by a factor of {@link #depth} if it is a divisor of
 * 2<sup>64</sup> -1 such as 3, 5, 15, 17 or 51.
 * 
 * @author Aaron Shouldis
 * @see Random
 */
public final class DensityRandom extends Random {

	/**
	 * The density of words generated by {@link #nextInt()}, {@link #nextLong()},
	 * and the rate at which {@link #nextBoolean()} returns {@code true}. Integers
	 * will have on average ({@link #density} * 32) bits in the <i>live</i> state,
	 * and longs will have ({@link #density} * 64) bits in the <i>live</i> state,
	 * with each generated bit having a chance equal to {@link #density} to be in
	 * the <i>live</i> state.
	 */
	public final double density;

	/**
	 * The depth, or number of operations performed in order to achieve an
	 * estimation of {@link #density}. This value is bounded to the range [1,
	 * {@link Long#SIZE}) by {@link #boundDepth(int)}, resulting in an accuracy of
	 * up to 2<sup>-64</sup>.
	 */
	public final int depth;

	/**
	 * The sequence of operations used to operate uniformly distributed random bits
	 * against each-other in order to generate specified density.
	 */
	private final long sequence;

	/**
	 * The field of bits used by {@link #nextBoolean()} to more efficiently produce
	 * boolean values which are {@code true} at the rate determined by
	 * {@link #density}. This field is refreshed with {@link #nextLong()} after all
	 * bits have been consumed.
	 */
	private long booleanWord;

	/**
	 * The internal counter to track how many bits of {@link #booleanWord} have been
	 * consumed by {@link #nextBoolean()}.
	 */
	private int bitsConsumed;

	/**
	 * Creates an instance of {@link DensityRandom} with the specified
	 * <b>density</b>, <b>depth</b>, and <b>seed</b>. <b>density</b> will be bounded
	 * by {@link #boundDensity(double, int)} to ensure it is always a valid
	 * percentage. <b>depth</b> will be bounded by {@link #boundDepth(int)} to
	 * ensure it is with in the range [1, {@link Long#SIZE}).
	 * <p>
	 * {@link #nextInt()}, {@link #nextLong()}, and {@link #nextBoolean()} of this
	 * {@link DensityRandom} will generate <i>live</i> bits at a rate determined by
	 * <b>density</b>. <b>depth</b> number of random number generation operations
	 * will be performed to derive a word of that desired <b>density</b>.
	 * 
	 * @param density the ratio of <i>live</i> bits to <i>dead</i> bits to be
	 *                produced.
	 * @param depth   how many operations to perform in order to estimate the
	 *                desired <b>density</b>.
	 * @param seed    the value to initialize the sequence of pseudo-random numbers
	 *                with.
	 */
	public DensityRandom(double density, int depth, final long seed) {
		super(seed);
		depth = boundDepth(depth);
		density = boundDensity(density, depth);
		long numerator = Math.round(density / powerInverse(depth));
		final int reductions = Long.numberOfTrailingZeros(numerator);
		numerator >>>= reductions;
		sequence = numerator >>> 1;
		this.depth = depth - reductions;
		this.density = numerator * powerInverse(this.depth);
	}

	/**
	 * Creates an instance of {@link DensityRandom} with the specified
	 * <b>density</b> and <b>depth</b>. <b>density</b> will be bounded by
	 * {@link #boundDensity(double, int)} to ensure it is always a valid percentage.
	 * <b>depth</b> will be bounded by {@link #boundDepth(int)} to ensure it is with
	 * in the range [1, {@link Long#SIZE}).
	 * <p>
	 * {@link #nextInt()}, {@link #nextLong()}, and {@link #nextBoolean()} of this
	 * {@link DensityRandom} will generate <i>live</i> bits at a rate determined by
	 * <b>density</b>. <b>depth</b> number of random number generation operations
	 * will be performed to derive a word of that desired <b>density</b>.
	 * 
	 * @param density the ratio of <i>live</i> bits to <i>dead</i> bits to be
	 *                produced.
	 * @param depth   how many operations to perform in order to estimate the
	 *                desired <b>density</b>.
	 */
	public DensityRandom(final double density, final int depth) {
		this(density, depth, generateSeed());
	}

	/**
	 * Creates an instance of {@link DensityRandom} with the specified
	 * <b>density</b>, <b>tolerance</b>, and <b>seed</b>. <b>density</b> will be
	 * bounded by {@link #boundDensity(double, int)} to ensure it is always a valid
	 * percentage. <b>tolerance</b> will be used to compute a {@link #depth}, which
	 * will then be bounded by {@link #boundDepth(int)} to ensure it is with in the
	 * range [1, {@link Long#SIZE}).
	 * <p>
	 * {@link #nextInt()}, {@link #nextLong()}, and {@link #nextBoolean()} of this
	 * {@link DensityRandom} will generate <i>live</i> bits at a rate determined by
	 * <b>density</b>. <b>depth</b> number of random number generation operations
	 * will be performed to derive a word of that desired <b>density</b>.
	 * 
	 * @param density   the ratio of <i>live</i> bits to <i>dead</i> bits to be
	 *                  produced.
	 * @param tolerance a percentage used to describe the distance that is
	 *                  acceptable to have between <b>density</b>, and the value it
	 *                  is estimated to.
	 * @param seed      the value to initialize the sequence of pseudo-random
	 *                  numbers with.
	 */
	public DensityRandom(final double density, final double tolerance, final long seed) {
		this(density, toleranceDepth(tolerance), seed);
	}

	/**
	 * Creates an instance of {@link DensityRandom} with the specified
	 * <b>density</b> and <b>tolerance</b>. <b>density</b> will be bounded by
	 * {@link #boundDensity(double, int)} to ensure it is always a valid percentage.
	 * <b>tolerance</b> will be used to compute a {@link #depth}, which will then be
	 * bounded by {@link #boundDepth(int)} to ensure it is with in the range [1,
	 * {@link Long#SIZE}).
	 * <p>
	 * {@link #nextInt()}, {@link #nextLong()}, and {@link #nextBoolean()} of this
	 * {@link DensityRandom} will generate <i>live</i> bits at a rate determined by
	 * <b>density</b>. <b>depth</b> number of random number generation operations
	 * will be performed to derive a word of that desired <b>density</b>.
	 * 
	 * @param density   the ratio of <i>live</i> bits to <i>dead</i> bits to be
	 *                  produced.
	 * @param tolerance a percentage used to describe the maximum distance that is
	 *                  acceptable to have between <b>density</b>, and the value it
	 *                  is estimated to.
	 */
	public DensityRandom(final double density, final double tolerance) {
		this(density, toleranceDepth(tolerance), generateSeed());
	}

	@Override
	public long nextLong() {
		long word = super.nextLong();
		for (long bit = 1L; bit != 1L << (depth - 1); bit <<= 1) {
			if ((bit & sequence) == 0L) {
				word &= super.nextLong();
			} else {
				word |= super.nextLong();
			}
		}
		return word;
	}

	@Override
	public int nextInt() {
		return (int) (nextLong() >>> Integer.SIZE);
	}

	@Override
	public boolean nextBoolean() {
		if (BitSet.modSize(bitsConsumed) == 0) {
			booleanWord = nextLong();
		}
		return (booleanWord & BitSet.bitMask(bitsConsumed++)) != 0L;
	}

	/**
	 * Computes a {@link #depth} corresponding to the specified <b>tolerance</b>.
	 * <b>tolerance</b> will be bounded by {@link #boundPercentage(double)}, to
	 * ensure it is a valid percentage used to describe the max acceptable distance
	 * between a {@link DensityRandom}'s {@link #density}, and the value it is
	 * estimated to.
	 * 
	 * @param tolerance the percentage to transform into a {@link #depth}.
	 * @return the required depth to ensure the generated bit's density is within
	 *         <b>tolerance</b> of the specified density when creating a
	 *         {@link DensityRandom}
	 */
	public static int toleranceDepth(final double tolerance) {
		return -Math.getExponent(boundPercentage(tolerance));
	}

	/**
	 * Efficient method of computing 2<sup>-<b>power</b></sup> doubles.
	 * 
	 * @param power the desired power of the resulting double.
	 * @return a double representation of 2<sup>-<b>power</b></sup>.
	 */
	public static double powerInverse(final int power) {
		return Double.longBitsToDouble((long) (Double.MAX_EXPONENT - power) << 52);
	}

	/**
	 * Ensures that the specified <b>density</b> is within the acceptable range
	 * depending on <b>depth</b> such that it will fall in
	 * [2<sup>-<b>depth</b></sup>, 1 -2<sup>-<b>depth</b></sup>].
	 * 
	 * @param density the density which will be bounded using the specified
	 *                <b>depth</b>.
	 * @param depth   the max number of operations used to estimate <b>density</b>.
	 * @return <b>density</b> bounded within [2<sup>-<b>depth</b></sup>, 1
	 *         -2<sup>-<b>depth</b></sup>].
	 */
	private static double boundDensity(final double density, final int depth) {
		final double inverse = powerInverse(depth);
		return Math.min(Math.max(density, inverse), 1.0 - inverse);
	}

	/**
	 * Ensures that the specified <b>density</b> is within the range [0, 1].
	 * 
	 * @param density the value to bound, representing the desired ratio of
	 *                <i>live</i> bits to <i>dead</i> bits.
	 * @return <b>density</b> bounded within [0, 1].
	 */
	private static double boundPercentage(final double density) {
		return Math.min(Math.max(density, 0.0), 1.0);
	}

	/**
	 * Ensures that the specified <b>depth</b> is within the range [1,
	 * {@link Long#SIZE}).
	 * 
	 * @param depth the value to bound, representing the max number of operations.
	 * @return <b>depth</b> bounded within [1, 63].
	 */
	private static int boundDepth(final int depth) {
		return Math.min(Math.max(depth, 1), 63);
	}

}
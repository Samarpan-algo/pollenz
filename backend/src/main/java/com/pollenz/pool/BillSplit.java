package com.pollenz.pool;

/**
 * The bill split rule from the SPEC, as pure arithmetic with no Spring or database dependency.
 *
 * {@code share[i] = itemTotals[i] + feesPaise / memberCount} (integer division); the remainder paise
 * of that division go to the host. Members with no items (item total 0) still pay a fee share.
 * Invariant: the shares sum exactly to the item totals plus {@code feesPaise}.
 *
 * Overflow is checked rather than assumed away: if the item totals plus fees do not fit in a long,
 * an {@link ArithmeticException} is thrown and nothing is returned. Once that grand total fits, every
 * share fits too, because all inputs are non-negative.
 */
final class BillSplit {

	private BillSplit() {
	}

	/**
	 * @param itemTotals per-member sum of final item prices, in paise; one entry per member, so the
	 *                   member count is {@code itemTotals.length}
	 * @param feesPaise  shared fees, in paise
	 * @param hostIndex  index of the host in {@code itemTotals}
	 * @return per-member shares, in the same order as {@code itemTotals}
	 * @throws IllegalArgumentException if there are no members, a value is negative or the host index
	 *                                  is out of range
	 * @throws ArithmeticException      if the item totals plus fees overflow a long
	 */
	static long[] shares(long[] itemTotals, long feesPaise, int hostIndex) {
		int memberCount = itemTotals.length;
		if (memberCount == 0) {
			throw new IllegalArgumentException("A pool always has at least one member");
		}
		if (hostIndex < 0 || hostIndex >= memberCount) {
			throw new IllegalArgumentException("Host index " + hostIndex + " out of range");
		}
		if (feesPaise < 0) {
			throw new IllegalArgumentException("Fees must not be negative");
		}
		long grandTotal = feesPaise;
		for (long total : itemTotals) {
			if (total < 0) {
				throw new IllegalArgumentException("Item totals must not be negative");
			}
			grandTotal = Math.addExact(grandTotal, total);
		}

		long feeShare = feesPaise / memberCount;
		long remainder = feesPaise % memberCount;
		long[] shares = new long[memberCount];
		long sum = 0;
		for (int i = 0; i < memberCount; i++) {
			shares[i] = Math.addExact(itemTotals[i], feeShare);
			if (i == hostIndex) {
				shares[i] = Math.addExact(shares[i], remainder);
			}
			sum = Math.addExact(sum, shares[i]);
		}
		if (sum != grandTotal) {
			// Cannot happen with the arithmetic above; kept so a future change cannot break it silently.
			throw new IllegalStateException("Shares sum to " + sum + ", expected " + grandTotal);
		}
		return shares;
	}

}

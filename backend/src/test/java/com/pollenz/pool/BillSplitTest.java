package com.pollenz.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

/** The bill split rule on its own: no Spring, no database. */
class BillSplitTest {

	private static final long CAP = ItemRequest.MAX_PAISE;

	@Test
	void evenSplit() {
		assertThat(BillSplit.shares(new long[] { 10000, 20000, 30000 }, 3000, 0))
				.containsExactly(11000, 21000, 31000);
	}

	@Test
	void remainderGoesToTheHost() {
		// 100 / 3 = 33 rest 1: the host (index 1) pays 34.
		assertThat(BillSplit.shares(new long[] { 0, 0, 0 }, 100, 1)).containsExactly(33, 34, 33);
		// 1003 / 4 = 250 rest 3.
		assertThat(BillSplit.shares(new long[] { 500, 700, 0, 100 }, 1003, 3)).containsExactly(750, 950, 250, 353);
		// Fees smaller than the member count: everyone else pays no fee at all.
		assertThat(BillSplit.shares(new long[] { 100, 200, 300 }, 2, 2)).containsExactly(100, 200, 302);
	}

	@Test
	void memberWithNoItemsStillPaysAFeeShare() {
		assertThat(BillSplit.shares(new long[] { 5000, 0, 0 }, 3000, 0)).containsExactly(6000, 1000, 1000);
	}

	@Test
	void zeroFees() {
		assertThat(BillSplit.shares(new long[] { 1234, 0, 999 }, 0, 0)).containsExactly(1234, 0, 999);
	}

	@Test
	void zeroPricedItemsAddNothing() {
		// A member whose only item was unavailable has an item total of 0.
		assertThat(BillSplit.shares(new long[] { 4000, 0 }, 0, 0)).containsExactly(4000, 0);
		assertThat(BillSplit.shares(new long[] { 0, 0 }, 0, 1)).containsExactly(0, 0);
	}

	@Test
	void singleMemberPaysEverything() {
		assertThat(BillSplit.shares(new long[] { 1234 }, 99, 0)).containsExactly(1333);
		assertThat(BillSplit.shares(new long[] { 0 }, 0, 0)).containsExactly(0);
	}

	@Test
	void largeValuesNearTheCap() {
		// Members with thousands of items at the cap, fees at the cap.
		long[] totals = { 5000 * CAP, 7000 * CAP, 0, CAP };
		long[] shares = BillSplit.shares(totals, CAP, 2);
		long feeShare = CAP / 4;
		assertThat(shares).containsExactly(5000 * CAP + feeShare, 7000 * CAP + feeShare, feeShare + CAP % 4,
				CAP + feeShare);
		assertThat(sum(shares)).isEqualTo(sum(totals) + CAP);

		// Exactly Long.MAX_VALUE in total still fits.
		assertThat(BillSplit.shares(new long[] { Long.MAX_VALUE - 10, 0 }, 10, 0))
				.containsExactly(Long.MAX_VALUE - 5, 5);
	}

	@Test
	void overflowIsRejected() {
		assertThatThrownBy(() -> BillSplit.shares(new long[] { Long.MAX_VALUE, 1 }, 0, 0))
				.isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> BillSplit.shares(new long[] { Long.MAX_VALUE }, 1, 0))
				.isInstanceOf(ArithmeticException.class);
		assertThatThrownBy(() -> BillSplit.shares(new long[] { Long.MAX_VALUE / 2 + 1, Long.MAX_VALUE / 2 + 1 }, 0, 1))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void invalidInputsAreRejected() {
		assertThatThrownBy(() -> BillSplit.shares(new long[0], 0, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BillSplit.shares(new long[] { 1 }, 0, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BillSplit.shares(new long[] { 1 }, 0, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BillSplit.shares(new long[] { 1 }, -1, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> BillSplit.shares(new long[] { -1, 5 }, 0, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void inputIsNotModified() {
		long[] totals = { 100, 200 };
		BillSplit.shares(totals, 51, 0);
		assertThat(totals).containsExactly(100, 200);
	}

	/** The rule and the sum invariant over many random pools; the seed is fixed, so failures repeat. */
	@Test
	void randomizedSplitsKeepTheInvariant() {
		Random random = new Random(20261117L);
		for (int run = 0; run < 2000; run++) {
			int members = 1 + random.nextInt(40);
			long[] totals = new long[members];
			for (int i = 0; i < members; i++) {
				// Some members with no items, some with up to 50 items at the cap.
				totals[i] = random.nextInt(4) == 0 ? 0 : (long) (random.nextDouble() * 50 * CAP);
			}
			long fees = random.nextBoolean() ? random.nextInt(1000) : (long) (random.nextDouble() * CAP);
			int host = random.nextInt(members);
			String context = "run " + run + ": totals " + Arrays.toString(totals) + ", fees " + fees + ", host " + host;

			long[] shares = BillSplit.shares(totals, fees, host);

			assertThat(shares).as(context).hasSize(members);
			assertThat(sum(shares)).as(context).isEqualTo(sum(totals) + fees);
			for (int i = 0; i < members; i++) {
				long expected = totals[i] + fees / members + (i == host ? fees % members : 0);
				assertThat(shares[i]).as(context + ", member " + i).isEqualTo(expected);
			}
		}
	}

	private static long sum(long[] values) {
		long sum = 0;
		for (long v : values) {
			sum = Math.addExact(sum, v);
		}
		return sum;
	}

}

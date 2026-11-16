package com.pollenz.pool;

/** {@code finalPricePaise} stays null until the {@code order} action. */
public record PoolItemResponse(Long id, String name, int quantity, long estPricePaise, Long finalPricePaise) {

	public static PoolItemResponse from(PoolItem i) {
		return new PoolItemResponse(i.getId(), i.getName(), i.getQuantity(), i.getEstPricePaise(),
				i.getFinalPricePaise());
	}

}

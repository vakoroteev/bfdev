package com.betfair.aping.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PriceSize {
	@JsonProperty("p")
	private Double price;
	@JsonProperty("s")
	private Double size;

	public Double getPrice() {
		return price;
	}

	public void setPrice(Double price) {
		this.price = price;
	}

	public Double getSize() {
		return size;
	}

	public void setSize(Double size) {
		this.size = size;
	}

}

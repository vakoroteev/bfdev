package my.pack.model;

import com.betfair.aping.entities.ExchangePrices;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class HorseStatBean {

	@JsonProperty("tm")
	Double totalMatched;
	ExchangePrices ex;
	@JsonProperty("sp")
	StartPrice startPrice;
	@JsonProperty("tmst")
	private Long timestamp;

	public HorseStatBean() {

	}

	public HorseStatBean(Double totalMatched, ExchangePrices ex,
			StartPrice startPrice, Long timestamp) {
		super();
		this.totalMatched = totalMatched;
		this.ex = ex;
		this.startPrice = startPrice;
		this.setTimestamp(timestamp);
	}

	public Double getTotalMatched() {
		return totalMatched;
	}

	public void setTotalMatched(Double totalMatched) {
		this.totalMatched = totalMatched;
	}

	public ExchangePrices getEx() {
		return ex;
	}

	public void setEx(ExchangePrices ex) {
		this.ex = ex;
	}

	public StartPrice getStartPrice() {
		return startPrice;
	}

	public void setStartPrice(StartPrice startPrice) {
		this.startPrice = startPrice;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

}

package my.pack.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class StartPrice {

	@JsonProperty("ap")
	Double actualPrice;
	@JsonProperty("fp")
	Double farFpice;
	@JsonProperty("np")
	Double nearPrice;

	public StartPrice() {

	};

	public StartPrice(Double actualPrice, Double farFpice, Double nearPrice) {
		super();
		this.actualPrice = actualPrice;
		this.farFpice = farFpice;
		this.nearPrice = nearPrice;
	}

	public Double getActualPrice() {
		return actualPrice;
	}

	public void setActualPrice(Double actualPrice) {
		this.actualPrice = actualPrice;
	}

	public Double getFarFpice() {
		return farFpice;
	}

	public void setFarFpice(Double farFpice) {
		this.farFpice = farFpice;
	}

	public Double getNearPrice() {
		return nearPrice;
	}

	public void setNearPrice(Double nearPrice) {
		this.nearPrice = nearPrice;
	}
	
	

}

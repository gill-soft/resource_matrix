package com.gillsoft.client;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(value = Include.NON_NULL)
public class Price implements Serializable {
	
	private static final long serialVersionUID = 7503673018700582892L;

	@JsonProperty("one_way")
	private int oneWay;
	
	@JsonProperty("round_trip")
	private int roundTrip;

	public int getOneWay() {
		return oneWay;
	}

	public void setOneWay(int oneWay) {
		this.oneWay = oneWay;
	}

	public int getRoundTrip() {
		return roundTrip;
	}

	public void setRoundTrip(int roundTrip) {
		this.roundTrip = roundTrip;
	}

}

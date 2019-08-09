package com.gillsoft.client;

import java.util.Date;

import com.gillsoft.model.AbstractJsonModel;

public class TripIdModel extends AbstractJsonModel {

	private static final long serialVersionUID = 6685617842271023619L;

	private String intervalId;

	private String routeId;

	private String from;

	private String to;

	private Date date;

	private String currency;

	public TripIdModel() {

	}

	public TripIdModel(String intervalId, String routeId, String from, String to, Date date, String currency) {
		this.intervalId = intervalId;
		this.routeId = routeId;
		this.from = from;
		this.to = to;
		this.date = date;
		this.currency = currency;
	}

	public String getIntervalId() {
		return intervalId;
	}

	public void setIntervalId(String intervalId) {
		this.intervalId = intervalId;
	}

	public String getRouteId() {
		return routeId;
	}

	public void setRouteId(String routeId) {
		this.routeId = routeId;
	}

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	@Override
	public TripIdModel create(String json) {
		return (TripIdModel) super.create(json);
	}

}

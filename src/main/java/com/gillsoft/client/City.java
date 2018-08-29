package com.gillsoft.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(value = Include.NON_NULL)
public class City extends Country {

	private static final long serialVersionUID = -443963281701008310L;

	@JsonProperty("geo_country_id")
	private int geoCountryId;

	@JsonProperty("geo_region_id")
	private int geoRegionId;
	private BigDecimal latitude;
	private BigDecimal longitude;

	public int getGeoCountryId() {
		return geoCountryId;
	}

	public void setGeoCountryId(int geoCountryId) {
		this.geoCountryId = geoCountryId;
	}

	public int getGeoRegionId() {
		return geoRegionId;
	}

	public void setGeoRegionId(int geoRegionId) {
		this.geoRegionId = geoRegionId;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public void setLatitude(BigDecimal latitude) {
		this.latitude = latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public void setLongitude(BigDecimal longitude) {
		this.longitude = longitude;
	}

}

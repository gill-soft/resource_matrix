package com.gillsoft.client;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(value = Include.NON_NULL)
public class Discount extends Fee {

	private static final long serialVersionUID = -4812961676356483394L;

	@JsonProperty("discount_kind")
	private String discountKind;
	
	@JsonProperty("discount_type")
	private String discountType;
	
	@JsonProperty("discount_value")
	private BigDecimal discountValue;

	private Map<String, Parameters> i18n;

	public String getDiscountKind() {
		return discountKind;
	}

	public void setDiscountKind(String discountKind) {
		this.discountKind = discountKind;
	}

	public String getDiscountType() {
		return discountType;
	}

	public void setDiscountType(String discountType) {
		this.discountType = discountType;
	}

	public BigDecimal getDiscountValue() {
		return discountValue;
	}

	public void setDiscountValue(BigDecimal discountValue) {
		this.discountValue = discountValue;
	}

	public Map<String, Parameters> getI18n() {
		return i18n;
	}

	public void setI18n(Map<String, Parameters> i18n) {
		this.i18n = i18n;
	}
	
}

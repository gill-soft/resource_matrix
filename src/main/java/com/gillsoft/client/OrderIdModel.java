package com.gillsoft.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gillsoft.model.AbstractJsonModel;

public class OrderIdModel extends AbstractJsonModel {

	private static final long serialVersionUID = 4318484251645220464L;

	private Map<String, List<String>> ids = new HashMap<>();

	public OrderIdModel() {

	}

	public Map<String, List<String>> getIds() {
		return ids;
	}

	public void setIds(Map<String, List<String>> ids) {
		this.ids = ids;
	}

	@Override
	public OrderIdModel create(String json) {
		return (OrderIdModel) super.create(json);
	}

}

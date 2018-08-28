package com.gillsoft.client;

import java.util.List;

import com.gillsoft.cache.AbstractUpdateTask;
import com.gillsoft.util.ContextProvider;

public class CitiesUpdateTask extends AbstractUpdateTask {

	private static final long serialVersionUID = 1792318881958028832L;
	
	protected String locale;

	public CitiesUpdateTask(String locale) {
		this.locale = locale;
	}
	
	@Override
	public void run() {
		RestClient client = ContextProvider.getBean(RestClient.class);
		try {
			List<City> cities = client.getCities(locale);
			writeObjectIgnoreAge(client.getCache(), RestClient.getCitiesCacheKey(locale),
					cities, Config.getCacheStationsUpdateDelay());
		} catch (ResponseError e) {
			writeObject(client.getCache(), RestClient.getCitiesCacheKey(locale),
					e, Config.getCacheErrorTimeToLive(), Config.getCacheErrorUpdateDelay());
		}
	}

}

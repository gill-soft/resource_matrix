package com.gillsoft.client;

import java.text.ParseException;
import java.util.List;

import org.springframework.util.MultiValueMap;

import com.gillsoft.cache.AbstractUpdateTask;
import com.gillsoft.util.ContextProvider;
import com.gillsoft.util.StringUtil;

public class TripsUpdateTask extends AbstractUpdateTask {

	private static final long serialVersionUID = -7483878909388429051L;
	
	private MultiValueMap<String, String> params;

	public TripsUpdateTask(MultiValueMap<String, String> params) {
		this.params = params;
	}

	@Override
	public void run() {
		RestClient client = ContextProvider.getBean(RestClient.class);
		try {
			List<Trip> response = client.getTrips(params);
			writeObject(client.getCache(), RestClient.getCacheKey(RestClient.TRIPS_CACHE_KEY, params), response,
					getTimeToLive(response), Config.getCacheTripUpdateDelay());
		} catch (ResponseError e) {
			writeObject(client.getCache(), RestClient.getCacheKey(RestClient.TRIPS_CACHE_KEY, params), e,
					Config.getCacheErrorTimeToLive(), Config.getCacheErrorUpdateDelay());
		}
	}
	
	// время жизни до момента самого позднего отправления
	private long getTimeToLive(List<Trip> trips) {
		if (Config.getCacheTripTimeToLive() != 0) {
			return Config.getCacheTripTimeToLive();
		}
		long max = 0;
		for (Trip trip : trips) {
			try {
				long date = StringUtil.fullDateFormat.parse(StringUtil.dateFormat.format(trip.getDepartDate()) + " " + trip.getDepartTime()).getTime();
				if (date > max) {
					max = date;
				}
			} catch (ParseException e) {
			}
		}
		if (max == 0
				|| max < System.currentTimeMillis()) {
			return Config.getCacheErrorTimeToLive();
		}
		return max - System.currentTimeMillis();
	}

}

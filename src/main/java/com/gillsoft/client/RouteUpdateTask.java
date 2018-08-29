package com.gillsoft.client;

import org.springframework.util.MultiValueMap;

import com.gillsoft.cache.AbstractUpdateTask;
import com.gillsoft.util.ContextProvider;

public class RouteUpdateTask extends AbstractUpdateTask {

	private static final long serialVersionUID = -6859368060540072639L;
	
	protected MultiValueMap<String, String> params;
	
	public RouteUpdateTask(MultiValueMap<String, String> params) {
		this.params = params;
	}

	@Override
	public void run() {
		RestClient client = ContextProvider.getBean(RestClient.class);
		RouteInfo route;
		try {
			route = client.getRoute(params);
			writeObject(client.getCache(), RestClient.getCacheKey(RestClient.ROUTE_CACHE_KEY, params),
					route, getTimeToLive(route), Config.getCacheTripUpdateDelay());
		} catch (ResponseError e) {
			writeObject(client.getCache(), RestClient.getCacheKey(RestClient.ROUTE_CACHE_KEY, params),
					e, Config.getCacheErrorTimeToLive(), Config.getCacheErrorUpdateDelay());
		}
	}
	
	// время жизни до конца существования маршрута
	private long getTimeToLive(RouteInfo routeInfo) {
		if (Config.getCacheTripTimeToLive() != 0) {
			return Config.getCacheTripTimeToLive();
		}
		if (routeInfo.getRoute().getEnded() == null
				|| routeInfo.getRoute().getEnded().getTime() < System.currentTimeMillis()) {
			return Config.getCacheErrorTimeToLive();
		}
		return routeInfo.getRoute().getEnded().getTime() - System.currentTimeMillis();
	}

}

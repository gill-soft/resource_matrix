package com.gillsoft.client;

import java.util.Date;
import java.util.List;

import org.springframework.util.MultiValueMap;

import com.gillsoft.cache.AbstractUpdateTask;
import com.gillsoft.util.ContextProvider;

public class ReturnRuleUpdateTask extends AbstractUpdateTask {

	private static final long serialVersionUID = 723857136441377723L;

	private MultiValueMap<String, String> params;
	private Date tripDate;
	
	public ReturnRuleUpdateTask(MultiValueMap<String, String> params, Date tripDate) {
		this.params = params;
	}

	@Override
	public void run() {
		RestClient client = ContextProvider.getBean(RestClient.class);
		try {
			List<ReturnRule> rules = client.getReturnRules(params);
			writeObject(client.getCache(), RestClient.getCacheKey(RestClient.RULE_CACHE_KEY, params),
					rules, getTimeToLive(), Config.getCacheRouteUpdateDelay());
		} catch (ResponseError e) {
			writeObject(client.getCache(), RestClient.getCacheKey(RestClient.RULE_CACHE_KEY, params),
					e, Config.getCacheErrorTimeToLive(), Config.getCacheErrorUpdateDelay());
		}
	}
	
	// время жизни до конца существования маршрута
	private long getTimeToLive() {
		if (Config.getCacheTripTimeToLive() != 0) {
			return Config.getCacheTripTimeToLive();
		}
		if (tripDate == null
				|| tripDate.getTime() < System.currentTimeMillis()) {
			return Config.getCacheErrorTimeToLive();
		}
		return tripDate.getTime() - System.currentTimeMillis();
	}

}

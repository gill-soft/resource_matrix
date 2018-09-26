package com.gillsoft.client;

import com.gillsoft.cache.AbstractUpdateTask;
import com.gillsoft.model.response.ScheduleResponse;
import com.gillsoft.util.ContextProvider;

public class ScheduleUpdateTask extends AbstractUpdateTask {

	private static final long serialVersionUID = 1792318881958028832L;

	public ScheduleUpdateTask() {
		
	}
	
	@Override
	public void run() {
		RestClient client = ContextProvider.getBean(RestClient.class);
		try {
			ScheduleResponse schedule = client.getSchedule();
			writeObjectIgnoreAge(client.getCache(), RestClient.SCHEDULE_CACHE_KEY,
					schedule, Config.getCacheScheduleUpdateDelay());
		} catch (ResponseError e) {
			writeObject(client.getCache(), RestClient.SCHEDULE_CACHE_KEY,
					e, Config.getCacheErrorTimeToLive(), Config.getCacheErrorUpdateDelay());
		}
	}

}

package com.gillsoft;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.gillsoft.abstract_rest_service.AbstractScheduleService;
import com.gillsoft.cache.IOCacheException;
import com.gillsoft.client.RestClient;
import com.gillsoft.model.request.ScheduleRequest;
import com.gillsoft.model.response.ScheduleResponse;

@RestController
public class ScheduleServiceController extends AbstractScheduleService {
	
	@Autowired
	private RestClient client;

	@Override
	public ScheduleResponse getScheduleResponse(ScheduleRequest request) {
		ScheduleResponse schedule = null;
		boolean cacheError = true;
		do {
			try {
				schedule = client.getCachedSchedule();
				cacheError = false;
			} catch (IOCacheException e) {
				try {
					TimeUnit.MILLISECONDS.sleep(100);
				} catch (InterruptedException ie) {
				}
			}
		} while (cacheError);
		return schedule;
	}

}

package com.gillsoft;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

import com.gillsoft.abstract_rest_service.AbstractLocalityService;
import com.gillsoft.cache.IOCacheException;
import com.gillsoft.client.City;
import com.gillsoft.client.RestClient;
import com.gillsoft.model.Lang;
import com.gillsoft.model.Locality;
import com.gillsoft.model.request.LocalityRequest;
import com.gillsoft.util.RestTemplateUtil;

@RestController
public class LocalityServiceController extends AbstractLocalityService {
	
	public static List<Locality> all;
	
	@Autowired
	private RestClient client;

	@Override
	public List<Locality> getAllResponse(LocalityRequest arg0) {
		createLocalities();
		return all;
	}

	@Override
	public Map<String, List<String>> getBindingResponse(LocalityRequest arg0) {
		throw RestTemplateUtil.createUnavailableMethod();
	}

	@Override
	public List<Locality> getUsedResponse(LocalityRequest arg0) {
		createLocalities();
		return all;
	}
	
	@Scheduled(initialDelay = 60000, fixedDelay = 900000)
	public void createLocalities() {
		if (LocalityServiceController.all == null) {
			synchronized (LocalityServiceController.class) {
				if (LocalityServiceController.all == null) {
					Map<String, Locality> localities = new HashMap<>();
					for (Lang lang : Lang.values()) {
						addLocalities(lang, localities);
					}
					List<Locality> all = new CopyOnWriteArrayList<>();
					all.addAll(localities.values());
					LocalityServiceController.all = all;
				}
			}
		}
	}
	
	private void addLocalities(Lang lang, Map<String, Locality> localities) {
		boolean cacheError = true;
		do {
			try {
				List<City> cities = client.getCachedCities(lang.toString().toLowerCase());
				if (cities != null) {
					List<Locality> all = new CopyOnWriteArrayList<>();
					for (City city : cities) {
						String key = String.valueOf(city.getId());
						Locality locality = localities.get(key);
						if (locality == null) {
							locality = new Locality();
							locality.setId(key);
							locality.setLatitude(getDecimal(city.getLatitude()));
							locality.setLongitude(getDecimal(city.getLongitude()));
							localities.put(key, locality);
						}
						locality.setName(lang, city.getName());
						all.add(locality);
					}
				}
				cacheError = false;
			} catch (IOCacheException e) {
				try {
					TimeUnit.MILLISECONDS.sleep(100);
				} catch (InterruptedException ie) {
				}
			}
		} while (cacheError);
	}
	
	private BigDecimal getDecimal(float value) {
		try {
			return new BigDecimal(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}

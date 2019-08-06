package com.gillsoft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import com.gillsoft.model.ScheduleRoute;
import com.gillsoft.model.ScheduleRoutePoint;
import com.gillsoft.model.request.LocalityRequest;
import com.gillsoft.model.response.ScheduleResponse;

@RestController
public class LocalityServiceController extends AbstractLocalityService {
	
	public static List<Locality> all;
	public static List<Locality> used;
	
	@Autowired
	private RestClient client;
	
	@Autowired
	private ScheduleServiceController controller;

	@Override
	public List<Locality> getAllResponse(LocalityRequest request) {
		createLocalities();
		return all;
	}

	@Override
	public Map<String, List<String>> getBindingResponse(LocalityRequest request) {
		Map<String, List<String>> binding = new HashMap<>();
		ScheduleResponse response = controller.getScheduleResponse(null);
		if (response != null
				&& response.getRoutes() != null) {
			List<ScheduleRoute> routes = response.getRoutes();
			for (ScheduleRoute route : routes) {
				for (int i = 0; i < route.getPath().size() - 1; i++) {
					ScheduleRoutePoint point = (ScheduleRoutePoint) route.getPath().get(i);
					if (point.getDestinations() != null) {
						List<String> tos = binding.get(point.getLocality().getParent().getId());
						if (tos == null) {
							tos = new ArrayList<>();
							binding.put(point.getLocality().getParent().getId(), tos);
						}
						for (ScheduleRoutePoint dest : point.getDestinations()) {
							String id = route.getPath().get(dest.getIndex()).getLocality().getParent().getId();
							if (!tos.contains(id)) {
								tos.add(id);
							}
						}
					}
				}
			}
		}
		return binding;
	}

	@Override
	public List<Locality> getUsedResponse(LocalityRequest request) {
		createLocalities();
		return used;
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
					ScheduleResponse response = controller.getScheduleResponse(null);
					if (response != null
							&& response.getParents() != null) {
						response.getParents().forEach((id, l) -> l.setId(id));
						used = new CopyOnWriteArrayList<>(response.getParents().values());
					}
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
							locality.setLatitude(city.getLatitude());
							locality.setLongitude(city.getLongitude());
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
	
	public static Locality getLocality(String id) {
		if (all == null) {
			return null;
		}
		for (Locality locality : all) {
			if (Objects.equals(id, locality.getId())) {
				return locality;
			}
		}
		return null;
	}

}

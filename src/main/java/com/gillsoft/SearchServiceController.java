package com.gillsoft;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import com.gillsoft.abstract_rest_service.SimpleAbstractTripSearchService;
import com.gillsoft.cache.CacheHandler;
import com.gillsoft.cache.IOCacheException;
import com.gillsoft.client.Discount;
import com.gillsoft.client.Parameters;
import com.gillsoft.client.PathPoint;
import com.gillsoft.client.Point;
import com.gillsoft.client.ResponseError;
import com.gillsoft.client.RestClient;
import com.gillsoft.client.ReturnRule;
import com.gillsoft.client.RouteInfo;
import com.gillsoft.client.Trip;
import com.gillsoft.client.TripIdModel;
import com.gillsoft.model.Currency;
import com.gillsoft.model.Document;
import com.gillsoft.model.Lang;
import com.gillsoft.model.Locality;
import com.gillsoft.model.Organisation;
import com.gillsoft.model.Price;
import com.gillsoft.model.RequiredField;
import com.gillsoft.model.RestError;
import com.gillsoft.model.ReturnCondition;
import com.gillsoft.model.Route;
import com.gillsoft.model.RoutePoint;
import com.gillsoft.model.RouteType;
import com.gillsoft.model.Seat;
import com.gillsoft.model.SeatStatus;
import com.gillsoft.model.SeatType;
import com.gillsoft.model.SeatsScheme;
import com.gillsoft.model.Segment;
import com.gillsoft.model.SimpleTripSearchPackage;
import com.gillsoft.model.Tariff;
import com.gillsoft.model.TripContainer;
import com.gillsoft.model.Vehicle;
import com.gillsoft.model.request.TripSearchRequest;
import com.gillsoft.model.response.TripSearchResponse;
import com.gillsoft.util.RestTemplateUtil;
import com.gillsoft.util.StringUtil;
import com.google.common.base.Objects;

@RestController
public class SearchServiceController extends SimpleAbstractTripSearchService<SimpleTripSearchPackage<List<Trip>>> {
	
	@Autowired
	private RestClient client;
	
	@Autowired
	@Qualifier("MemoryCacheHandler")
	private CacheHandler cache;

	@Override
	public TripSearchResponse initSearchResponse(TripSearchRequest request) {
		return simpleInitSearchResponse(cache, request);
	}
	
	@Override
	public void addInitSearchCallables(List<Callable<SimpleTripSearchPackage<List<Trip>>>> callables,
			TripSearchRequest request) {
		callables.add(() -> {
			SimpleTripSearchPackage<List<Trip>> searchPackage = new SimpleTripSearchPackage<>();
			searchPackage.setRequest(request);
			searchTrips(searchPackage);
			return searchPackage;
		});
	}
	
	private void searchTrips(SimpleTripSearchPackage<List<Trip>> searchPackage) {
		searchPackage.setInProgress(false);
		try {
			TripSearchRequest request = searchPackage.getRequest();
			
			List<Trip> trips = client.getCachedTrips(request.getLocalityPairs().get(0)[0], request.getLocalityPairs().get(0)[1],
					request.getDates().get(0), client.getCurrency(request.getCurrency()));
			searchPackage.setSearchResult(new CopyOnWriteArrayList<Trip>());
			searchPackage.getSearchResult().addAll(trips);
			for (Trip trip : trips) {
				if (trip.getRouteInfo() == null) {
					try {
						trip.setRouteInfo(client.getCachedRoute(String.valueOf(trip.getRouteId())));
					} catch (IOCacheException e) {
						searchPackage.setInProgress(true);
					} catch (ResponseError e) {
					}
				}
				if (trip.getReturnRules() == null) {
					for (Lang lang : Lang.values()) {
						try {
							trip.addReturnRules(lang, client.getCachedReturnRules(trip.getIntervalId(),
									lang.toString().toLowerCase(), getDate(trip.getDepartDate(), trip.getDepartTime())));
						} catch (IOCacheException e) {
							searchPackage.setInProgress(true);
						} catch (ResponseError e) {
						}
					}
				}
			}
		} catch (IOCacheException e) {
			searchPackage.setInProgress(true);
		} catch (ResponseError e) {
			searchPackage.setException(e);
		}
	}

	@Override
	public TripSearchResponse getSearchResultResponse(String searchId) {
		return simpleGetSearchResponse(cache, searchId);
	}
	
	@Override
	public void addNextGetSearchCallablesAndResult(List<Callable<SimpleTripSearchPackage<List<Trip>>>> callables,
			Map<String, Vehicle> vehicles, Map<String, Locality> localities, Map<String, Organisation> organisations,
			Map<String, Segment> segments, List<TripContainer> containers,
			SimpleTripSearchPackage<List<Trip>> result) {
		if (!result.isInProgress()) {
			addResult(vehicles, localities, organisations, segments, containers, result);
		} else {
			callables.add(() -> {
				searchTrips(result);
				return result;
			});
		}
	}
	
	private void addResult(Map<String, Vehicle> vehicles, Map<String, Locality> localities,
			Map<String, Organisation> organisations, Map<String, Segment> segments, List<TripContainer> containers,
			SimpleTripSearchPackage<List<Trip>> result) {
		TripContainer container = new TripContainer();
		container.setRequest(result.getRequest());
		if (result.getSearchResult() != null) {
			List<com.gillsoft.model.Trip> trips = new ArrayList<>();
			for (int i = result.getSearchResult().size() - 1; i >= 0; i--) {
				Trip trip = result.getSearchResult().get(i);
				if (trip.getRouteInfo() != null) {
					
					com.gillsoft.model.Trip resTrip = new com.gillsoft.model.Trip();
					resTrip.setId(addSegment(vehicles, localities, organisations, segments, trip, result.getRequest()));
					trips.add(resTrip);
					
					result.getSearchResult().remove(i);
				}
			}
			container.setTrips(trips);
		}
		if (result.getException() != null) {
			container.setError(new RestError(result.getException().getMessage()));
		}
		containers.add(container);
	}
	
	private String addSegment(Map<String, Vehicle> vehicles, Map<String, Locality> localities,
			Map<String, Organisation> organisations, Map<String, Segment> segments, Trip trip, TripSearchRequest request) {
		Segment segment = new Segment();
		segment.setNumber(trip.getRouteCode());
		segment.setDepartureDate(getDate(trip.getDepartDate(), trip.getDepartTime()));
		segment.setArrivalDate(getDate(trip.getArriveDate(), trip.getArriveTime()));
		segment.setFreeSeatsCount(trip.getFreeSeats().getCount());
		segment.setTimeInWay(trip.getTimeInWay());
		
		segment.setDeparture(createLocality(localities, trip.getDepartCityId(), trip.getDepartStationId(), null, trip.getDepartStation()));
		segment.setArrival(createLocality(localities, trip.getArriveCityId(), trip.getArriveStationId(), null, trip.getArriveStation()));
		
		segment.setCarrier(addOrganisation(organisations, trip.getCarrierCode(), trip.getCarrierName()));
		segment.setVehicle(addVehicle(vehicles, trip.getBusNumber()));
		
		segment.setPrice(createPrice(trip));
		segment.setRoute(createRoute(trip.getRouteInfo(), trip.getRouteId(), localities));
		
		TripIdModel id = new TripIdModel(trip.getIntervalId(), trip.getRouteId(), request.getLocalityPairs().get(0)[0],
				request.getLocalityPairs().get(0)[1], request.getDates().get(0), client.getCurrency(request.getCurrency()));
		String key = id.asString();
		segments.put(key, segment);
		
		return key;
	}
	
	private Date getDate(Date date, String time) {
		try {
			return StringUtil.fullDateFormat.parse(StringUtil.dateFormat.format(date) + " " + time);
		} catch (ParseException e) {
			return null;
		}
	}
	
	public Locality createLocality(Map<String, Locality> localities, int parentId, int id, String name, String address) {
		String key = String.valueOf(id);
		Locality station = new Locality();
		station.setName(name);
		station.setAddress(address);
		
		Locality parent = LocalityServiceController.getLocality(String.valueOf(parentId));
		station.setParent(parent);
		Locality locality = localities.get(key);
		if (locality == null) {
			localities.put(key, station);
		}
		return new Locality(key);
	}
	
	public Organisation addOrganisation(Map<String, Organisation> organisations, String code, String name) {
		if (code == null
				&& name == null) {
			return null;
		}
		String key = StringUtil.md5(code);
		Organisation organisation = organisations.get(key);
		if (organisation == null) {
			organisation = new Organisation();
			organisation.setTradeMark(code);
			organisation.setName(name);
			organisations.put(key, organisation);
		}
		return new Organisation(key);
	}
	
	public Vehicle addVehicle(Map<String, Vehicle> vehicles, String number) {
		if (number == null) {
			return null;
		}
		String key = StringUtil.md5(number);
		Vehicle vehicle = vehicles.get(key);
		if (vehicle == null) {
			vehicle = new Vehicle();
			vehicle.setNumber(number);
			vehicles.put(key, vehicle);
		}
		return new Vehicle(key);
	}
	
	private Price createPrice(Trip trip) {
		
		// тариф
		Tariff tariff = new Tariff();
		tariff.setId("0");
		tariff.setValue(trip.getTariff());
		
		// условия возврата
		if (trip.getReturnRules() != null) {
			tariff.setReturnConditions(createReturnConditions(trip.getReturnRules()));
		} else if (trip.getRouteInfo() != null) {
			tariff.setReturnConditions(createReturnConditions(trip.getRouteInfo().getRoute().getReturnPolicy()));
		}
		// стоимость
		Price price = new Price();
		price.setCurrency(Currency.valueOf(trip.getCurrency()));
		price.setAmount(trip.getPrice().getOneWay());
		price.setTariff(tariff);
		return price;
	}
	
	private Route createRoute(RouteInfo routeInfo, String routeId, Map<String, Locality> localities) {
		if (routeInfo == null) {
			return null;
		}
		Route route = new Route();
		route.setId(routeId);
		route.setName(routeInfo.getRoute().getCode());
		try {
			route.setType(RouteType.valueOf(routeInfo.getRoute().getType().toUpperCase()));
		} catch (Exception e) {
		}
		List<RoutePoint> path = new ArrayList<>(routeInfo.getRoute().getPath().size());
		for (PathPoint point : routeInfo.getRoute().getPath()) {
			RoutePoint routePoint = new RoutePoint();
			routePoint.setDistance(point.getDistance());
			routePoint.setPlatform(point.getPlatform());
			routePoint.setArrivalDay(point.getArriveDay());
			routePoint.setDepartureTime(point.getDepartTime());
			routePoint.setArrivalTime(point.getArriveTime());
			routePoint.setLocality(createRouteLocality(localities, point.getGeo().getPoint(), point.getGeo().getLocality().getId()));
			path.add(routePoint);
		}
		route.setPath(path);
		return route;
	}
	
	private Locality createRouteLocality(Map<String, Locality> localities, Point point, int parentId) {
		String key = String.valueOf(point.getId());
		Locality station = new Locality();
		station.setId(String.valueOf(point.getId()));
		station.setLatitude(point.getLatitude());
		station.setLongitude(point.getLongitude());
		for (Entry<String, Parameters> entry : point.getI18n().entrySet()) {
			try {
				Lang lang = Lang.valueOf(entry.getKey().toUpperCase());
				station.setName(lang, entry.getValue().getName());
				station.setAddress(lang, entry.getValue().getAddress());
			} catch (Exception e) {
			}
		}
		Locality parent = LocalityServiceController.getLocality(String.valueOf(parentId));
		station.setParent(parent);
		if (localities == null) {
			return station;
		}
		localities.put(key, station);
		return new Locality(key);
	}

	@Override
	public Route getRouteResponse(String tripId) {
		TripIdModel idModel = new TripIdModel().create(tripId);
		try {
			RouteInfo route = client.getCachedRoute(String.valueOf(idModel.getRouteId()));
			return createRoute(route, idModel.getRouteId(), null);
		} catch (IOCacheException | ResponseError e) {
			throw new RestClientException(e.getMessage());
		}
	}

	@Override
	public SeatsScheme getSeatsSchemeResponse(String tripId) {
		TripIdModel idModel = new TripIdModel().create(tripId);
		try {
			List<List<com.gillsoft.client.Seat>> seatsMap = client.getSeatsMap(idModel.getIntervalId());
			SeatsScheme seatsScheme = new SeatsScheme();
			seatsScheme.setScheme(new HashMap<>());
			
			// список ид мест
			// первый list строки, второй - колонки
			List<List<String>> scheme = new ArrayList<>();
			for (List<com.gillsoft.client.Seat> seats : seatsMap) {
				List<String> ids = new ArrayList<>();
				for (com.gillsoft.client.Seat seat : seats) {
					ids.add(seat.getId() == null ? seatsMap.indexOf(seats) + "_" + seats.indexOf(seat) : seat.getId());
				}
				scheme.add(ids);
			}
			seatsScheme.getScheme().put(1, scheme);
			return seatsScheme;
		} catch (ResponseError e) {
			throw new RestClientException(e.getMessage());
		}
	}

	@Override
	public List<Seat> getSeatsResponse(String tripId) {
		TripIdModel idModel = new TripIdModel().create(tripId);
		try {
			List<List<com.gillsoft.client.Seat>> seatsMap = client.getSeatsMap(idModel.getIntervalId());
			List<Seat> newSeats = new ArrayList<>(seatsMap.size());
			for (List<com.gillsoft.client.Seat> seats : seatsMap) {
				for (com.gillsoft.client.Seat seat : seats) {
					Seat newSeat = new Seat();
					newSeat.setType(getSeatType(seat.getType()));
					newSeat.setId(seat.getId() == null ? seatsMap.indexOf(seats) + "_" + seats.indexOf(seat) : seat.getId());
					newSeat.setNumber(seat.getNumber());
					newSeat.setStatus(getSeatStatus(seat.getStatus()));
					newSeats.add(newSeat);
				}
			}
			return newSeats;
		} catch (ResponseError e) {
			throw new RestClientException(e.getMessage());
		}
	}
	
	private SeatStatus getSeatStatus(String status) {
		if (status == null) {
			return SeatStatus.EMPTY;
		}
		switch (status) {
		case "disable":
			return SeatStatus.SALED;
		case "enable":
			return SeatStatus.FREE;
		default:
			return SeatStatus.EMPTY;
		}
	}
	
	private SeatType getSeatType(String type) {
		if (type == null) {
			return SeatType.FLOOR;
		}
		switch (type) {
		case "seat":
			return SeatType.SEAT;
		case "not seat":
			return SeatType.EXIT;
		default:
			return SeatType.FLOOR;
		}
	}

	@Override
	public List<Tariff> getTariffsResponse(String tripId) {
		Trip trip = getTripFromCache(tripId);
		if (trip != null) {
			List<Tariff> tariffs = new ArrayList<>();
			for (Discount discount : trip.getDiscounts()) {
				Tariff tariff = new Tariff();
				tariff.setId(String.valueOf(discount.getId()));
				tariff.setMinAge(discount.getLimitFrom());
				tariff.setMaxAge(discount.getLimitTo());
				tariff.setCode(discount.getKind());
				for (Lang lang : Lang.values()) {
					Parameters parameters = discount.getI18n().get(lang.toString().toLowerCase());
					if (parameters != null) {
						tariff.setName(lang, parameters.getName());
						tariff.setDescription(lang, parameters.getDescription());
					}
				}
				tariff.setValue(trip.getTariff()
						.multiply(new BigDecimal(100).subtract(discount.getValue())
								.multiply(new BigDecimal("0.01"))));
				tariffs.add(tariff);
			}
			Tariff tariff = new Tariff();
			tariff.setId("0");
			tariff.setCode("base");
			tariff.setValue(trip.getTariff());
			tariffs.add(tariff);
			return tariffs;
		}
		return null;
	}

	@Override
	public List<RequiredField> getRequiredFieldsResponse(String tripId) {
		Trip trip = getTripFromCache(tripId);
		if (trip != null) {
			List<RequiredField> fields = new ArrayList<>();
			for (Entry<String, Boolean> field : trip.getDocFields().entrySet()) {
				if (field.getValue()) {
					RequiredField required = getRequiredField(field.getKey());
					if (required != null) {
						fields.add(required);
					}
				}
			}
			if (trip.isInternational()) {
				fields.add(RequiredField.ONLY_LATIN);
			}
			fields.add(RequiredField.SEAT);
			fields.add(RequiredField.TARIFF);
			return fields;
		}
		return null;
	}
	
	public Trip getTripFromCache(String tripId) {
		TripIdModel idModel = new TripIdModel().create(tripId);
		try {
			List<Trip> trips = client.getCachedTrips(idModel.getFrom(), idModel.getTo(),
					idModel.getDate(), idModel.getCurrency());
			for (Trip trip : trips) {
				if (Objects.equal(trip.getIntervalId(), idModel.getIntervalId())) {
					return trip;
				}
			}
		} catch (IOCacheException e) {
		} catch (ResponseError e) {
			throw new RestClientException(e.getMessage());
		}
		return null;
	}
	
	private RequiredField getRequiredField(String field) {
		switch (field) {
		case "email":
			return RequiredField.EMAIL;
		case "phone":
			return RequiredField.PHONE;
		case "name":
			return RequiredField.NAME;
		case "surname":
			return RequiredField.SURNAME;
		case "birth_date":
			return RequiredField.BIRTHDAY;
		case "doc_type":
			return RequiredField.DOCUMENT_TYPE;
		case "doc_number":
			return RequiredField.DOCUMENT_NUMBER;
		default:
			return null;
		}
	}

	@Override
	public List<Seat> updateSeatsResponse(String tripId, List<Seat> seats) {
		throw RestTemplateUtil.createUnavailableMethod();
	}

	@Override
	public List<ReturnCondition> getConditionsResponse(String tripId, String tariffId) {
		TripIdModel idModel = new TripIdModel().create(tripId);
		return getReturnConditions(idModel.getIntervalId());
	}
	
	public List<ReturnCondition> getReturnConditions(String intervalId) {
		RestClientException exception = null;
		Map<Lang, List<ReturnRule>> rules = new HashMap<>();
		for (Lang lang : Lang.values()) {
			try {
				rules.put(lang, client.getCachedReturnRules(intervalId, lang.toString().toLowerCase(), null));
			} catch (IOCacheException e) {
			} catch (ResponseError e) {
				exception = new RestClientException(e.getMessage());
			}
		}
		if (!rules.isEmpty()) {
			return createReturnConditions(rules);
		}
		if (exception != null) {
			throw exception;
		}
		return null;
	}
	
	private List<ReturnCondition> createReturnConditions(Map<Lang, List<ReturnRule>> rules) {
		Map<Integer, ReturnCondition> returnConditions = new HashMap<>(rules.size());
		for (Entry<Lang, List<ReturnRule>> returnRules : rules.entrySet()) {
			for (ReturnRule rule : returnRules.getValue()) {
				ReturnCondition condition = returnConditions.get(rule.getMinutesBeforeDepart());
				if (condition == null) {
					condition = new ReturnCondition();
					returnConditions.put(rule.getMinutesBeforeDepart(), condition);
				}
				condition.setTitle(returnRules.getKey(), rule.getTitle());
				condition.setDescription(returnRules.getKey(), rule.getDescription());
				condition.setMinutesBeforeDepart(rule.getMinutesBeforeDepart());
				condition.setReturnPercent(new BigDecimal(rule.getValue()));
			}
		}
		return new ArrayList<>(returnConditions.values());
	}
	
	private List<ReturnCondition> createReturnConditions(String returnPolicy) {
		if (returnPolicy == null) {
			return null;
		}
		List<ReturnCondition> returnConditions = new ArrayList<>();
		String[] policies = returnPolicy.split("((<|</|)\\w+>)");
		for (String policy : policies) {
			policy = policy.replaceAll("(&\\w+;)", "").trim();
			if (!policy.isEmpty()) {
				ReturnCondition condition = new ReturnCondition();
				condition.setDescription(policy);
				returnConditions.add(condition);
			}
		}
		return returnConditions;
	}

	@Override
	public List<Document> getDocumentsResponse(String tripId) {
		throw RestTemplateUtil.createUnavailableMethod();
	}

}

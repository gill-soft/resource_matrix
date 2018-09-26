package com.gillsoft.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.gillsoft.cache.CacheHandler;
import com.gillsoft.cache.IOCacheException;
import com.gillsoft.cache.RedisMemoryCache;
import com.gillsoft.logging.SimpleRequestResponseLoggingInterceptor;
import com.gillsoft.model.Currency;
import com.gillsoft.model.Customer;
import com.gillsoft.model.ServiceItem;
import com.gillsoft.model.response.ScheduleResponse;
import com.gillsoft.util.RestTemplateUtil;
import com.gillsoft.util.StringUtil;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class RestClient {
	
	public static final String CITIES_CACHE_KEY = "matrix.cities.";
	public static final String ROUTE_CACHE_KEY = "matrix.route.";
	public static final String RULE_CACHE_KEY = "matrix.rule.";
	public static final String TRIPS_CACHE_KEY = "matrix.trips";
	public static final String SCHEDULE_CACHE_KEY = "matrix.schedule";
	
	public static final String STATUS_CANCEL = "cancel";
	public static final String STATUS_BOOKING = "booking";
	public static final String STATUS_NEW = "new";
	public static final String STATUS_BUY = "buyout";
	public static final String STATUS_ANNULMENT = "annulment";
	public static final String STATUS_RETURNED = "returned";
	
	private static final String PING = "/get/ping";
	private static final String CURRENCIES = "/get/currency-list";
	private static final String CITIES = "/get/cities";
	private static final String TRIPS = "/get/trips";
	private static final String RULES = "/get/trip/return-rules";
	private static final String ROUTE = "/get/route-info";
	private static final String SEATS_MAP = "/get/seatsMap";
	private static final String FREE_SEATS = "/get/freeSeats";
	private static final String NEW_ORDER = "/order/new";
	private static final String RESERVE = "/order/reserve";
	private static final String BUY = "/order/buy";
	private static final String INFO = "/order/info";
	private static final String CANCEL = "/order/cancel";
	private static final String ANNULMENT = "/order/annulment";
	private static final String TICKET_AUTO_RETURN = "/ticket/auto-return";
	private static final String TICKET_AUTO_RETURN_PRICE = "/ticket/auto-return-price";
	private static final String PRINT_TICKETS = "/order/print/{0}";
	
	private static final String DEFAULT_LOCALE = "en";
	
	@Autowired
    @Qualifier("RedisMemoryCache")
	private CacheHandler cache;
	
	private RestTemplate template;
	
	// для запросов поиска с меньшим таймаутом
	private RestTemplate searchTemplate;
	
	public RestClient() {
		template = createNewPoolingTemplate(Config.getRequestTimeout());
		searchTemplate = createNewPoolingTemplate(Config.getSearchRequestTimeout());
	}
	
	public RestTemplate createNewPoolingTemplate(int requestTimeout) {
		RestTemplate template = new RestTemplate(new BufferingClientHttpRequestFactory(
				RestTemplateUtil.createPoolingFactory(Config.getUrl(), 300, requestTimeout)));
		template.setInterceptors(Collections.singletonList(
				new SimpleRequestResponseLoggingInterceptor()));
		template.setErrorHandler(new RestTemplateResponseErrorHandler());
		return template;
	}
	
	public boolean ping() {
		try {
			Response<Object> response = sendRequest(searchTemplate, PING, HttpMethod.GET, null, createLoginParams(DEFAULT_LOCALE),
					new ParameterizedTypeReference<Response<Object>>() {});
			return response.isStatus();
		} catch (ResponseError e) {
			return false;
		}
	}
	
	public List<City> getCities(String locale) throws ResponseError {
		Response<List<City>> response = sendRequest(searchTemplate, CITIES, HttpMethod.GET, null, createLoginParams(locale),
				new ParameterizedTypeReference<Response<List<City>>>() {});
		return response.getData();
	}
	
	public List<City> getCachedCities(String locale) throws IOCacheException {
		try {
			return getCachedObject(getCitiesCacheKey(locale), new CitiesUpdateTask(locale));
		} catch (ResponseError e) {
			return null;
		}
	}
	
	public List<Trip> getTrips(String departLocality, String arriveLocality, Date departDate, String currency) throws ResponseError {
		return getTrips(getTripSearchParams(departLocality, arriveLocality, departDate, currency));
	}
	
	public List<Trip> getTrips(MultiValueMap<String, String> params) throws ResponseError {
		return sendRequest(searchTemplate, TRIPS, HttpMethod.POST, null, params,
				new ParameterizedTypeReference<Response<List<Trip>>>() {}).getData();
	}
	
	private MultiValueMap<String, String> getTripSearchParams(String departLocality, String arriveLocality,
			Date departDate, String currency) throws ResponseError {
		MultiValueMap<String, String> params = createLoginParams(null);
		params.add("depart_locality", departLocality);
		params.add("arrive_locality", arriveLocality);
		params.add("depart_date", StringUtil.dateFormat.format(departDate));
		params.add("with_empty_seats", "false");
		params.add("currency", currency);
		params.add("unique_trip", "true");
		return params;
	}
	
	private Set<String> currencies = new HashSet<>();
	
	public String getCurrency(Currency currency) {
		if (currencies.isEmpty()) {
			try {
				currencies.addAll(sendRequest(searchTemplate, CURRENCIES, HttpMethod.GET, null, createLoginParams(DEFAULT_LOCALE),
						new ParameterizedTypeReference<Response<Map<String, String>>>() {}).getData().keySet());
			} catch (ResponseError e) {
			}
		}
		if (currency == null) {
			return currencies.iterator().next();
		}
		if (currencies.contains(currency.toString())) {
			return currency.toString();
		}
		return currencies.iterator().next();
	}
	
	public List<Trip> getCachedTrips(String departLocality, String arriveLocality, Date departDate, String currency)
			throws IOCacheException, ResponseError {
		MultiValueMap<String, String> params = getTripSearchParams(departLocality, arriveLocality, departDate, currency);
		return getCachedObject(getCacheKey(TRIPS_CACHE_KEY, params), new TripsUpdateTask(params));
	}
	
	public RouteInfo getRoute(String routeId) throws ResponseError {
		return getRoute(getRouteParams(routeId));
	}
	
	public RouteInfo getRoute(MultiValueMap<String, String> params) throws ResponseError {
		return sendRequest(searchTemplate, ROUTE, HttpMethod.POST, null, params,
				new ParameterizedTypeReference<Response<RouteInfo>>() {}).getData();
	}
	
	private MultiValueMap<String, String> getRouteParams(String routeId) {
		MultiValueMap<String, String> params = createLoginParams(null);
		params.add("route_id", routeId);
		return params;
	}
	
	public RouteInfo getCachedRoute(String routeId) throws ResponseError, IOCacheException {
		MultiValueMap<String, String> params = getRouteParams(routeId);
		return getCachedObject(getCacheKey(ROUTE_CACHE_KEY, params), new RouteUpdateTask(params));
	}
	
	public Map<String, String> getFreeSeats(String intervalId) throws ResponseError {
		return sendRequest(searchTemplate, FREE_SEATS, HttpMethod.POST, null, getIntervalParams(intervalId, null),
				new ParameterizedTypeReference<Response<Map<String, String>>>() {}).getData();
	}
	
	public List<List<Seat>> getSeatsMap(String intervalId) throws ResponseError {
		return sendRequest(searchTemplate, SEATS_MAP, HttpMethod.POST, null, getIntervalParams(intervalId, null),
				new ParameterizedTypeReference<Response<List<List<Seat>>>>() {}).getData();
	}
	
	private MultiValueMap<String, String> getIntervalParams(String intervalId, String locale) {
		MultiValueMap<String, String> params = createLoginParams(locale);
		params.add("interval_id", intervalId);
		return params;
	}
	
	public List<ReturnRule> getReturnRules(String intervalId, String locale) throws ResponseError {
		return getReturnRules(getIntervalParams(intervalId, locale));
	}
	
	public List<ReturnRule> getCachedReturnRules(String intervalId, String locale, Date tripStart) throws ResponseError, IOCacheException {
		MultiValueMap<String, String> params = getIntervalParams(intervalId, locale);
		return getCachedObject(getCacheKey(RULE_CACHE_KEY, params), tripStart == null ? null : new ReturnRuleUpdateTask(params, tripStart));
	}
	
	public List<ReturnRule> getReturnRules(MultiValueMap<String, String> params) throws ResponseError {
		return sendRequest(searchTemplate, RULES, HttpMethod.POST, null, params,
				new ParameterizedTypeReference<Response<List<ReturnRule>>>() {}).getData();
	}
	
	public Order newOrder(String intervalId, Currency currency, Map<String, Customer> customers,
			List<ServiceItem> services) throws ResponseError {
		MultiValueMap<String, String> params = createLoginParams(null);
		params.add("interval_id[0]", intervalId);
		params.add("currency", getCurrency(currency));
		params.add("with_fees", "1");
		boolean contactsAdded = false;
		for (int i = 0; i < services.size(); i++) {
			ServiceItem service = services.get(i);
			Customer customer = customers.get(service.getCustomer().getId());
			if (!contactsAdded) {
				params.add("email", customer.getEmail());
				params.add("phone", customer.getPhone());
				contactsAdded = true;
			}
			params.add("name[" + i + "]", customer.getName());
			params.add("surname[" + i + "]", customer.getSurname());
			if (!Objects.equals("0", service.getPrice().getTariff().getId())) {
				params.add("discount_id[0][" + i + "]", service.getPrice().getTariff().getId());
			}
			params.add("seat[0][" + i + "]", service.getSeat().getId());
		}
		return sendRequest(template, NEW_ORDER, HttpMethod.POST, null, params,
				new ParameterizedTypeReference<Response<Order>>() {}).getData();
	}
	
	private MultiValueMap<String, String> getOrderParams(String orderId, String description) {
		MultiValueMap<String, String> params = createLoginParams(null);
		params.add("order_id", orderId);
		params.add("description", description);
		params.add("with_fees", "true");
		return params;
	}
	
	public Order cancel(String orderId) throws ResponseError {
		return orderMethod(orderId, null, CANCEL);
	}
	
	public Order info(String orderId) throws ResponseError {
		return orderMethod(orderId, null, INFO);
	}
	
	public Order reserve(String orderId) throws ResponseError {
		return orderMethod(orderId, null, RESERVE);
	}
	
	public Order buy(String orderId) throws ResponseError {
		return orderMethod(orderId, null, BUY);
	}
	
	public Order annulate(String orderId, String description) throws ResponseError {
		return orderMethod(orderId, description, ANNULMENT);
	}
	
	private Order orderMethod(String orderId, String description, String method) throws ResponseError {
		return sendRequest(template, method, HttpMethod.POST, null, getOrderParams(orderId, description),
				new ParameterizedTypeReference<Response<Order>>() {}).getData();
	}
	
	private MultiValueMap<String, String> geTicketParams(String ticketId) {
		MultiValueMap<String, String> params = createLoginParams(null);
		params.add("ticket_id", ticketId);
		return params;
	}
	
	public Ticket autoReturnPrice(String ticketId) throws ResponseError {
		return ticketMethod(ticketId, TICKET_AUTO_RETURN_PRICE);
	}
	
	public Ticket autoReturn(String ticketId) throws ResponseError {
		return ticketMethod(ticketId, TICKET_AUTO_RETURN);
	}
	
	private Ticket ticketMethod(String ticketId, String method) throws ResponseError {
		return sendRequest(template, method, HttpMethod.POST, null, geTicketParams(ticketId),
				new ParameterizedTypeReference<Response<Ticket>>() {}).getData();
	}
	
	private MultiValueMap<String, String> createLoginParams(String locale) {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("login", Config.getLogin());
		params.add("password", Config.getPassword());
		params.add("locale", locale);
		return params;
	}
	
	@SuppressWarnings("unchecked")
	private <T> T getCachedObject(String key, Runnable task) throws IOCacheException, ResponseError {
		Map<String, Object> params = new HashMap<>();
		params.put(RedisMemoryCache.OBJECT_NAME, key);
		params.put(RedisMemoryCache.UPDATE_TASK, task);
		Object cached = cache.read(params);
		if (cached == null) {
			return null;
		}
		if (cached instanceof ResponseError) {
			throw (ResponseError) cached;
		}
		return (T) cached;
	}
	
	private <T> Response<T> sendRequest(RestTemplate template, String uriMethod, HttpMethod httpMethod, Object request,
			MultiValueMap<String, String> params, ParameterizedTypeReference<Response<T>> typeReference) throws ResponseError {
		URI uri = UriComponentsBuilder.fromUriString(Config.getUrl() + uriMethod).queryParams(params).build().toUri();
		RequestEntity<Object> requestEntity = new RequestEntity<>(request, httpMethod, uri);
		try {
			ResponseEntity<Response<T>> responseEntity = template.exchange(requestEntity, typeReference);
			if (responseEntity.getBody() == null) {
				throw new RestClientException("Empty response from resource.");
			}
			if ((responseEntity.getStatusCode() != HttpStatus.ACCEPTED
					&& responseEntity.getStatusCode() != HttpStatus.OK)
					|| !responseEntity.getBody().isStatus()) {
				throw new RestClientException(responseEntity.getBody().getError());
			}
			return responseEntity.getBody();
		} catch (RestClientException e) {
			throw new ResponseError(e.getMessage());
		}
	}
	
	public CacheHandler getCache() {
		return cache;
	}
	
	public static String getCitiesCacheKey(String locale) {
		return CITIES_CACHE_KEY + "." + locale;
	}
	
	public static String getCacheKey(String key, MultiValueMap<String, String> params) {
		List<String> values = new ArrayList<>();
		for (List<String> list : params.values()) {
			values.addAll(list.stream().filter(param -> param != null).collect(Collectors.toList()));
		}
		Collections.sort(values);
		values.add(0, key);
		return String.join(".", values);
	}
	
	public String getTickets(String orderId) throws ResponseError {
		URI uri = UriComponentsBuilder.fromUriString(Config.getUrl()
				+ MessageFormat.format(PRINT_TICKETS, orderId)).build().toUri();
		RequestEntity<Object> requestEntity = new RequestEntity<>(null, HttpMethod.GET, uri);
		ResponseEntity<Resource> response = template.exchange(requestEntity, Resource.class);
		try {
			InputStream in = response.getBody().getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			checkError(out.toByteArray());
			byte[] buffer = new byte[256];
			while (in.read(buffer) != -1) {
				out.write(buffer);
			}
			return StringUtil.toBase64(out.toByteArray());
		} catch (IOException e) {
			throw new ResponseError(e.getMessage());
		}
	}
	
	private void checkError(byte[] bytes) throws ResponseError {
		ObjectReader reader = new ObjectMapper().readerFor(new TypeReference<Response<String>>() { });
		try {
			Response<String> response = reader.readValue(bytes);
			if (response.getError() != null) {
				throw new ResponseError(response.getError());
			}
		} catch (IOException e) {
		}
	}
	
	public ScheduleResponse getSchedule() throws ResponseError {
		URI uri = UriComponentsBuilder.fromUriString(Config.getScheduleUrl()).build().toUri();
		RequestEntity<Object> requestEntity = new RequestEntity<>(HttpMethod.GET, uri);
		try {
			ResponseEntity<ScheduleResponse> responseEntity = template.exchange(requestEntity, ScheduleResponse.class);
			if (responseEntity.getBody() == null) {
				throw new RestClientException("Empty response from resource.");
			}
			return responseEntity.getBody();
		} catch (RestClientException e) {
			throw new ResponseError(e.getMessage());
		}
	}
	
	public ScheduleResponse getCachedSchedule() throws IOCacheException {
		try {
			return getCachedObject(SCHEDULE_CACHE_KEY, new ScheduleUpdateTask());
		} catch (ResponseError e) {
			return null;
		}
	}

}

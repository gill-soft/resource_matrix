package com.gillsoft.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.ParameterizedTypeReference;
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

import com.gillsoft.cache.CacheHandler;
import com.gillsoft.cache.IOCacheException;
import com.gillsoft.cache.RedisMemoryCache;
import com.gillsoft.logging.SimpleRequestResponseLoggingInterceptor;
import com.gillsoft.model.Currency;
import com.gillsoft.util.RestTemplateUtil;
import com.gillsoft.util.StringUtil;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class RestClient {
	
	public static final String COUNTRIES_CACHE_KEY = "matrix.countries.";
	public static final String CITIES_CACHE_KEY = "matrix.cities.";
	public static final String ROUTE_CACHE_KEY = "matrix.route.";
	public static final String RULE_CACHE_KEY = "matrix.rule.";
	public static final String TRIPS_CACHE_KEY = "matrix.trips";
	
	public static final String PING = "/get/ping";
	public static final String LOCALES = "/get/locales";
	public static final String CURRENCIES = "/get/currency-list";
	public static final String COUNTRIES = "/get/countries";
	public static final String CITIES = "/get/cities";
	public static final String TRIPS = "/get/trips";
	public static final String RULES = "/get/trip/return-rules";
	public static final String ROUTE = "/get/route-info";
	public static final String SEATS_MAP = "/get/seatsMap";
	public static final String FREE_SEATS = "/get/freeSeats";
	public static final String NEW_ORDER = "/order/new";
	public static final String RESERVE = "/order/reserve";
	public static final String BUY = "/order/buy";
	public static final String INFO = "/order/info";
	public static final String CANCEL = "/order/cancel";
	public static final String ANNULMENT = "/order/annulment";
	public static final String AUTO_RETURN = "/order/auto-return";
	public static final String RETURN = "/order/return";
	public static final String TICKET_AUTO_RETURN = "/ticket/auto-return";
	public static final String TICKET_AUTO_RETURN_PRICE = "/ticket/auto-return-price";
	public static final String TICKET_ANNULMENT = "/ticket/annulment";
	public static final String TICKET_RETURN = "/ticket/return";
	
	public static final String DEFAULT_LOCALE = "en";
	
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
			MultiValueMap<String, String> params,  ParameterizedTypeReference<Response<T>> typeReference) throws ResponseError {
		URI uri = UriComponentsBuilder.fromUriString(Config.getUrl() + uriMethod).queryParams(params).build().toUri();
		RequestEntity<Object> requestEntity = new RequestEntity<>(request, httpMethod, uri);
		try {
			ResponseEntity<Response<T>> responseEntity = template.exchange(requestEntity, typeReference);
			if (responseEntity.getBody() == null) {
				throw new RestClientException("Empty response from resource.");
			}
			if (responseEntity.getStatusCode() != HttpStatus.ACCEPTED
					&& responseEntity.getStatusCode() != HttpStatus.OK) {
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

}

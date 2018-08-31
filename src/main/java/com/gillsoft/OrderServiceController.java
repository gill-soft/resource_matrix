package com.gillsoft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.gillsoft.abstract_rest_service.AbstractOrderService;
import com.gillsoft.client.Fee;
import com.gillsoft.client.Order;
import com.gillsoft.client.OrderIdModel;
import com.gillsoft.client.Parameters;
import com.gillsoft.client.ResponseError;
import com.gillsoft.client.RestClient;
import com.gillsoft.client.Ticket;
import com.gillsoft.client.Trip;
import com.gillsoft.client.TripIdModel;
import com.gillsoft.model.CalcType;
import com.gillsoft.model.Commission;
import com.gillsoft.model.Currency;
import com.gillsoft.model.Customer;
import com.gillsoft.model.Lang;
import com.gillsoft.model.Locality;
import com.gillsoft.model.Organisation;
import com.gillsoft.model.Price;
import com.gillsoft.model.RestError;
import com.gillsoft.model.Seat;
import com.gillsoft.model.Segment;
import com.gillsoft.model.ServiceItem;
import com.gillsoft.model.Tariff;
import com.gillsoft.model.ValueType;
import com.gillsoft.model.request.OrderRequest;
import com.gillsoft.model.response.OrderResponse;
import com.gillsoft.util.RestTemplateUtil;
import com.gillsoft.util.StringUtil;

@RestController
public class OrderServiceController extends AbstractOrderService {
	
	@Autowired
	private RestClient client;
	
	@Autowired
	private SearchServiceController search;

	@Override
	public OrderResponse createResponse(OrderRequest request) {
		
		// формируем ответ
		OrderResponse response = createResponse(request.getCustomers());
		
		OrderIdModel idModel = new OrderIdModel();
		for (Entry<String, List<ServiceItem>> item : groupeByTripId(request).entrySet()) {
			TripIdModel tripId = new TripIdModel().create(item.getKey());
			try {
				List<Customer> customers = getOrderCustomers(item.getValue(), request.getCustomers());
				Order order = client.newOrder(tripId.getIntervalId(), item.getValue().get(0).getPrice().getCurrency(), customers);
				List<String> tickets = new ArrayList<>();
				idModel.getIds().put(order.getHash(), tickets);
				for (Ticket ticket : order.getTickets().get(tripId.getIntervalId())) {
					tickets.add(ticket.getHash());
				}
				addOrder(response, order, customers);
			} catch (ResponseError e) {
				for (ServiceItem serviceItem : item.getValue()) {
					serviceItem.setError(new RestError(e.getMessage()));
					response.getServices().add(serviceItem);
				}
			}
		}
		response.setId(idModel.asString());
		return response;
	}
	
	private OrderResponse createResponse(Map<String, Customer> customers) {
		
		// формируем ответ
		OrderResponse response = new OrderResponse();
		response.setCustomers(customers);
		
		Map<String, Locality> localities = new HashMap<>();
		Map<String, Organisation> organisations = new HashMap<>(); 
		Map<String, Segment> segments = new HashMap<>();
		
		List<ServiceItem> resultItems = new ArrayList<>();
		response.setLocalities(localities);
		response.setOrganisations(organisations);
		response.setSegments(segments);
		response.setServices(resultItems);
		return response;
	}
	
	private void addOrder(OrderResponse response, Order order, List<Customer> customers) {
		for (Entry<String, List<Ticket>> tickets : order.getTickets().entrySet()) {
			for (Ticket ticket : tickets.getValue()) {
				ServiceItem serviceItem = new ServiceItem();
				serviceItem.setId(ticket.getHash());
				serviceItem.setNumber(ticket.getNumber());
				serviceItem.setPrice(createPrice(ticket));
				serviceItem.setSeat(createSeat(ticket));
				
				Trip trip = search.getTripFromCache(tickets.getKey());
				TripIdModel id = null;
				if (trip != null) {
					id = new TripIdModel(tickets.getKey(), trip.getRouteId(), String.valueOf(trip.getDepartCityId()),
							String.valueOf(trip.getArriveCityId()), trip.getDepartDate(), trip.getCurrency());
				} else {
					id = new TripIdModel(tickets.getKey(), 0, String.valueOf(ticket.getGeoLocalityFrom()),
							String.valueOf(ticket.getGeoLocalityTo()), ticket.getDepartAt(), ticket.getCurrency());
				}
				String key = id.asString();
				serviceItem.setSegment(new Segment(key));
				addSegment(key, response.getLocalities(), response.getOrganisations(), response.getSegments(), ticket);
				
				// добовляем пассажира
				if (customers != null) {
					for (Customer customer : customers) {
						if (Objects.equals(customer.getName(), ticket.getPassName())
								&& Objects.equals(customer.getSurname(), ticket.getPassSurname())) {
							serviceItem.setCustomer(new Customer(customer.getId()));
							customers.remove(customer);
							break;
						}
					}
				} else {
					String uuid = StringUtil.generateUUID();
					serviceItem.setCustomer(new Customer(uuid));
					Customer customer = new Customer();
					customer.setName(ticket.getPassName());
					customer.setSurname(ticket.getPassSurname());
					response.getCustomers().put(uuid, customer);
				}
				response.getServices().add(serviceItem);
			}
		}
	}
	
	private Seat createSeat(Ticket ticket) {
		Seat seat = new Seat();
		seat.setId(ticket.getSeat());
		seat.setNumber(ticket.getSeatNumber());
		return seat;
	}
	
	private void addSegment(String key, Map<String, Locality> localities,
			Map<String, Organisation> organisations, Map<String, Segment> segments, Ticket ticket) {
		Segment segment = new Segment();
		segment.setNumber(ticket.getRouteCode());
		segment.setDepartureDate(ticket.getDepartAt());
		segment.setArrivalDate(ticket.getArriveAt());
		
		segment.setDeparture(search.createLocality(localities,
				ticket.getGeoLocalityFrom(), ticket.getGeoPointFrom(), ticket.getGeoPointFromName(), null));
		segment.setArrival(search.createLocality(localities,
				ticket.getGeoLocalityTo(), ticket.getGeoPointTo(), ticket.getGeoPointToName(), null));
		
		segment.setCarrier(search.addOrganisation(organisations,
				ticket.getCarrierCode(), ticket.getCarrierName()));
		segment.setInsurance(search.addOrganisation(organisations,
				ticket.getInsuranceCode(), null));
		
		segments.put(key, segment);
	}
	
	private Price createPrice(Ticket ticket) {
		
		// тариф
		Tariff tariff = new Tariff();
		if (ticket.getTicketDiscount().isEmpty()) {
			tariff.setCode("base");
		} else {
			tariff.setCode(ticket.getTicketDiscount().get(0).getDiscountKind());
			for (Lang lang : Lang.values()) {
				Parameters parameters = ticket.getTicketDiscount().get(0).getI18n().get(lang.toString().toLowerCase());
				if (parameters != null) {
					tariff.setName(lang, parameters.getName());
					tariff.setDescription(lang, parameters.getDescription());
				}
			}
		}
		tariff.setValue(ticket.getTariff());
		
		// стоимость
		Price price = new Price();
		price.setCurrency(Currency.valueOf(ticket.getCurrency()));
		price.setAmount(ticket.getCost());
		price.setTariff(tariff);
		
		// сборы
		List<Commission> commissions = new ArrayList<>(ticket.getTicketFee().size());
		for (Fee fee : ticket.getTicketFee()) {
			Commission commission = new Commission();
			commission.setCode(fee.getKind());
			commission.setName(fee.getName());
			commission.setValue(fee.getAmount());
			commission.setType(ValueType.FIXED);
			commission.setValueCalcType(fee.isInCarrierTariff() || fee.isInPathTariff() ? CalcType.IN : CalcType.OUT);
			commissions.add(commission);
		}
		price.setCommissions(commissions);
		return price;
	}
	
	private List<Customer> getOrderCustomers(List<ServiceItem> items, Map<String, Customer> customers) {
		return items.stream().map(item -> customers.get(item.getCustomer().getId())).collect(Collectors.toList());
	}
	
	private Map<String, List<ServiceItem>> groupeByTripId(OrderRequest request) {
		Map<String, List<ServiceItem>> trips = new HashMap<>();
		for (ServiceItem item : request.getServices()) {
			TripIdModel tripIdModel = new TripIdModel().create(item.getSegment().getId());
			String tripId = tripIdModel.getId();
			List<ServiceItem> items = trips.get(tripId);
			if (items == null) {
				items = new ArrayList<>();
				trips.put(tripId, items);
			}
			items.add(item);
		}
		return trips;
	}

	@Override
	public OrderResponse addServicesResponse(OrderRequest request) {
		throw RestTemplateUtil.createUnavailableMethod();
	}

	@Override
	public OrderResponse removeServicesResponse(OrderRequest request) {
		throw RestTemplateUtil.createUnavailableMethod();
	}

	@Override
	public OrderResponse updateCustomersResponse(OrderRequest request) {
		throw RestTemplateUtil.createUnavailableMethod();
	}

	@Override
	public OrderResponse getResponse(String orderId) {
		// формируем ответ
		OrderResponse response = createResponse(new HashMap<String, Customer>());
		response.setOrderId(orderId);
		
		OrderIdModel orderIdModel = new OrderIdModel().create(orderId);
		for (String id : orderIdModel.getIds().keySet()) {
			try {
				Order order = client.info(id);
				addOrder(response, order, null);
			} catch (ResponseError e) {
				for (String ticketId : orderIdModel.getIds().get(id)) {
					addServiceItems(response.getServices(), ticketId, null, new RestError(e.getMessage()));
				}
			}
		}
		return response;
	}

	@Override
	public OrderResponse getServiceResponse(String serviceId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderResponse bookingResponse(String orderId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderResponse confirmResponse(String orderId) {
		
		// формируем ответ
		OrderResponse response = new OrderResponse();

		// преобразовываем ид заказа в объкт
		OrderIdModel orderIdModel = new OrderIdModel().create(orderId);
		List<ServiceItem> resultItems = new ArrayList<>();

		for (String id : orderIdModel.getIds().keySet()) {
			try {
				client.buy(id);
				for (String ticketId : orderIdModel.getIds().get(id)) {
					addServiceItems(resultItems, ticketId, true, null);
				}
			} catch (ResponseError e) {
				for (String ticketId : orderIdModel.getIds().get(id)) {
					addServiceItems(resultItems, ticketId, false, new RestError(e.getMessage()));
				}
			}
		}
		response.setOrderId(orderId);
		response.setServices(resultItems);
		return response;
	}
	
	private void addServiceItems(List<ServiceItem> resultItems, String ticketId, Boolean confirmed,
			RestError error) {
		ServiceItem serviceItem = new ServiceItem();
		serviceItem.setId(ticketId);
		serviceItem.setConfirmed(confirmed);
		serviceItem.setError(error);
		resultItems.add(serviceItem);
	}

	@Override
	public OrderResponse cancelResponse(String orderId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderResponse prepareReturnServicesResponse(OrderRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderResponse returnServicesResponse(OrderRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OrderResponse getPdfDocumentsResponse(OrderRequest request) {
		// TODO Auto-generated method stub
		return null;
	}
}

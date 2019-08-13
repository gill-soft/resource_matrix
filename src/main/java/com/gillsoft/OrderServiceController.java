package com.gillsoft;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
import com.gillsoft.model.Document;
import com.gillsoft.model.DocumentType;
import com.gillsoft.model.Lang;
import com.gillsoft.model.Locality;
import com.gillsoft.model.Organisation;
import com.gillsoft.model.Price;
import com.gillsoft.model.RestError;
import com.gillsoft.model.ReturnCondition;
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
			try {
				Order order = client.newOrder(item.getKey(), request.getCurrency(),
						request.getCustomers(), item.getValue());
				List<String> tickets = new ArrayList<>();
				idModel.getIds().put(order.getHash(), tickets);
				for (List<Ticket> orderTickets : order.getTickets().values()) {
					for (Ticket ticket : orderTickets) {
						tickets.add(ticket.getHash());
					}
				}
				addOrder(response, order, getOrderCustomers(item.getValue(), request.getCustomers()));
				request.getCustomers().forEach((key, value) -> value.setId(null));
			} catch (ResponseError e) {
				for (ServiceItem serviceItem : item.getValue()) {
					serviceItem.setError(new RestError(e.getMessage()));
					response.getServices().add(serviceItem);
				}
			}
		}
		response.setOrderId(idModel.asString());
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
				OrderIdModel ticketId = new OrderIdModel();
				ticketId.getIds().put(order.getHash(), Collections.singletonList(ticket.getHash()));
				serviceItem.setId(ticketId.asString());
				serviceItem.setNumber(ticket.getNumber());
				serviceItem.setPrice(createPrice(ticket));
				serviceItem.setSeat(createSeat(ticket));
				
				TripIdModel id = new TripIdModel(tickets.getKey(), null, String.valueOf(ticket.getGeoLocalityFrom()),
						String.valueOf(ticket.getGeoLocalityTo()), ticket.getDepartAt(), ticket.getCurrency());
				Trip trip = search.getTripFromCache(id.asString());
				if (trip != null) {
					id = new TripIdModel(tickets.getKey(), trip.getRouteId(), String.valueOf(trip.getDepartCityId()),
							String.valueOf(trip.getArriveCityId()), trip.getDepartDate(), trip.getCurrency());
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
		tariff.setValue(ticket.getCost());
		
		// стоимость
		Price price = new Price();
		price.setCurrency(Currency.valueOf(ticket.getCurrency()));
		price.setAmount(ticket.getCost());
		price.setTariff(tariff);
		
		// сборы
		addCommissions(price, ticket.getTicketFee(), false);
		
		return price;
	}
	
	private void addCommissions(Price price, List<Fee> fees, boolean calcReturn) {
		List<Commission> commissions = new ArrayList<>(fees.size());
		for (Fee fee : fees) {
			Commission commission = new Commission();
			commission.setCode(fee.getKind());
			commission.setName(fee.getName());
			commission.setValue(calcReturn ? fee.getReturnAmount() : fee.getAmount());
			commission.setType(ValueType.FIXED);
			commission.setValueCalcType(CalcType.IN);
			commissions.add(commission);
		}
		price.setCommissions(commissions);
	}
	
	private List<Customer> getOrderCustomers(List<ServiceItem> items, Map<String, Customer> customers) {
		customers.forEach((key, value) -> value.setId(key));
		return items.stream().map(item -> customers.get(item.getCustomer().getId())).collect(Collectors.toList());
	}
	
	private Map<String, List<ServiceItem>> groupeByTripId(OrderRequest request) {
		Map<String, List<ServiceItem>> trips = new HashMap<>();
		for (ServiceItem item : request.getServices()) {
			TripIdModel tripIdModel = new TripIdModel().create(item.getSegment().getId());
			String tripId = tripIdModel.getIntervalId();
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
		return getOrder(orderId, null);
	}

	@Override
	public OrderResponse getServiceResponse(String serviceId) {
		OrderIdModel orderIdModel = new OrderIdModel().create(serviceId);
		return getOrder(serviceId, orderIdModel.getIds().entrySet().iterator().next().getValue().get(0));
	}
	
	private OrderResponse getOrder(String orderId, String selectedTicketId) {
		
		// формируем ответ
		OrderResponse response = createResponse(new HashMap<String, Customer>());
		response.setOrderId(orderId);
		
		OrderIdModel orderIdModel = new OrderIdModel().create(orderId);
		for (String id : orderIdModel.getIds().keySet()) {
			try {
				Order order = client.info(id);
				if (selectedTicketId != null) {
					for (Entry<String, List<Ticket>> tickets : order.getTickets().entrySet()) {
						for (Iterator<Ticket> iterator = tickets.getValue().iterator(); iterator.hasNext();) {
							Ticket ticket = iterator.next();
							if (!Objects.equals(selectedTicketId, ticket.getHash())) {
								iterator.remove();
							}
						}
					}
				}
				addOrder(response, order, null);
			} catch (ResponseError e) {
				for (String ticketId : orderIdModel.getIds().get(id)) {
					addServiceItem(response.getServices(), id, ticketId, null, new RestError(e.getMessage()));
				}
			}
		}
		return response;
	}

	@Override
	public OrderResponse bookingResponse(String orderId) {
		return confirmOrder(orderId, true);
	}

	@Override
	public OrderResponse confirmResponse(String orderId) {
		return confirmOrder(orderId, false);
	}
	
	private OrderResponse confirmOrder(String orderId, boolean reserve) {
		
		// формируем ответ
		OrderResponse response = new OrderResponse();

		// преобразовываем ид заказа в объкт
		OrderIdModel orderIdModel = new OrderIdModel().create(orderId);
		List<ServiceItem> resultItems = new ArrayList<>();

		for (String id : orderIdModel.getIds().keySet()) {
			try {
				if (reserve) {
					Order order = client.reserve(id);
					List<ServiceItem> reserveItems = new ArrayList<>();
					for (String ticketId : orderIdModel.getIds().get(id)) {
						addServiceItem(reserveItems, id, ticketId, true, null);
					}
					for (ServiceItem service : reserveItems) {
						service.setExpire(order.getReservedTo());
					}
					resultItems.addAll(reserveItems);
				} else {
					client.buy(id);
					for (String ticketId : orderIdModel.getIds().get(id)) {
						addServiceItem(resultItems, id, ticketId, true, null);
					}
				}
			} catch (ResponseError e) {
				for (String ticketId : orderIdModel.getIds().get(id)) {
					addServiceItem(resultItems, id, ticketId, false, new RestError(e.getMessage()));
				}
			}
		}
		response.setOrderId(orderId);
		response.setServices(resultItems);
		return response;
	}
	
	private void addServiceItem(List<ServiceItem> resultItems, String orderId, String ticketId, Boolean confirmed,
			RestError error) {
		ServiceItem serviceItem = new ServiceItem();
		if (orderId != null) {
			OrderIdModel model = new OrderIdModel();
			model.getIds().put(orderId, Collections.singletonList(ticketId));
			serviceItem.setId(model.asString());
		} else {
			serviceItem.setId(ticketId);
		}
		serviceItem.setConfirmed(confirmed);
		serviceItem.setError(error);
		resultItems.add(serviceItem);
	}

	@Override
	public OrderResponse cancelResponse(String orderId) {
		
		// формируем ответ
		OrderResponse response = new OrderResponse();

		// преобразовываем ид заказа в объкт
		OrderIdModel orderIdModel = new OrderIdModel().create(orderId);
		List<ServiceItem> resultItems = new ArrayList<>();

		for (String id : orderIdModel.getIds().keySet()) {
			try {
				Order order = client.info(id);
				if (order.getStatus().equals(RestClient.STATUS_NEW)
						|| order.getStatus().equals(RestClient.STATUS_BOOKING)) {
					order = client.cancel(id);
					checkStatus(order, resultItems, id, orderIdModel.getIds().get(id), RestClient.STATUS_CANCEL);
				} else if (order.getStatus().equals(RestClient.STATUS_BUY)) {
					order = client.annulate(id, "Order error");
					checkStatus(order, resultItems, id, orderIdModel.getIds().get(id), RestClient.STATUS_ANNULMENT);
				} else if (order.getStatus().equals(RestClient.STATUS_CANCEL)
						|| order.getStatus().equals(RestClient.STATUS_SYSTEM_CANCEL)) {
					for (String ticketId : orderIdModel.getIds().get(id)) {
						addServiceItem(resultItems, id, ticketId, true, null);
					}
				} else {
					for (String ticketId : orderIdModel.getIds().get(id)) {
						addServiceItem(resultItems, id, ticketId, false, null);
					}
				}
			} catch (ResponseError e) {
				for (String ticketId : orderIdModel.getIds().get(id)) {
					addServiceItem(resultItems, id, ticketId, false, new RestError(e.getMessage()));
				}
			}
		}
		response.setOrderId(orderId);
		response.setServices(resultItems);
		return response;
	}
	
	private void checkStatus(Order order, List<ServiceItem> services, String orderId, List<String> ticketIds,
			String checkStatus) throws ResponseError {
		if (order.getStatus().equals(checkStatus)) {
			for (String ticketId : ticketIds) {
				addServiceItem(services, orderId, ticketId, true, null);
			}
		} else {
			throw new ResponseError("Order not canceled. Status = " + order.getStatus());
		}
	}

	@Override
	public OrderResponse prepareReturnServicesResponse(OrderRequest request) {
		
		// формируем ответ
		OrderResponse response = new OrderResponse();
		List<ServiceItem> resultItems = new ArrayList<>();
		for (ServiceItem service : request.getServices()) {
			try {
				Ticket ticket = client.autoReturnPrice(getTicketId(service.getId()));
				addReturnPrice(service, ticket);
			} catch (ResponseError e) {
				service.setError(new RestError(e.getMessage()));
			}
			resultItems.add(service);
		}
		response.setServices(resultItems);
		return response;
	}
	
	private void addReturnPrice(ServiceItem service, Ticket ticket) {
		Price price = new Price();
		price.setCurrency(Currency.valueOf(ticket.getCurrency()));
		price.setAmount(ticket.getAmount());
		service.setPrice(price);
		
		Tariff tariff = new Tariff();
		tariff.setValue(ticket.getCarrierTariff());
		price.setTariff(tariff);
		
		if (ticket.getTicketReturnFee() != null) {
			addCommissions(price, ticket.getTicketReturnFee(), true);
		}
		service.setPrice(price);
		
		// условие возврата
		addReturnCondition(service.getId(), ticket, tariff);
	}
	
	private void addReturnCondition(String serviceId, Ticket ticket, Tariff tariff) {
		OrderIdModel orderIdModel = new OrderIdModel().create(serviceId);
		BigDecimal ticketCost = null;
		try {
			Order order = client.info(orderIdModel.getIds().entrySet().iterator().next().getKey());
			if (order != null) {
				String id = orderIdModel.getIds().entrySet().iterator().next().getValue().get(0);
				out:
				for (Entry<String, List<Ticket>> tickets : order.getTickets().entrySet()) {
					for (Iterator<Ticket> iterator = tickets.getValue().iterator(); iterator.hasNext();) {
						Ticket info = iterator.next();
						if (Objects.equals(id, info.getHash())) {
							ticketCost = info.getCost();
							List<ReturnCondition> conditions = search.getReturnConditions(tickets.getKey());
							if (conditions != null) {
								for (ReturnCondition condition : conditions) {
									if (condition.getMinutesBeforeDepart() == ticket.getReturnRule().getMinutesBeforeDepart()) {
										condition.setReturnPercent(ticket.getAmount().divide(ticketCost, 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100)));
										tariff.setReturnConditions(Collections.singletonList(condition));
										break;
									}
								}
							}
							break out;
						}
					}
				}
			}
		} catch (Exception e) {
		}
		if (tariff.getReturnConditions() == null) {
			ReturnCondition condition = new ReturnCondition();
			condition.setTitle(ticket.getReturnRule().getTitle());
			condition.setDescription(ticket.getReturnRule().getDescription());
			condition.setMinutesBeforeDepart(ticket.getReturnRule().getMinutesBeforeDepart());
			if (ticketCost != null) {
				condition.setReturnPercent(ticket.getAmount().divide(ticketCost, 2, RoundingMode.HALF_UP).multiply(new BigDecimal(100)));
			}
			tariff.setReturnConditions(Collections.singletonList(condition));
		}
	}

	@Override
	public OrderResponse returnServicesResponse(OrderRequest request) {
		
		// формируем ответ
		OrderResponse response = new OrderResponse();
		List<ServiceItem> resultItems = new ArrayList<>();
		for (ServiceItem service : request.getServices()) {
			try {
				
				Ticket ticket = client.autoReturn(getTicketId(service.getId()));
				if (Objects.equals(ticket.getStatus(), RestClient.STATUS_RETURNED)) {
					addReturnPrice(service, ticket.getTicketReturn());
					service.setConfirmed(true);
					resultItems.add(service);
				} else {
					throw new ResponseError("Ticket not returned. Status = " + ticket.getStatus());
				}
			} catch (ResponseError e) {
				addServiceItem(resultItems, null, service.getId(), false, new RestError(e.getMessage()));
			}
		}
		response.setServices(resultItems);
		return response;
	}

	@Override
	public OrderResponse getPdfDocumentsResponse(OrderRequest request) {
		OrderResponse response = new OrderResponse();
		List<Document> documents = new ArrayList<>();
		OrderIdModel idModel = new OrderIdModel().create(request.getOrderId());
		for (String id : idModel.getIds().keySet()) {
			try {
				String base64 = client.getTickets(id);
				if (base64 != null) {
					Document document = new Document();
					document.setType(DocumentType.TICKET);
					document.setBase64(base64);
					documents.add(document);
				}
			} catch (ResponseError e) {
				for (String ticketId : idModel.getIds().get(id)) {
					addServiceItem(response.getServices(), id, ticketId, null, new RestError(e.getMessage()));
				}
			}
		}
		response.setDocuments(documents);
		return response;
	}
	
	private String getTicketId(String serviceId) {
		return new OrderIdModel().create(serviceId).getIds().entrySet().iterator().next().getValue().get(0);
	}
	
}

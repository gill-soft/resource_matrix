package com.gillsoft;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import com.gillsoft.abstract_rest_service.AbstractResourceService;
import com.gillsoft.client.RestClient;
import com.gillsoft.model.Method;
import com.gillsoft.model.MethodType;
import com.gillsoft.model.Ping;
import com.gillsoft.model.Resource;
import com.gillsoft.util.StringUtil;

@RestController
public class ResourceServiceController extends AbstractResourceService {
	
	@Autowired
	private RestClient client;

	@Override
	public List<Method> getAvailableMethodsResponse() {
		List<Method> methods = new ArrayList<>();
		
		// resource
		addMethod(methods, "Resource activity check", Method.PING, MethodType.GET);
		addMethod(methods, "Information about resource", Method.INFO, MethodType.GET);
		addMethod(methods, "Available methods", Method.METHOD, MethodType.GET);
		
		// localities
		addMethod(methods, "All available resource localities", Method.LOCALITY_ALL, MethodType.POST);
		addMethod(methods, "All used resource localities", Method.LOCALITY_USED, MethodType.POST);
		
		// search
		addMethod(methods, "Init search", Method.SEARCH, MethodType.POST);
		addMethod(methods, "Return search result", Method.SEARCH, MethodType.GET);
		addMethod(methods, "Return trip route", Method.SEARCH_TRIP_ROUTE, MethodType.GET);
		addMethod(methods, "Return trip tariffs", Method.SEARCH_TRIP_TARIFFS, MethodType.GET);
		addMethod(methods, "Return tariff return conditions", Method.SEARCH_TRIP_CONDITIONS, MethodType.GET);
		addMethod(methods, "Return the list of seats on trip", Method.SEARCH_TRIP_SEATS, MethodType.GET);
		addMethod(methods, "Return the scheme of seats on trip", Method.SEARCH_TRIP_SEATS_SCHEME, MethodType.GET);
		addMethod(methods, "Return required fields to create order", Method.SEARCH_TRIP_REQUIRED, MethodType.GET);

		// order
		addMethod(methods, "Create new order", Method.ORDER, MethodType.POST);
		addMethod(methods, "Book order", Method.ORDER_BOOKING, MethodType.POST);
		addMethod(methods, "Confirm order", Method.ORDER_CONFIRM, MethodType.POST);
		addMethod(methods, "Cancel order", Method.ORDER_CANCEL, MethodType.POST);
		addMethod(methods, "Prepare order for return", Method.ORDER_RETURN_PREPARE, MethodType.POST);
		addMethod(methods, "Confirm order return", Method.ORDER_RETURN_CONFIRM, MethodType.POST);
		return methods;
	}

	@Override
	public Resource getInfoResponse() {
		Resource resource = new Resource();
		resource.setCode("Matrix");
		resource.setName("SPLOT API");
		return resource;
	}

	@Override
	public Ping pingResponse(String id) {
		return createPing(client.ping() ? id : StringUtil.generateUUID());
	}

}

package com.gillsoft.client;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.gillsoft.util.StringUtil;

@JsonInclude(value = Include.NON_NULL)
public class Response<T> implements Serializable {
	

	private static final long serialVersionUID = -5472021283160252622L;
	
	private int statusCode;
	private boolean status;
	private String error;
	private String message;
	private Map<String, List<String>> messages;
	private T data;

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public boolean isStatus() {
		return status;
	}

	public void setStatus(boolean status) {
		this.status = status;
	}

	public String getError() {
		try {
			return error + ": "
					+ (messages != null ? StringUtil.objectToJsonString(messages) : "");
		} catch (JsonProcessingException e) {
			return error;
		}
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Map<String, List<String>> getMessages() {
		return messages;
	}

	public void setMessages(Map<String, List<String>> messages) {
		this.messages = messages;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

}

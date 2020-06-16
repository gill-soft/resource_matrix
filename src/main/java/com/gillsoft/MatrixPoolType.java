package com.gillsoft;

import com.gillsoft.concurrent.BasePoolType;

public class MatrixPoolType implements BasePoolType {

	private String name;
	private int size;

	public MatrixPoolType(String name, int size) {
		this.name = name;
		this.size = size;
	}

	@Override
	public int getSize() {
		return size;
	}

	@Override
	public String name() {
		return name;
	}

}

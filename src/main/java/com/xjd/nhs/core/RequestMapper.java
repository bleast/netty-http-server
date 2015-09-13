package com.xjd.nhs.core;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Arrays;

public class RequestMapper {
	private String[] reqUris;
	private String reqMethod;
	private boolean reqSupportMultipart;
	private String resContentType;
	private Charset resCharset;
	private String beanName;
	private Method method;
	private Method proxyMethod;

	public String[] getReqUris() {
		return reqUris;
	}

	public void setReqUris(String[] reqUris) {
		this.reqUris = reqUris;
	}

	public String getReqMethod() {
		return reqMethod;
	}

	public void setReqMethod(String reqMethod) {
		this.reqMethod = reqMethod;
	}

	public boolean isReqSupportMultipart() {
		return reqSupportMultipart;
	}

	public void setReqSupportMultipart(boolean reqSupportMultipart) {
		this.reqSupportMultipart = reqSupportMultipart;
	}

	public String getResContentType() {
		return resContentType;
	}

	public void setResContentType(String resContentType) {
		this.resContentType = resContentType;
	}

	public Charset getResCharset() {
		return resCharset;
	}

	public void setResCharset(Charset resCharset) {
		this.resCharset = resCharset;
	}

	public String getBeanName() {
		return beanName;
	}

	public void setBeanName(String beanName) {
		this.beanName = beanName;
	}

	public Method getMethod() {
		return method;
	}

	public void setMethod(Method method) {
		this.method = method;
	}

	public Method getProxyMethod() {
		return proxyMethod;
	}

	public void setProxyMethod(Method proxyMethod) {
		this.proxyMethod = proxyMethod;
	}

	@Override
	public String toString() {
		return "RequestMapper{" +
				"reqUris=" + Arrays.toString(reqUris) +
				", reqMethod='" + reqMethod + '\'' +
				", reqSupportMultipart=" + reqSupportMultipart +
				", resContentType='" + resContentType + '\'' +
				", resCharset=" + resCharset +
				", beanName='" + beanName + '\'' +
				", method='" + (method == null ? method : method.getName()) + '\'' +
				", proxyed=" + (proxyMethod == method ? false : true) +
				'}';
	}
}

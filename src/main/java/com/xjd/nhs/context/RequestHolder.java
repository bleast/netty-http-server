package com.xjd.nhs.context;

import com.xjd.nhs.HttpRequest;

/**
 * @author elvis.xu
 * @since 2015-08-30 22:36
 */
public abstract class RequestHolder {
	private static ThreadLocal<HttpRequest> requestThreadLocal = new ThreadLocal<HttpRequest>();

	public static HttpRequest get() {
		return requestThreadLocal.get();
	}

	public static void set(HttpRequest request) {
		requestThreadLocal.set(request);
	}

	public static void clear() {
		requestThreadLocal.remove();
	}
}

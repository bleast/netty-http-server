package com.xjd.nhs;

import java.util.Collection;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;

public interface HttpResponse {

	HttpResponseStatus getStatus();

	void setStatus(HttpResponseStatus status);

	HttpHeaders getHeaders();

	void setHeaders(HttpHeaders headers);

	Collection<Cookie> getCookies();

	void setCookies(Collection<Cookie> cookies);

	Object getContent();

	void setContent(Object content);
}

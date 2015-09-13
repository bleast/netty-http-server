package com.xjd.nhs;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.FileUpload;

public interface HttpRequest {
	SocketAddress getRemoteAddress();

	SocketAddress getLocalAddress();

	String getUri();

	String getRequestUri();

	HttpVersion getProtocol();

	HttpMethod getMethod();

	HttpHeaders getHeaders();

	Map<String, List<String>> getParameters();

	Collection<Cookie> getCookies();

	boolean isMultipart();

	List<FileUpload> getUploadedFiles();
	
	boolean isCustomBody();
	
	byte[] getBody();
}

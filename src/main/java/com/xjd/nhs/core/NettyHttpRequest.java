package com.xjd.nhs.core;

import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.FileUpload;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class NettyHttpRequest implements com.xjd.nhs.HttpRequest, HttpRequest {

	protected HttpRequest request;

	protected SocketAddress remoteAddress;
	protected SocketAddress localAddress;
	protected String requestUri;
	protected Map<String, List<String>> parameters;
	protected Collection<Cookie> cookies;
	protected boolean multipart;
	protected List<FileUpload> uploadedFiles;
	protected boolean customBody;
	protected byte[] body;

	public NettyHttpRequest(HttpRequest httpRequest) {
		this.request = httpRequest;
	}

	@Override
	public HttpRequest setProtocolVersion(HttpVersion version) {
		request.setProtocolVersion(version);
		return this;
	}

	@Override
	public HttpRequest setMethod(HttpMethod method) {
		request.setMethod(method);
		return this;
	}

	@Override
	public HttpRequest setUri(String uri) {
		request.setUri(uri);
		return this;
	}

	@Override
	public HttpVersion getProtocolVersion() {
		return request.getProtocolVersion();
	}

	@Override
	public HttpHeaders headers() {
		return request.headers();
	}

	@Override
	public DecoderResult getDecoderResult() {
		return request.getDecoderResult();
	}

	@Override
	public void setDecoderResult(DecoderResult result) {
		request.setDecoderResult(result);
	}

	public SocketAddress getRemoteAddress() {
		return remoteAddress;
	}

	public SocketAddress getLocalAddress() {
		return localAddress;
	}

	public String getUri() {
		return request.getUri();
	}

	public HttpVersion getProtocol() {
		return request.getProtocolVersion();
	}

	public HttpMethod getMethod() {
		return request.getMethod();
	}

	public HttpHeaders getHeaders() {
		return request.headers();
	}

	public Map<String, List<String>> getParameters() {
		return parameters;
	}

	public Collection<Cookie> getCookies() {
		return cookies;
	}

	public boolean isMultipart() {
		return multipart;
	}

	public List<FileUpload> getUploadedFiles() {
		return uploadedFiles;
	}

	public void setRemoteAddress(SocketAddress remoteAddress) {
		this.remoteAddress = remoteAddress;
	}

	public void setLocalAddress(SocketAddress localAddress) {
		this.localAddress = localAddress;
	}

	public void setParameters(Map<String, List<String>> parameters) {
		this.parameters = parameters;
	}

	public void setCookies(Collection<Cookie> cookies) {
		this.cookies = cookies;
	}

	public void setMultipart(boolean multipart) {
		this.multipart = multipart;
	}

	public void setUploadedFiles(List<FileUpload> uploadedFiles) {
		this.uploadedFiles = uploadedFiles;
	}

	public boolean isCustomBody() {
		return customBody;
	}

	public void setCustomBody(boolean customBody) {
		this.customBody = customBody;
	}

	public byte[] getBody() {
		return body;
	}

	public void setBody(byte[] body) {
		this.body = body;
	}

	@Override
	public String getRequestUri() {
		return requestUri;
	}

	public void setRequestUri(String requestUri) {
		this.requestUri = requestUri;
	}
}

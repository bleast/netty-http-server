package com.xjd.nhs.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.handler.stream.ChunkedFile;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedStream;

import org.perf4j.log4j.Log4JStopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xjd.nhs.HttpResponse;
import com.xjd.nhs.context.RequestHolder;

/**
 * <pre>
 * Http请求的业务解析, 线程不安全
 * </pre>
 * @author elvis.xu
 * @since 2015-6-4
 */
public class NettyHttpChannelHandler extends SimpleChannelInboundHandler<HttpObject> {
	private static Logger log = LoggerFactory.getLogger(NettyHttpChannelHandler.class);

	protected static ObjectMapper objectMapper = new ObjectMapper();
	protected static HttpDataFactory httpDataFactory = new DefaultHttpDataFactory();

	static {
		objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
	}

	protected HttpRequestRouter router;
	protected NettyHttpRequest request;
	protected HttpPostRequestDecoder decoder;
	protected CompositeByteBuf buf;

	protected boolean reset = false;

	protected Log4JStopWatch stopWatch;

	public NettyHttpChannelHandler(HttpRequestRouter httpRequestRouter) {
		if (httpRequestRouter == null) {
			throw new RuntimeException("httpRequestRouter cannot be null.");
		}
		this.router = httpRequestRouter;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.error("netty exception caught: ", cause);
		reset();
		ctx.close();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
		if (msg instanceof HttpRequest) {
			startWatch();

			reset = false;

			HttpRequest httpRequest = (HttpRequest) msg;

			request = new NettyHttpRequest(httpRequest);
			request.setLocalAddress(ctx.channel().localAddress());
			request.setRemoteAddress(ctx.channel().remoteAddress());

			Collection<Cookie> cookies = null;
			String cookieStr = request.headers().get(HttpHeaders.Names.COOKIE);
			if (cookieStr == null) {
				cookies = Collections.emptySet();
			} else {
				cookies = CookieDecoder.decode(cookieStr);
			}

			QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
			request.setRequestUri(queryStringDecoder.path());
			Map<String, List<String>> params = queryStringDecoder.parameters();
			if (httpRequest.getMethod() == HttpMethod.POST) {
				Map<String, List<String>> ps = new HashMap<String, List<String>>();
				ps.putAll(params);
				request.setParameters(ps);
			} else {
				request.setParameters(params);
			}

			if (httpRequest.getMethod() == HttpMethod.POST) {
				request.setMultipart(HttpPostRequestDecoder.isMultipart(httpRequest));
			}

			if (request.isMultipart()) {
				request.setUploadedFiles(new LinkedList<FileUpload>());
			} else {
				request.setUploadedFiles(Collections.<FileUpload> emptyList());
			}

			HttpResponse response = router.support(request); // 是否支持该请求的处理

			if (response != null) { // 不支持
				write(ctx, request, response);
				reset();
				return;
			}

			if (httpRequest.getMethod() == HttpMethod.POST) {
				if (request.isMultipart() || isFormData(request.getHeaders().get(HttpHeaders.Names.CONTENT_TYPE))) {
					try {
						decoder = new HttpPostRequestDecoder(httpDataFactory, httpRequest);
					} catch (ErrorDataDecoderException e) {
						log.error("cannot reolve request.", e);
						decodeError(ctx, request);
						return;
					}
				}
			}

			if (httpRequest.getMethod() == HttpMethod.POST && decoder == null) {// 不需要decoder使用Bytebuf
				request.setCustomBody(true);
				buf = Unpooled.compositeBuffer(); // 注意大小只有16个
			}

		} else if (msg instanceof HttpContent) {
			if (reset) {
				return;
			}
			HttpContent chunk = (HttpContent) msg;

			if (decoder != null) {
				try {
					decoder.offer(chunk);
					decodeAttributes(decoder, request);
				} catch (ErrorDataDecoderException e) {
					log.error("cannot reolve request.", e);
					decodeError(ctx, request);
					return;
				} catch (IOException e) {
					log.error("cannot reolve request.", e);
					decodeError(ctx, request);
					return;
				}
			} else if (buf != null) { // 不需要decoder
				buf.addComponent(chunk.content());
				chunk.content().retain();
			}

			if (chunk instanceof LastHttpContent) {
				if (buf != null && buf.numComponents() > 0) {
					int len = 0;
					for (int i = 0; i < buf.numComponents(); i++) {
						len += buf.component(i).readableBytes();
					}
					byte[] bs = new byte[len];
					len = 0;
					for (int i = 0; i < buf.numComponents(); i++) {
						int r = buf.component(i).readableBytes();
						buf.component(i).readBytes(bs, len, r);
						buf.component(i).release();
						len += r;
					}
					request.setBody(bs);
				}
				RequestHolder.set(request);
				HttpResponse response = router.route(request);
				RequestHolder.clear();
				write(ctx, request, response);
			}
		}
	}

	protected void write(ChannelHandlerContext ctx, NettyHttpRequest request, HttpResponse res) throws Exception {
		DefaultHttpResponse response = new DefaultHttpResponse(request.getProtocolVersion(), res.getStatus());

		// header
		if (res.getHeaders() != null) {
			response.headers().set(res.getHeaders());
		}

		// keepAlive的判断
		if (HttpHeaders.isKeepAlive(request)) {
			response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
		}

		// cookie
		if (res.getCookies() != null && !res.getCookies().isEmpty()) {
			String cookie = ClientCookieEncoder.encode(res.getCookies());
			response.headers().set(HttpHeaders.Names.SET_COOKIE, cookie);
		}

		// 跨域问题
		response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
		response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, HEAD, OPTIONS");
		response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS, "Origin, X-Requested-With, Content-Type, Accept");

		// 输出对象
		Object rt = res.getContent();
		long length = 0;
		if (rt == null) {
			response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, 0);
			response.headers().remove(HttpHeaders.Names.CONTENT_TYPE);
			ctx.write(response);
			ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
		} else {
			response.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
			if (rt instanceof byte[]) {
				byte[] bs = (byte[]) rt;
				response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bs.length);
				ctx.write(response);
				writeBytes(ctx, bs);

			} else if (rt instanceof String) {
				String s = (String) rt;
				byte[] bs = (byte[]) s.getBytes(getCharset(response.headers().get(HttpHeaders.Names.CONTENT_TYPE)));
				response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bs.length);
				ctx.write(response);
				writeBytes(ctx, bs);

			} else if (rt instanceof InputStream) {
				InputStream is = (InputStream) rt;
				HttpChunkedInput input = new HttpChunkedInput(new ChunkedStream(is));
				ctx.write(response);
				ctx.write(input);

			} else if (rt instanceof File) {
				File f = (File) rt;
				response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, f.length());
				HttpChunkedInput input = new HttpChunkedInput(new ChunkedFile(f));
				ctx.write(response);
				ctx.write(input);

			} else {
				String contentType = response.headers().get(HttpHeaders.Names.CONTENT_TYPE);
				if (contentType != null && contentType.toLowerCase().indexOf("json") != -1) { // JSON转换
					Charset c = getCharset(contentType);
					if (Charset.forName("UTF-8").equals(c)) {
						byte[] bs = objectMapper.writeValueAsBytes(rt);
						response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bs.length);
						ctx.write(response);
						writeBytes(ctx, bs);
					} else {
						String s = objectMapper.writeValueAsString(rt);
						byte[] bs = (byte[]) s.getBytes(c);
						response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, bs.length);
						ctx.write(response);
						writeBytes(ctx, bs);
					}

				} else if (contentType != null && contentType.toLowerCase().indexOf("xml") != -1) {
					// TODO XML转换
				} else {
					// TODO 默认序列化
				}
			}
		}
		log.debug("request process: uri={}, resStatus={}", request.getRequestUri(), response.getStatus());
		ctx.flush();
		stopWatch(request.getRequestUri());
	}

	protected Charset getCharset(String contentType) {
		int i;
		if (contentType == null || (i = contentType.toLowerCase().indexOf("charset")) == -1
				|| (i = contentType.indexOf('=', i)) == -1) {
			return Charset.forName("UTF-8");
		}

		try {
			return Charset.forName(contentType.substring(i + 1));
		} catch (Exception e) {
			log.warn("can not parse charset '{}'.", contentType.substring(i + "charset".length()));
		}
		return Charset.forName("UTF-8");
	}

	protected void writeBytes(ChannelHandlerContext ctx, final byte[] bs) {
		HttpChunkedInput input = new HttpChunkedInput(new ChunkedInput<ByteBuf>() {
			boolean read = false;

			@Override
			public ByteBuf readChunk(ChannelHandlerContext ctx) throws Exception {
				if (read) {
					return null;
				}
				ByteBuf buf = Unpooled.wrappedBuffer(bs);
				read = true;
				return buf;
			}

			@Override
			public boolean isEndOfInput() throws Exception {
				return read;
			}

			@Override
			public void close() throws Exception {
				read = true;
			}
		});
		ctx.write(input);
	}

	protected void reset() {
		if (!reset) {
			if (decoder != null) {
				try {
					decoder.destroy();
				} catch (Exception e) {
					log.warn("", e);
				}
				decoder = null;
			}
			if (buf != null) {
				buf = null;
			}
			if (request != null) {
				request = null;
			}
			reset = true;
		}
	}

	protected void decodeError(ChannelHandlerContext ctx, NettyHttpRequest request) throws Exception {
		NettyHttpResponse response = new NettyHttpResponse();
		response.setStatus(HttpResponseStatus.BAD_REQUEST);
		response.setCookies(request.getCookies());
		DefaultHttpHeaders headers = new DefaultHttpHeaders();
		headers.set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=utf8");
		response.setHeaders(headers);
		write(ctx, request, response);
		reset();
		ctx.channel().close();
	}

	protected boolean isFormData(String contentType) {
		if (contentType == null || contentType.trim().equals("")) {
			return false;
		}
		if (contentType.toLowerCase().startsWith(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED)) {
			return true;
		}
		return false;
	}

	protected void decodeAttributes(HttpPostRequestDecoder decoder, NettyHttpRequest request) throws IOException {
		try {
			while (decoder.hasNext()) {
				InterfaceHttpData interfaceHttpData = decoder.next();
				if (interfaceHttpData.getHttpDataType() == HttpDataType.Attribute) {
					Attribute attribute = (Attribute) interfaceHttpData;
					String name = attribute.getName();
					String value = attribute.getValue();

					List<String> attrValues = request.getParameters().get(name);
					if (attrValues == null) {
						attrValues = new LinkedList<String>();
						request.getParameters().put(name, attrValues);
					}
					attrValues.add(value);

				} else if (interfaceHttpData.getHttpDataType() == HttpDataType.FileUpload) {
					FileUpload fileUpload = (FileUpload) interfaceHttpData;

					request.getUploadedFiles().add(fileUpload);
				}
			}
		} catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
			log.debug("", e);
		}
	}

	protected void startWatch() {
		stopWatch = new Log4JStopWatch();
	}

	protected void stopWatch(String tag) {
		if (stopWatch != null) {
			stopWatch.stop(tag);
			stopWatch = null;
		}
	}
}

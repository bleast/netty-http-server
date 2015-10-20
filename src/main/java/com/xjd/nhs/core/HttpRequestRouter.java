package com.xjd.nhs.core;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.FileUpload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjd.nhs.HttpRequest;
import com.xjd.nhs.HttpResponse;
import com.xjd.nhs.annotation.RequestBody;
import com.xjd.nhs.annotation.RequestMapping;
import com.xjd.nhs.annotation.RequestParam;

public class HttpRequestRouter {
	public static Logger log = LoggerFactory.getLogger(HttpRequestRouter.class);

	protected static ObjectMapper objectMapper = new ObjectMapper();
	static {
		objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
	}

	protected ApplicationContext contxt;
	protected Map<String, RequestMapper> requestMap;

	public HttpRequestRouter(ApplicationContext contxt, Map<String, RequestMapper> requestMap) {
		this.contxt = contxt;
		this.requestMap = requestMap;
	}

	public HttpResponse support(NettyHttpRequest request) {
		String uri = request.getRequestUri();
		RequestMapper reqMapper = requestMap.get(uri);
		if (reqMapper == null) {
			NettyHttpResponse res = new NettyHttpResponse();
			res.setStatus(HttpResponseStatus.NOT_FOUND);
			return res;
		}
		RequestMapping.Method spMethod = RequestMapping.Method.valueOfCode(reqMapper.getReqMethod());
		if (spMethod != RequestMapping.Method.ALL && spMethod != RequestMapping.Method.valueOfCode(request.getMethod().name())) {
			NettyHttpResponse res = new NettyHttpResponse();
			res.setStatus(HttpResponseStatus.FORBIDDEN);
			return res;
		}
		if (!reqMapper.isReqSupportMultipart() && request.isMultipart()) {
			NettyHttpResponse res = new NettyHttpResponse();
			res.setStatus(HttpResponseStatus.FORBIDDEN);
			return res;
		}
		return null;
	}

	public HttpResponse route(NettyHttpRequest request) {
		String uri = request.getRequestUri();
		RequestMapper reqMapper = requestMap.get(uri);

		Object rt = null;
		try {
			rt = execute(request, reqMapper);
		} catch (IOException e) {
			log.warn("execute request exception: {}", (Object) e);
			NettyHttpResponse res = new NettyHttpResponse();
			res.setStatus(HttpResponseStatus.BAD_REQUEST);
			return res;
		} catch (Throwable e) {
			log.error("execute request exception.", e);
			NettyHttpResponse res = new NettyHttpResponse();
			res.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
			return res;
		}

		NettyHttpResponse res = new NettyHttpResponse();
		res.setStatus(HttpResponseStatus.OK);
		HttpHeaders headers = new DefaultHttpHeaders();
		headers.set(HttpHeaders.Names.CONTENT_TYPE, reqMapper.getResContentType() + "; charset="
				+ reqMapper.getResCharset().name());
		res.setHeaders(headers);
		res.setContent(rt);
		return res;
	}

	protected Object execute(NettyHttpRequest request, RequestMapper requestMapper) throws IOException,
			InvocationTargetException, IllegalAccessException {
		Method method = requestMapper.getMethod();

		Class<?>[] paramTypes = method.getParameterTypes();
		Annotation[][] paramAnnos = method.getParameterAnnotations();
		List<Object> paramList = new ArrayList<Object>(paramTypes.length);
		for (int i = 0; i < paramTypes.length; i++) {
			Class<?> paramType = paramTypes[i];
			Object param = null;
			if (paramType.isAssignableFrom(HttpRequest.class)) {
				param = request;
			} else {
				Annotation[] pas = paramAnnos[i];
				RequestBody bodyA = null;
				RequestParam paramA = null;
				for (Annotation a : pas) {
					if (a.annotationType().equals(RequestBody.class)) {
						bodyA = (RequestBody) a;
						break;
					} else if (a.annotationType().equals(RequestParam.class)) {
						paramA = (RequestParam) a;
						break;
					}
				}

				if (bodyA != null) {
					byte[] body = request.getBody();

					if (body == null || body.length == 0) {
						param = null;
					} else if (String.class.equals(paramType)) {
						// TODO 解析请求的charset
						param = (new String(body, Charset.forName("utf8")));
					} else if (paramType.isArray()
							&& (byte.class.equals(paramType.getComponentType()) || Byte.class
									.equals(paramType.getComponentType()))) {
						param = (body);
					} else {
						// TODO 解析请求的charset
						if (bodyA.objectMappingAs() == null || bodyA.objectMappingAs() == RequestBody.ObjectMappingAs.JSON) {
							Object paramObj = objectMapper.readValue(new String(body, Charset.forName("utf8")), paramType);
							param = (paramObj);
						} else {
							// TODO XML方式
						}
					}
				} else if (paramA != null) {
					String paramStr = paramA.value();
					if (paramType.equals(FileUpload.class)) {
						FileUpload fu = null;
						if (paramStr != null && request.getUploadedFiles() != null) {
							for (FileUpload f : request.getUploadedFiles()) {
								if (paramStr.equals(f.getName())) {
									fu = f;
									break;
								}
							}
						} else if (paramStr == null){
							if (request.getUploadedFiles() != null && request.getUploadedFiles().size() > 1) {
								throw new IllegalArgumentException("You must specify which uploaded file to be use.");
							} else if (request.getUploadedFiles() != null) {
								fu = request.getUploadedFiles().get(0);
							}
						}
						if (fu == null && paramA.required()) {
							throw new IllegalArgumentException("No uploaded file.");
						}
						param = fu;
					} else if (paramType.isArray() && paramType.getComponentType().equals(FileUpload.class)) {
						param = (request.getUploadedFiles() == null || request.getUploadedFiles().isEmpty()) ? null : request.getUploadedFiles().toArray(new FileUpload[0]);
						if (param == null && paramA.required()) {
							throw new IllegalArgumentException("No uploaded file.");
						}
					} else {
						if (paramStr == null || paramStr.trim().equals("")) {
							throw new IllegalArgumentException("You must specify 'value' for RequestParam.");
						}
						List<String> paramVal = request.getParameters().get(paramStr);
						if (paramVal == null || paramVal.isEmpty()) {
							if (paramA.required()) {
								throw new IllegalArgumentException("param '" + paramStr + "' required.");
							}
							if (paramType.isPrimitive()) {
								throw new IllegalArgumentException("param '" + paramStr + "' required for primitive type '" + paramType.getName() + "'.");
							}
						} else {
							if (paramType.equals(String.class)) {
								// String
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = paramVal.get(0);

							} else if (paramType.equals(Integer.class) || paramType.equals(int.class)) {
								// int
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = Integer.valueOf(paramVal.get(0));

							} else if (paramType.equals(Long.class) || paramType.equals(long.class)) {
								// int
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = Long.valueOf(paramVal.get(0));

							} else if (paramType.equals(Byte.class) || paramType.equals(byte.class)) {
								// int
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = Byte.valueOf(paramVal.get(0));

							} else if (paramType.equals(Double.class) || paramType.equals(double.class)) {
								// int
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = Double.valueOf(paramVal.get(0));

							} else if (paramType.equals(Float.class) || paramType.equals(float.class)) {
								// int
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = Float.valueOf(paramVal.get(0));

							} else if (paramType.equals(Boolean.class) || paramType.equals(boolean.class)) {
								// int
								if (paramVal.size() > 1) {
									throw new IllegalArgumentException("parameter '" + paramStr + "' is a array.");
								}
								param = Boolean.valueOf(paramVal.get(0));

							} else if (paramType.isArray()) {
								Class<?> componentType = paramType.getComponentType();
								if (componentType.equals(String.class)) {
									// String
									param = paramVal.toArray(new String[0]);

								} else if (componentType.equals(Integer.class) || componentType.equals(int.class)) {
									// int
									Object array = Array.newInstance(componentType, paramVal.size());
									for (int i1 = 0; i1 < paramVal.size(); i1++) {
										Integer it = Integer.valueOf(paramVal.get(i1));
										Array.set(array, i1, it);
									}
									param = array;

								} else if (componentType.equals(Long.class) || componentType.equals(long.class)) {
									// long
									Object array = Array.newInstance(componentType, paramVal.size());
									for (int i1 = 0; i1 < paramVal.size(); i1++) {
										Long it = Long.valueOf(paramVal.get(i1));
										Array.set(array, i1, it);
									}
									param = array;

								} else if (componentType.equals(Byte.class) || componentType.equals(byte.class)) {
									// Byte
									Object array = Array.newInstance(componentType, paramVal.size());
									for (int i1 = 0; i1 < paramVal.size(); i1++) {
										Byte it = Byte.valueOf(paramVal.get(i1));
										Array.set(array, i1, it);
									}
									param = array;

								} else if (componentType.equals(Double.class) || componentType.equals(double.class)) {
									// Double
									Object array = Array.newInstance(componentType, paramVal.size());
									for (int i1 = 0; i1 < paramVal.size(); i1++) {
										Double it = Double.valueOf(paramVal.get(i1));
										Array.set(array, i1, it);
									}
									param = array;

								} else if (componentType.equals(Float.class) || componentType.equals(float.class)) {
									// Float
									Object array = Array.newInstance(componentType, paramVal.size());
									for (int i1 = 0; i1 < paramVal.size(); i1++) {
										Float it = Float.valueOf(paramVal.get(i1));
										Array.set(array, i1, it);
									}
									param = array;

								} else if (componentType.equals(Boolean.class) || componentType.equals(boolean.class)) {
									// Boolean
									Object array = Array.newInstance(componentType, paramVal.size());
									for (int i1 = 0; i1 < paramVal.size(); i1++) {
										Boolean it = Boolean.valueOf(paramVal.get(i1));
										Array.set(array, i1, it);
									}
									param = array;
								}

							} else {
								throw new IllegalArgumentException("cannot convert parameter '" + paramStr + "' to '" + paramType.getName() + "'.");
							}
						}
					}
				}
			}

			paramList.add(param);
		}

		Object bean = contxt.getBean(requestMapper.getBeanName());

		return requestMapper.getProxyMethod().invoke(bean, paramList.toArray());
	}
}

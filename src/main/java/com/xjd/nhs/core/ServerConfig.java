package com.xjd.nhs.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.xjd.nhs.annotation.RequestMapping;
import com.xjd.nhs.annotation.ResponseBody;

@Configuration
public class ServerConfig {
	private static Logger log = LoggerFactory.getLogger(ServerConfig.class);

	@Bean
	@Resource(name = "channelInitializer")
	public ServerBootstrap serverBootstrapFactory(ChannelInitializer<SocketChannel> channelInitializer) {
		// 配置服务器
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory());
		ServerBootstrap serverBootstrap = new ServerBootstrap();
		serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.INFO)).childHandler(channelInitializer)
				.option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

		return serverBootstrap;
	}

	@Bean(name = "channelInitializer")
	public ChannelInitializer<SocketChannel> initializerFactory(final ApplicationContext contxt) {
		return new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				SimpleChannelInboundHandler<?> channelInboundHandler = contxt.getBean(NettyHttpChannelHandler.class);
				ChannelPipeline pipeline = ch.pipeline();
				// HTTP
				pipeline.addLast(new HttpRequestDecoder());
				pipeline.addLast(new HttpResponseEncoder());
				pipeline.addLast(new HttpContentCompressor());
				pipeline.addLast(new ChunkedWriteHandler());
				// 设置处理的Handler
				pipeline.addLast(channelInboundHandler);
			}
		};
	}

	@Bean
	@Scope("prototype")
	@Resource(name = "httpRequestRouter")
	public NettyHttpChannelHandler channelHandlerFactory(HttpRequestRouter httpRequestRouter) {
		return new NettyHttpChannelHandler(httpRequestRouter);
	}

	@Bean(name = "httpRequestRouter")
	public HttpRequestRouter routerFactory(ApplicationContext contxt) {
		return new HttpRequestRouter(contxt, mapRequest(contxt));
	}

	@Bean()
	public Map<String, RequestMapper> mapRequest(ApplicationContext contxt) {
		String[] names = contxt.getBeanNamesForAnnotation(Controller.class);
		Map<String, RequestMapper> reqMap = new HashMap<String, RequestMapper>(names == null ? 0 : names.length);

		for (String name : names) {
			Class<?> clazz;
			Object bean = contxt.getBean(name);
			boolean proxy = AopUtils.isAopProxy(bean);
			if (proxy) {
				clazz = AopUtils.getTargetClass(bean);
			} else {
				clazz = contxt.getType(name);
			}

			List<Method> methodList = new LinkedList<Method>();
			for (Method method : clazz.getDeclaredMethods()) {
				if (Modifier.isPublic(method.getModifiers())) {
					methodList.add(method);
				}
			}

			RequestMapping rmClazz = clazz.getAnnotation(RequestMapping.class);
			if (rmClazz != null && methodList.size() > 1) {
				for (Method method : methodList) {
					if (method.getAnnotation(RequestMapping.class) == null) {
						throw new IllegalArgumentException("cannot mapping ctrl method: " + clazz.getName() + "."
								+ method.getName() + "()");
					}
				}
			}

			String[] defaultUris = new String[] { "" };
			for (Method method : methodList) {
				RequestMapping rmMethod = method.getAnnotation(RequestMapping.class);

				if (rmClazz == null && rmMethod == null) {
					continue;
				}

				RequestMapper reqMapper = new RequestMapper();
				reqMapper.setBeanName(name);
				reqMapper.setMethod(method);
				if (proxy) {
					reqMapper.setProxyMethod(AopUtils.getMostSpecificMethod(method, bean.getClass()));
				} else {
					reqMapper.setProxyMethod(method);
				}
				{
					List<String> uriList = new LinkedList<String>();
					String[] clazzUris = (rmClazz == null || rmClazz.value() == null || rmClazz.value().length == 0) ? defaultUris
							: rmClazz.value();
					String[] methodUris = (rmMethod == null || rmMethod.value() == null || rmMethod.value().length == 0) ? defaultUris
							: rmMethod.value();
					for (String cu : clazzUris) {
						for (String mu : methodUris) {
							uriList.add((cu + mu).trim());
						}
					}
					reqMapper.setReqUris(uriList.toArray(new String[uriList.size()]));
				}
				reqMapper.setReqMethod(rmMethod != null ? rmMethod.method().getCode() : rmClazz.method().getCode());
				reqMapper.setReqSupportMultipart(rmMethod != null ? rmMethod.supportMultipart() : rmClazz.supportMultipart());

				ResponseBody rbMethod = method.getAnnotation(ResponseBody.class);
				reqMapper.setResContentType(rbMethod != null ? rbMethod.produce().getVal()
						: ResponseBody.Produce.APPLICATION_JSON.getVal());
				reqMapper.setResCharset(rbMethod != null ? Charset.forName(rbMethod.charset()) : Charset.forName("UTF-8"));

				for (String uri : reqMapper.getReqUris()) {
					if (reqMap.get(uri) != null) {
						throw new IllegalArgumentException("double mapping uri: " + uri);
					}
					reqMap.put(uri, reqMapper);
				}

				log.debug("map request: {}", reqMapper);
			}
		}

		return reqMap;
	}
}

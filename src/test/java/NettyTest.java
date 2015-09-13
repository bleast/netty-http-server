import java.util.*;
import java.util.Map.Entry;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.stream.ChunkedWriteHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyTest {
	private static Logger log = LoggerFactory.getLogger(NettyTest.class);

	public static void main(String[] args) throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workGroup = new NioEventLoopGroup();

		try {
			ServerBootstrap sb = new ServerBootstrap();
			sb.group(bossGroup, workGroup).channel(NioServerSocketChannel.class).childHandler(new HttpChannelInitializer());

			Channel channel = sb.bind(8095).sync().channel();

			log.info("server started at: 8095");

			channel.closeFuture().sync();
		} finally {
			bossGroup.shutdownGracefully();
			workGroup.shutdownGracefully();
		}
	}

	public static class HttpChannelInitializer extends ChannelInitializer<SocketChannel> {

		protected void initChannel(SocketChannel ch) throws Exception {
			ChannelPipeline pipeline = ch.pipeline();

			pipeline.addLast("httpRequestDecoder", new HttpRequestDecoder());
			pipeline.addLast("httpResponseEncoder", new HttpResponseEncoder());
			pipeline.addLast("httpContentCompressor", new HttpContentCompressor());
			pipeline.addLast("chunkedWriteHandler", new ChunkedWriteHandler());
			pipeline.addLast("httpHandler", new HttpHandler());
		}

	}

	public static class HttpHandler extends SimpleChannelInboundHandler<HttpObject> {

		protected HttpPostRequestDecoder httpPostRequestDecoder;

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (httpPostRequestDecoder != null) {
				try {
					httpPostRequestDecoder.cleanFiles();
					httpPostRequestDecoder = null;
				} catch (Exception e) {
					log.warn("", e);
				}
			}
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
			log.trace("messaage received: {}", msg.getClass().getName());

			if (msg instanceof HttpRequest) {
				HttpRequest httpRequest = (HttpRequest) msg;

				log.trace("uri: {}", httpRequest.getUri());
				log.trace("protocol: {}", httpRequest.getProtocolVersion().toString());
				log.trace("method: {}", httpRequest.getMethod().toString());
				log.trace("decoderResult: {}", httpRequest.getDecoderResult().toString());
				log.trace("remoteAddress: {}", ctx.channel().remoteAddress());
				log.trace("localAddress: {}", ctx.channel().localAddress());

				log.trace("[[headers]]");
				HttpHeaders headers = httpRequest.headers();
				for (Entry<String, String> header : headers.entries()) {
					log.trace("{}:{}", header.getKey(), header.getValue());
				}

				log.trace("[[cookies]]");
				Set<Cookie> cookies = null;
				String cookieStr = headers.get(HttpHeaders.Names.COOKIE);
				if (cookieStr == null) {
					cookies = Collections.emptySet();
				} else {
					cookies = CookieDecoder.decode(cookieStr);
				}
				for (Cookie cookie : cookies) {
					log.trace("Cookie: {}", cookie.toString());
				}

				log.trace("[[parameters]]");
				QueryStringDecoder queryStringDecoder = new QueryStringDecoder(httpRequest.getUri());
				log.trace("uri: {}", queryStringDecoder.uri());
				log.trace("path: {}", queryStringDecoder.path());
				Map<String, List<String>> params = queryStringDecoder.parameters();
				for (Entry<String, List<String>> entry : params.entrySet()) {
					log.trace("{}:{}", entry.getKey(), Arrays.toString(entry.getValue().toArray()));
				}

				if (httpRequest.getMethod() == HttpMethod.POST) {
					log.trace("isChunked: {}", HttpHeaders.isTransferEncodingChunked(httpRequest));
					log.trace("isMulti: {}", HttpPostRequestDecoder.isMultipart(httpRequest));

					HttpDataFactory httpDataFactory;
					if (HttpPostRequestDecoder.isMultipart(httpRequest)) {
						httpDataFactory = new DefaultHttpDataFactory(true); // use disk
					} else {
						httpDataFactory = new DefaultHttpDataFactory(); // use mixed
					}
					try {
						httpPostRequestDecoder = new HttpPostRequestDecoder(httpDataFactory, httpRequest);
					} catch (ErrorDataDecoderException e) {
						log.error("cannot reolve request.");
						resolveError(ctx);
						return;
					}
				}

			} else if (msg instanceof HttpContent) {
				HttpContent chunk = (HttpContent) msg;
//				try {
//					httpPostRequestDecoder.offer(chunk);
//				} catch (ErrorDataDecoderException e) {
//					log.error("cannot reolve request.");
//					resolveError(ctx);
//					return;
//				}

//				log.trace("[[DATA]]");
//				while (httpPostRequestDecoder.hasNext()) {
//					InterfaceHttpData interfaceHttpData = httpPostRequestDecoder.next();
//					if (interfaceHttpData.getHttpDataType() == HttpDataType.Attribute) {
//						Attribute attribute = (Attribute) interfaceHttpData;
//						log.trace("attribute: {}", attribute.toString());
//					} else if (interfaceHttpData.getHttpDataType() == HttpDataType.FileUpload) {
//						FileUpload fileUpload = (FileUpload) interfaceHttpData;
//						log.trace("fileupload: {}", fileUpload.toString());
//					}
//				}

				if (chunk instanceof LastHttpContent) {
					log.trace("OK");
					// 应答
					// 应答完成后
//					DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
//					response.headers().set(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
//					ctx.write(response);
//					HttpChunkedInput httpChunkWriter;
//					try {
//						httpChunkWriter = new HttpChunkedInput(new ChunkedFile(new File("/tmp/tmp.txt")));
//						ctx.write(httpChunkWriter, ctx.newProgressivePromise()).addListener(
//								new ChannelProgressiveFutureListener() {
//
//									@Override
//									public void operationComplete(ChannelProgressiveFuture future) throws Exception {
//										System.out.println("FINISH!");
//									}
//
//									@Override
//									public void operationProgressed(ChannelProgressiveFuture future, long progress, long total)
//											throws Exception {
//										System.out.println(progress + ":" + total);
//									}
//
//								});
//
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
					// ctx.write(new
					// DefaultHttpContent(Unpooled.wrappedBuffer("HELLO".getBytes(Charset.forName("utf8")))));
					// ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
					DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
//					response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json; charset=UTF-8");
					response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, 0);
					response.headers().set(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
					response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
					response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, HEAD, OPTIONS");
					response.headers().set(HttpHeaders.Names.ACCESS_CONTROL_EXPOSE_HEADERS, "Origin, X-Requested-With, Content-Type, Accept");
					ctx.write(response);
					ctx.write(LastHttpContent.EMPTY_LAST_CONTENT);
					ctx.flush();
//					reset();
				}
			}
		}

		protected void resolveError(ChannelHandlerContext ctx) {
			// 返回错误
			reset();
			ctx.channel().close();
		}

		protected void reset() {
			if (httpPostRequestDecoder != null) {
				try {
					httpPostRequestDecoder.destroy();
					httpPostRequestDecoder = null;
				} catch (Exception e) {
					log.warn("", e);
				}
			}
		}
	}

}

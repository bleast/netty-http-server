import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Example {
	private static Logger log = LoggerFactory.getLogger(Example.class);
	public static void main(String[] args) throws Exception {
		ApplicationContext contxt = new ClassPathXmlApplicationContext("classpath:spring-test.xml");
		ServerBootstrap bootstrap = contxt.getBean(ServerBootstrap.class);
		try {
			int port = 8095;
			ChannelFuture channelFuture = bootstrap.bind(port).sync();
			log.info("server start at '{}'...", port);
			channelFuture.channel().closeFuture().sync();
		} finally {
			bootstrap.childGroup().shutdownGracefully();
			bootstrap.group().shutdownGracefully();
		}
	}
}

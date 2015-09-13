# netty-http-server
This is a very light http server based on the popular netty project and spring framework.

### Require

JDK 1.7 +


### Usage

1.import the spring config class ``com.xjd.nhs.core.ServerConfig`` in your spring config file.

```xml
// spring-test.xml
<bean class="com.xjd.nhs.core.ServerConfig" />

```

2.define a controller class to process the request

```java
@Controller
@RequestMapping(value = "/api/10")
public class TestCtrl {

	@RequestMapping(value = "/test1")
	public Object test1(@RequestBody Test1Req test1) {
		return test1;
	}

	public static class Test1Req {
		private String name;
		private Integer age;
		//... getters and setters
	}
}

```

3.start the server as follows

```java
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
```

also remember to import your controller class into spring, we can use annotation as follow

```xml
// spring-test.xml
<context:annotation-config />
<context:component-scan base-package="controller" />

```

4.No we can test it now. currently the controller only support ``post``method and the ``Test1Req``bean is initialize from a json string from the request body.mock http request as follow.

```java

POST /api/10/test1 HTTP/1.1
Host: 127.0.0.1:8095
Content-Length: 23
Connection: keep-alive

{"name":"XXX","age":30}


```
The response body as follow (just 

```java

{"name":"XXX","age":30}

```

More information please sea the Example.java

package controller;

import java.io.IOException;
import java.util.Arrays;

import io.netty.handler.codec.http.multipart.FileUpload;

import org.springframework.stereotype.Controller;

import com.xjd.nhs.annotation.RequestMapping;
import com.xjd.nhs.annotation.RequestParam;

/**
 * @author elvis.xu
 * @since 2015-10-20 23:42
 */
@Controller
@RequestMapping(value = "/api/11")
public class TestCtrl11 {
	@RequestMapping(value = "/test1")
	public Object test1(@RequestParam(value = "name", required = true) String name, @RequestParam("age") Integer age, @RequestParam("isgood") boolean isgood) {
		System.out.println(name + ", " + age + ", " + isgood);
		return "OK";
	}

	@RequestMapping(value = "/test2")
	public Object test2(@RequestParam(value = "name", required = true) String[] name, @RequestParam("age") Integer[] age, @RequestParam("isgood") boolean[] isgood) {
		System.out.println(Arrays.toString(name) + ", " + Arrays.toString(age) + ", " + Arrays.toString(isgood));
		return "OK";
	}

	@RequestMapping(value = "/test3", supportMultipart = true)
	public Object test3(@RequestParam(value = "name", required = true) String[] name, @RequestParam("age") Integer[] age, @RequestParam("isgood") boolean[] isgood,
						@RequestParam("file") FileUpload fu) throws IOException {
		System.out.println(Arrays.toString(name) + ", " + Arrays.toString(age) + ", " + Arrays.toString(isgood));
		System.out.println(fu.getName() + ", " + fu.getFilename() + ", " + fu.getContentType() + ": " + fu.getString());
		return "OK";
	}
}

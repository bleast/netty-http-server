package controller;

import java.io.File;
import java.io.IOException;

import org.springframework.stereotype.Controller;

import com.xjd.nhs.HttpRequest;
import com.xjd.nhs.annotation.RequestBody;
import com.xjd.nhs.annotation.RequestMapping;

/**
 * @author elvis.xu
 * @since 2015-08-27 22:43
 */
@Controller
@RequestMapping(value = "/api/10")
public class TestCtrl {

	@RequestMapping(value = "/test1")
	public Object test1(@RequestBody Test1Req test1) {
		return test1;
	}

	@RequestMapping(value = "/test2", supportMultipart = true)
	public Object test2(HttpRequest request) {
		try {
			request.getUploadedFiles().get(0).renameTo(new File("D:/tmp/tmp.jpg"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "OK";
	}

	public static class Test1Req {
		private String name;
		private Integer age;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public Integer getAge() {
			return age;
		}

		public void setAge(Integer age) {
			this.age = age;
		}

	}
}

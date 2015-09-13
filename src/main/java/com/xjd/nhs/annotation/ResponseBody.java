package com.xjd.nhs.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResponseBody {

	Produce produce() default Produce.APPLICATION_JSON;

	String charset() default "UTF-8";

	public static enum Produce {
		TEXT_PLAIN("text/plain"), 
		TEXT_HTML("text/html"), 
		APPLICATION_JSON("application/json"), 
		APPLICATION_XML("application/xml");

		String val;

		Produce(String val) {
			this.val = val;
		}

		public String getVal() {
			return val;
		}
	}
}

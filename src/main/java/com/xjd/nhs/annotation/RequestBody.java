package com.xjd.nhs.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestBody {

	ObjectMappingAs objectMappingAs() default ObjectMappingAs.JSON;

	public static enum ObjectMappingAs {
		JSON, XML;
	}
}

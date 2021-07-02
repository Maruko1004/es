package com.hcf.nszh.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Author hx
 * @Date 2019/7/1
 **/

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface RequestLogging {
}
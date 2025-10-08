package com.github.beemerwt.mcrpg.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JankKey {
    String value();
}

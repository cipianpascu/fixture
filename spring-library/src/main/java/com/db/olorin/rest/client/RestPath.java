package com.db.olorin.rest.client;
import java.lang.annotation.*;
/** Binds a generated-client argument to a named URI path placeholder. */
@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
public @interface RestPath { String value(); }

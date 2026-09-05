package com.db.olorin.rest.client;
import java.lang.annotation.*;
/** Binds a generated-client argument to a query parameter. */
@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
public @interface RestQuery { String value(); }

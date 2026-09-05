package com.db.olorin.rest.client;
import java.lang.annotation.*;
/** Binds the generated request model argument to the HTTP request body. */
@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
public @interface RestBody { }

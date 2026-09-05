package com.db.olorin.rest.client;
import java.lang.annotation.*;
/** Binds a generated-client argument to an outbound HTTP cookie. */
@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
public @interface RestCookie { String value(); }

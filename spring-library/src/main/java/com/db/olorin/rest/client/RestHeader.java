package com.db.olorin.rest.client;
import java.lang.annotation.*;
/** Binds a generated-client argument to an outbound HTTP header. */
@Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
public @interface RestHeader { String value(); }

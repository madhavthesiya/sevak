package com.sevak.api.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Names the catalogue query behind an endpoint; it is sent to the browser as X-Sevak-Query. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ShowsQuery {
    String value();
}

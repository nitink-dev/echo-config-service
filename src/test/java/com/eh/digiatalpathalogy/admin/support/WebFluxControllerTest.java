package com.eh.digiatalpathalogy.admin.support;


import com.eh.digiatalpathalogy.admin.config.auth.SecurityPolicyConfig;
import com.eh.digiatalpathalogy.admin.exception.SystemFailureWebExceptionHandler;
import com.eh.digiatalpathalogy.admin.security.WebFluxHttpSecurityConfig;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@WebFluxTest
        (
                controllers = {},
                excludeFilters = {
                        @ComponentScan.Filter(
                                type = FilterType.ASSIGNABLE_TYPE,
                                classes = {SecurityPolicyConfig.class, WebFluxHttpSecurityConfig.class, SystemFailureWebExceptionHandler.class}
                        )
                }
        )
@Import(SecurityConfigTest.class)
public @interface WebFluxControllerTest {
    Class<?>[] controllers();   // <‑‑ required parameter
}
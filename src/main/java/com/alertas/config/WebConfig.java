package com.alertas.config;

import com.alertas.shared.idempotencia.IdempotenciaInterceptor;
import com.alertas.shared.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final IdempotenciaInterceptor idempotenciaInterceptor;

    public WebConfig(TenantInterceptor tenantInterceptor, IdempotenciaInterceptor idempotenciaInterceptor) {

        this.tenantInterceptor = tenantInterceptor;
        this.idempotenciaInterceptor = idempotenciaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // primero el tenant, la idempotencia puede necesitarlo
        registry.addInterceptor(tenantInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(idempotenciaInterceptor).addPathPatterns("/api/v1/**");
    }
}

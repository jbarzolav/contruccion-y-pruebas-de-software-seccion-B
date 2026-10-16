package com.ejemplo.backenduserapi.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
/** HU 23 - Comprobaciones de autorización para los endpoints del backend. */
@Configuration
public class AutorizacionConfig implements WebMvcConfigurer {
    private final AutorizacionInterceptor interceptor;
    public AutorizacionConfig(AutorizacionInterceptor interceptor) { this.interceptor = interceptor; }
    @Override
    public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).addPathPatterns("/api/**"); }
}

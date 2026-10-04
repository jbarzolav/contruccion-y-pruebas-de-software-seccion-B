package com.ejemplo.backenduserapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path carpetaImagenes;

    public WebConfig(
            @Value("${app.upload.dir:${java.io.tmpdir}/producto-imagenes}") Path carpetaImagenes) {
        this.carpetaImagenes = carpetaImagenes;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/imagenes/**")
                .addResourceLocations(carpetaImagenes.toUri().toString());
    }
}
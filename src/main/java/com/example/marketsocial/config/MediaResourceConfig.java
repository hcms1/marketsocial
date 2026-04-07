package com.example.marketsocial.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    private final MediaStorageProperties mediaStorageProperties;

    public MediaResourceConfig(MediaStorageProperties mediaStorageProperties) {
        this.mediaStorageProperties = mediaStorageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String mediaLocation = mediaStorageProperties.uploadDir().toUri().toString();
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaLocation);
    }
}

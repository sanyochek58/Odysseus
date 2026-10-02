package com.odysseus.workspace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/** Стабильный JSON страниц: {@code content} и {@code page} вместо внутренней структуры PageImpl. */
@Configuration(proxyBeanMethods = false)
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class PageSerializationConfig {
}

package com.andrewproject.agent.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@Data
@Slf4j
public class ApplicationConfig implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    public static ApplicationConfig getInstance() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not set yet. Ensure Spring has initialized the context before calling ApplicationConfig.getInstance()");
        }
        return applicationContext.getBean(ApplicationConfig.class);
    }

    @Value("classpath:/prompts/prompt-template-main.st")
    private Resource bookPromptTemplate;

    @Value("classpath:/prompts/book-search-prompt-template.st")
    private Resource bookSearchPromptTemplate;

    @Value("classpath:/prompts/travel-agent-prompt-template.st")
    private Resource travelPromptTemplate;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        ApplicationConfig.applicationContext = applicationContext;
    }
}

package com.coap.example;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class SimpleApplicationContextAware implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Autowired
    private SimpleCoapServer simpleCoapServer;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @PostConstruct
    public void startSecureCoapServer() {
        simpleCoapServer.start();
    }


}

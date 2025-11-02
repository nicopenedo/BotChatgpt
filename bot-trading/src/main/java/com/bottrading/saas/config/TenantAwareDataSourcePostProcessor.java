package com.bottrading.saas.config;

import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class TenantAwareDataSourcePostProcessor implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
      return new TenantAwareDataSource(dataSource);
    }
    return bean;
  }
}

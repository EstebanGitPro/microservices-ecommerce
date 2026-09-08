package com.ecommerce.order_service.config;

import com.ecommerce.order_service.service.client.InvetoryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class WebClientConf {

    @Bean
    public WebClient webClientBuilder() {
        return  WebClient.builder()
                .baseUrl("http://localhost:8082")
                .build();
    }

    @Bean
    public InvetoryClient invetoryClient(WebClient webClient) {
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(InvetoryClient.class);
    }
}

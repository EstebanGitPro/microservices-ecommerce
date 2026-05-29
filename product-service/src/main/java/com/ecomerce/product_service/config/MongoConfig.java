package com.ecomerce.product_service.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {
    @Override
    protected String getDatabaseName() {
        return "product-db";
    }

    @Override
    @Bean
    public MongoClient mongoClient(){
        MongoCredential credential = MongoCredential.createCredential(
                "root", "admin", "password".toCharArray()
        );

        MongoClientSettings settings = mongoClientSettings().builder()
                .applyConnectionString(new ConnectionString("mongodb://localhost:27017"))
                .credential(credential)
                .build();
        return MongoClients.create(settings);
    }
}

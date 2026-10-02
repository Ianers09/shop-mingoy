package edu.cit.mingoy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.UUID;

@SpringBootApplication
@EnableScheduling
public class ShopApplication {

    public static void main(String[] args) {
        String instanceId = UUID.randomUUID().toString();
        System.setProperty("shop.instance-id", instanceId);
        System.out.println("SHOP_INSTANCE_ID=" + instanceId);
        SpringApplication.run(ShopApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
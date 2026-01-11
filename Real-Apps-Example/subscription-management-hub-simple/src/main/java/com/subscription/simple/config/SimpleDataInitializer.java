package com.subscription.simple.config;

import com.subscription.simple.entity.Plan;
import com.subscription.simple.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SimpleDataInitializer implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(SimpleDataInitializer.class);
    private final PlanRepository planRepository;
    
    @Override
    public void run(String... args) {
        if (planRepository.count() == 0) {
            log.info("Initializing sample plans...");
            
            Plan basic = new Plan();
            basic.setName("Basic Plan");
            basic.setDescription("Perfect for getting started with essential features");
            basic.setPrice(new BigDecimal("9.99"));
            basic.setTier("BASIC");
            basic.setActive(true);
            
            Plan pro = new Plan();
            pro.setName("Pro Plan");
            pro.setDescription("For professionals with advanced features and priority support");
            pro.setPrice(new BigDecimal("49.99"));
            pro.setTier("PRO");
            pro.setActive(true);
            
            Plan enterprise = new Plan();
            enterprise.setName("Enterprise Plan");
            enterprise.setDescription("For large organizations with unlimited features and dedicated support");
            enterprise.setPrice(new BigDecimal("199.99"));
            enterprise.setTier("ENTERPRISE");
            enterprise.setActive(true);
            
            planRepository.saveAll(List.of(basic, pro, enterprise));
            log.info("Initialized {} plans", planRepository.count());
        }
    }
}

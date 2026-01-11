package com.subscription.hub.config;

import com.subscription.hub.entity.SubscriptionPlan;
import com.subscription.hub.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Initialize sample subscription plans for testing
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final SubscriptionPlanRepository planRepository;
    
    @Override
    public void run(String... args) {
        if (planRepository.count() == 0) {
            log.info("Initializing sample subscription plans...");
            
            SubscriptionPlan basicPlan = new SubscriptionPlan();
            basicPlan.setName("Basic Plan");
            basicPlan.setDescription("Perfect for individuals getting started. Includes 10GB storage, basic support, and core features.");
            basicPlan.setMonthlyPrice(new BigDecimal("9.99"));
            basicPlan.setAnnualPrice(new BigDecimal("99.99"));
            basicPlan.setTier(SubscriptionPlan.PlanTier.BASIC);
            basicPlan.setFeatures(List.of("10GB storage", "Email support", "Core features", "Mobile app access"));
            basicPlan.setMaxUsers(1);
            basicPlan.setStorageGB(10);
            basicPlan.setIsActive(true);
            
            SubscriptionPlan proPlan = new SubscriptionPlan();
            proPlan.setName("Pro Plan");
            proPlan.setDescription("For professionals and small teams. Includes 100GB storage, priority support, advanced features, and team collaboration tools.");
            proPlan.setMonthlyPrice(new BigDecimal("49.99"));
            proPlan.setAnnualPrice(new BigDecimal("499.99"));
            proPlan.setTier(SubscriptionPlan.PlanTier.PRO);
            proPlan.setFeatures(List.of("100GB storage", "Priority support", "Advanced features", "Team collaboration", "API access", "Analytics dashboard"));
            proPlan.setMaxUsers(5);
            proPlan.setStorageGB(100);
            proPlan.setIsActive(true);
            
            SubscriptionPlan enterprisePlan = new SubscriptionPlan();
            enterprisePlan.setName("Enterprise Plan");
            enterprisePlan.setDescription("For large organizations. Includes unlimited storage, 24/7 dedicated support, all features, custom integrations, and enterprise security.");
            enterprisePlan.setMonthlyPrice(new BigDecimal("199.99"));
            enterprisePlan.setAnnualPrice(new BigDecimal("1999.99"));
            enterprisePlan.setTier(SubscriptionPlan.PlanTier.ENTERPRISE);
            enterprisePlan.setFeatures(List.of("Unlimited storage", "24/7 dedicated support", "All features", "Custom integrations", "Enterprise security", "SLA guarantee", "Dedicated account manager"));
            enterprisePlan.setMaxUsers(null);
            enterprisePlan.setStorageGB(null);
            enterprisePlan.setIsActive(true);
            
            planRepository.saveAll(List.of(basicPlan, proPlan, enterprisePlan));
            log.info("Initialized {} subscription plans", planRepository.count());
        }
    }
}

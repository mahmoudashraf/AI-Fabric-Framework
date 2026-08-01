package com.subscription.hub.config;

import com.subscription.hub.entity.SubscriptionPlan;
import com.subscription.hub.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Initialize sample subscription plans for testing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    
    private final SubscriptionPlanRepository planRepository;
    
    @Override
    public void run(String... args) {
        if (planRepository.count() == 0) {
            log.info("Initializing sample subscription plans...");
            
            SubscriptionPlan basicPlan = SubscriptionPlan.builder()
                .name("Basic Plan")
                .description("Perfect for individuals getting started. Includes 10GB storage, basic support, and core features.")
                .monthlyPrice(new BigDecimal("9.99"))
                .annualPrice(new BigDecimal("99.99"))
                .tier(SubscriptionPlan.PlanTier.BASIC)
                .features(List.of("10GB storage", "Email support", "Core features", "Mobile app access"))
                .maxUsers(1)
                .storageGB(10)
                .isActive(true)
                .build();
            
            SubscriptionPlan proPlan = SubscriptionPlan.builder()
                .name("Pro Plan")
                .description("For professionals and small teams. Includes 100GB storage, priority support, advanced features, and team collaboration tools.")
                .monthlyPrice(new BigDecimal("49.99"))
                .annualPrice(new BigDecimal("499.99"))
                .tier(SubscriptionPlan.PlanTier.PRO)
                .features(List.of("100GB storage", "Priority support", "Advanced features", "Team collaboration", "API access", "Analytics dashboard"))
                .maxUsers(5)
                .storageGB(100)
                .isActive(true)
                .build();
            
            SubscriptionPlan enterprisePlan = SubscriptionPlan.builder()
                .name("Enterprise Plan")
                .description("For large organizations. Includes unlimited storage, 24/7 dedicated support, all features, custom integrations, and enterprise security.")
                .monthlyPrice(new BigDecimal("199.99"))
                .annualPrice(new BigDecimal("1999.99"))
                .tier(SubscriptionPlan.PlanTier.ENTERPRISE)
                .features(List.of("Unlimited storage", "24/7 dedicated support", "All features", "Custom integrations", "Enterprise security", "SLA guarantee", "Dedicated account manager"))
                .maxUsers(null) // Unlimited
                .storageGB(null) // Unlimited
                .isActive(true)
                .build();
            
            planRepository.saveAll(List.of(basicPlan, proPlan, enterprisePlan));
            log.info("Initialized {} subscription plans", planRepository.count());
        }
    }
}

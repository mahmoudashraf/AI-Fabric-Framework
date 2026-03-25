package com.ai.fabric.realapps.chat.migration.service;

import com.ai.fabric.realapps.chat.accounts.repo.AccountRepository;
import com.ai.fabric.realapps.chat.addresses.repo.AddressRepository;
import com.ai.fabric.realapps.chat.cart.repo.CartItemRepository;
import com.ai.fabric.realapps.chat.cart.repo.CartRepository;
import com.ai.fabric.realapps.chat.catalog.repo.ProductRepository;
import com.ai.fabric.realapps.chat.orders.repo.OrderItemRepository;
import com.ai.fabric.realapps.chat.orders.repo.PurchaseOrderRepository;
import com.ai.fabric.realapps.chat.payments.repo.PaymentRepository;
import com.ai.fabric.realapps.chat.policies.repo.PolicyRepository;
import com.ai.fabric.realapps.chat.promotions.repo.CouponRepository;
import com.ai.fabric.realapps.chat.returns.repo.ReturnRequestRepository;
import com.ai.fabric.realapps.chat.reviews.repo.ReviewRepository;
import com.ai.fabric.realapps.chat.shipping.repo.ShipmentRepository;
import com.ai.fabric.realapps.chat.support.repo.SupportTicketRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DemoResetService {

    private final ReturnRequestRepository returnRequestRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final OrderItemRepository orderItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;

    private final SupportTicketRepository supportTicketRepository;
    private final AddressRepository addressRepository;
    private final AccountRepository accountRepository;

    private final ReviewRepository reviewRepository;
    private final CouponRepository couponRepository;
    private final PolicyRepository policyRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Map<String, Object> clearConnectorData() {
        Map<String, Object> deleted = new LinkedHashMap<>();

        // Children first (FK constraints).
        deleted.put("returnRequests", deleteAll(returnRequestRepository));
        deleted.put("payments", deleteAll(paymentRepository));
        deleted.put("shipments", deleteAll(shipmentRepository));
        deleted.put("orderItems", deleteAll(orderItemRepository));
        deleted.put("purchaseOrders", deleteAll(purchaseOrderRepository));

        deleted.put("cartItems", deleteAll(cartItemRepository));
        deleted.put("carts", deleteAll(cartRepository));

        deleted.put("supportTickets", deleteAll(supportTicketRepository));
        deleted.put("addresses", deleteAll(addressRepository));
        deleted.put("accounts", deleteAll(accountRepository));

        deleted.put("reviews", deleteAll(reviewRepository));
        deleted.put("coupons", deleteAll(couponRepository));
        deleted.put("policies", deleteAll(policyRepository));
        deleted.put("products", deleteAll(productRepository));

        return Map.of(
            "success", true,
            "deleted", deleted
        );
    }

    private long deleteAll(org.springframework.data.jpa.repository.JpaRepository<?, ?> repository) {
        long count = repository.count();
        if (count > 0) {
            repository.deleteAllInBatch();
        }
        return count;
    }
}


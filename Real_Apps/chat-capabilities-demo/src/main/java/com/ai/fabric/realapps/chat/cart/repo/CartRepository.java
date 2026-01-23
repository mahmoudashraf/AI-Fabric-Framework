package com.ai.fabric.realapps.chat.cart.repo;

import com.ai.fabric.realapps.chat.cart.domain.Cart;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findFirstByUserIdAndStatusOrderByUpdatedAtDesc(String userId, Cart.Status status);

    List<Cart> findByUserIdOrderByUpdatedAtDesc(String userId);
}


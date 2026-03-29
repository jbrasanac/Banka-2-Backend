package rs.raf.banka2_bek.order.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StopOrderActivationService {

    private static final Logger log = LoggerFactory.getLogger(StopOrderActivationService.class);

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;

    @Transactional
    public void checkAndActivateStopOrders() {
        log.info("Starting stop order activation check...");

        // 1. Dohvatiti sve APPROVED naloge koji nisu zavrseni [cite: 58, 68]
        List<Order> activeOrders = orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED);

        // Filtriramo samo STOP i STOP_LIMIT tipove [cite: 69]
        List<Order> stopOrders = activeOrders.stream()
                .filter(o -> o.getOrderType() == OrderType.STOP || o.getOrderType() == OrderType.STOP_LIMIT)
                .toList();

        if (stopOrders.isEmpty()) {
            return;
        }

        for (Order order : stopOrders) {
            try {
                // 2a. Dohvatiti azuriranu cenu listinga [cite: 70]
                Listing listing = listingRepository.findById(order.getListing().getId()).orElse(null);
                if (listing == null) {
                    log.warn("Listing not found for order #{}. Skipping.", order.getId());
                    continue;
                }

                // 2b. Dohvatiti trenutnu trzisnu cenu
                BigDecimal currentPrice = listing.getPrice();
                if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                if (order.getStopValue() == null) {
                    log.warn("Stop order #{} is missing stopValue. Skipping.", order.getId());
                    continue;
                }

                // 2c. Provera stop uslova po specifikaciji [cite: 72, 74]
                boolean shouldActivate = false;
                if (order.getDirection() == OrderDirection.BUY) {
                    // BUY stop: aktivira se kad currentPrice >= stopValue [cite: 72]
                    if (currentPrice.compareTo(order.getStopValue()) >= 0) {
                        shouldActivate = true;
                    }
                } else if (order.getDirection() == OrderDirection.SELL) {
                    // SELL stop: aktivira se kad currentPrice <= stopValue [cite: 74]
                    if (currentPrice.compareTo(order.getStopValue()) <= 0) {
                        shouldActivate = true;
                    }
                }

                // 2d. Aktivacija naloga ako je uslov ispunjen
                if (shouldActivate) {
                    OrderType originalType = order.getOrderType();

                    if (originalType == OrderType.STOP) {
                        // STOP postaje MARKET [cite: 76]
                        order.setOrderType(OrderType.MARKET);
                        order.setPricePerUnit(currentPrice);
                    } else if (originalType == OrderType.STOP_LIMIT) {
                        // STOP_LIMIT postaje LIMIT
                        order.setOrderType(OrderType.LIMIT);
                        order.setPricePerUnit(order.getLimitValue());
                    }

                    // 2e. Azuriranje metapodataka i cuvanje
                    order.setLastModification(LocalDateTime.now());
                    orderRepository.save(order);

                    log.info("Stop order #{} activated: {} -> {}, trigger price: {}, stop value: {}",
                            order.getId(), originalType, order.getOrderType(),
                            currentPrice, order.getStopValue());
                }

            } catch (Exception e) {
                log.error("Critical error processing stop order #{}: {}", order.getId(), e.getMessage());
            }
        }
    }
}
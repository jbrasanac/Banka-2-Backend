package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StopOrderActivationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private StopOrderActivationService stopOrderActivationService;

    // 1. TEST: STOP SELL - Uspešna aktivacija
    @Test
    void testCheckAndActivate_StopSell_Success() {
        Listing stock = new Listing();
        stock.setId(2L);
        stock.setPrice(new BigDecimal("95.00")); // Cena je pala na 95

        Order order = new Order();
        order.setId(20L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.SELL);
        order.setStopValue(new BigDecimal("100.00")); // Stop na 100 (aktiviraj ako je <= 100)
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(2L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.MARKET, order.getOrderType());
        verify(orderRepository, times(1)).save(order);
    }

    // 2. TEST: STOP_LIMIT BUY - Konverzija u LIMIT
    @Test
    void testCheckAndActivate_StopLimitBuy_ToLimit() {
        Listing stock = new Listing();
        stock.setId(3L);
        stock.setPrice(new BigDecimal("210.00"));

        Order order = new Order();
        order.setId(30L);
        order.setOrderType(OrderType.STOP_LIMIT);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("200.00"));
        order.setLimitValue(new BigDecimal("205.00")); // Limit koji treba da se postavi
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(3L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        assertEquals(OrderType.LIMIT, order.getOrderType());
        assertEquals(new BigDecimal("205.00"), order.getPricePerUnit()); // Postavlja se limitValue
        verify(orderRepository, times(1)).save(order);
    }

    // 3. TEST: Nalog se ne aktivira jer uslov nije ispunjen
    @Test
    void testCheckAndActivate_ConditionNotMet_NoAction() {
        Listing stock = new Listing();
        stock.setId(4L);
        stock.setPrice(new BigDecimal("140.00")); // Cena je 140

        Order order = new Order();
        order.setId(40L);
        order.setOrderType(OrderType.STOP);
        order.setDirection(OrderDirection.BUY);
        order.setStopValue(new BigDecimal("150.00")); // Čeka 150, ali je tek na 140
        order.setListing(stock);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(4L)).thenReturn(Optional.of(stock));

        stopOrderActivationService.checkAndActivateStopOrders();

        // Tip mora ostati STOP, a save se ne sme pozvati za ovaj nalog
        assertEquals(OrderType.STOP, order.getOrderType());
        verify(orderRepository, never()).save(order);
    }

    // 4. TEST: Edge case - Listing ne postoji u bazi
    @Test
    void testCheckAndActivate_ListingNotFound_Skip() {
        Order order = new Order();
        order.setId(50L);
        order.setListing(new Listing()); // Prazan listing
        order.getListing().setId(999L);
        order.setOrderType(OrderType.STOP);
        order.setStatus(OrderStatus.APPROVED);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED)).thenReturn(List.of(order));
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        stopOrderActivationService.checkAndActivateStopOrders();

        // Proveravamo da save nije pozvan jer je listing null
        verify(orderRepository, never()).save(any(Order.class));
    }
}
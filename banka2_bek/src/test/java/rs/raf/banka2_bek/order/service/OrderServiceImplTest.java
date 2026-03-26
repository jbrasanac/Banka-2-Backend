package rs.raf.banka2_bek.order.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.dto.CreateOrderDto;
import rs.raf.banka2_bek.order.dto.OrderDto;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.order.service.implementation.OrderServiceImpl;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("OrderServiceImpl — createOrder")
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private OrderValidationService orderValidationService;
    @Mock private ListingPriceService listingPriceService;
    @Mock private FundsVerificationService fundsVerificationService;
    @Mock private OrderStatusService orderStatusService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Listing testListing;
    private Client testClient;
    private Employee testEmployee;

    @BeforeEach
    void setUp() {
        testListing = new Listing();
        testListing.setId(1L);
        testListing.setTicker("AAPL");
        testListing.setName("Apple Inc.");
        testListing.setListingType(ListingType.STOCK);
        testListing.setPrice(new BigDecimal("150"));
        testListing.setAsk(new BigDecimal("151"));
        testListing.setBid(new BigDecimal("149"));
        testListing.setExchangeAcronym("NASDAQ");

        testClient = new Client();
        testClient.setId(42L);
        testClient.setEmail("client@test.com");

        testEmployee = new Employee();
        testEmployee.setId(99L);
        testEmployee.setEmail("agent@test.com");
    }

    private void mockSecurityContext(String email) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(email);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    private CreateOrderDto validMarketBuyDto() {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setListingId(1L);
        dto.setOrderType("MARKET");
        dto.setDirection("BUY");
        dto.setQuantity(5);
        dto.setContractSize(1);
        dto.setAccountId(100L);
        return dto;
    }

    private Order savedOrder(CreateOrderDto dto, Listing listing, OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setListing(listing);
        order.setOrderType(OrderType.MARKET);
        order.setDirection(OrderDirection.BUY);
        order.setQuantity(dto.getQuantity());
        order.setContractSize(dto.getContractSize());
        order.setPricePerUnit(new BigDecimal("151"));
        order.setApproximatePrice(new BigDecimal("755.0000"));
        order.setStatus(status);
        order.setApprovedBy(status == OrderStatus.APPROVED ? "No need for approval" : null);
        order.setAllOrNone(false);
        order.setMargin(false);
        order.setAfterHours(false);
        order.setDone(false);
        order.setRemainingPortions(dto.getQuantity());
        return order;
    }

    @Nested
    @DisplayName("CLIENT kreiranje ordera")
    class ClientCreateOrder {

        @Test
        @DisplayName("CLIENT MARKET BUY → status APPROVED, approvedBy='No need for approval'")
        void clientMarketBuyApproved() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("CLIENT", 42L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrderDto result = orderService.createOrder(dto);

            assertNotNull(result);
            assertEquals("APPROVED", result.getStatus());
            assertEquals("No need for approval", result.getApprovedBy());
            assertEquals("CLIENT", result.getUserRole());

            verify(orderRepository).save(any(Order.class));
            // CLIENT → usedLimit se NE ažurira
            verify(actuaryInfoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("AGENT kreiranje ordera")
    class AgentCreateOrder {

        @Test
        @DisplayName("AGENT — APPROVED → usedLimit se ažurira")
        void agentApprovedUpdatesUsedLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("agent@test.com");

            ActuaryInfo agentInfo = new ActuaryInfo();
            agentInfo.setActuaryType(ActuaryType.AGENT);
            agentInfo.setUsedLimit(new BigDecimal("1000"));
            agentInfo.setDailyLimit(new BigDecimal("10000"));
            agentInfo.setNeedApproval(false);

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("EMPLOYEE", 99L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.APPROVED);
            when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(agentInfo));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrderDto result = orderService.createOrder(dto);

            assertEquals("APPROVED", result.getStatus());
            // Proveri da je usedLimit ažuriran
            verify(actuaryInfoRepository).save(agentInfo);
            assertEquals(new BigDecimal("1755.0000"), agentInfo.getUsedLimit());
        }

        @Test
        @DisplayName("AGENT — PENDING → usedLimit se NE ažurira")
        void agentPendingDoesNotUpdateUsedLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("agent@test.com");

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("EMPLOYEE", 99L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.PENDING);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            OrderDto result = orderService.createOrder(dto);

            assertEquals("PENDING", result.getStatus());
            assertNull(result.getApprovedBy());
            verify(actuaryInfoRepository, never()).save(any());
        }

        @Test
        @DisplayName("AGENT APPROVED koji je SUPERVISOR — usedLimit se NE ažurira")
        void supervisorApprovedDoesNotUpdateUsedLimit() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("agent@test.com");

            ActuaryInfo supervisorInfo = new ActuaryInfo();
            supervisorInfo.setActuaryType(ActuaryType.SUPERVISOR);

            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus("EMPLOYEE", 99L, new BigDecimal("755.0000"))).thenReturn(OrderStatus.APPROVED);
            when(orderStatusService.getAgentInfo(99L)).thenReturn(Optional.of(supervisorInfo));
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            orderService.createOrder(dto);

            // Supervisor nije AGENT, ne ažuriramo usedLimit
            verify(actuaryInfoRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Greške")
    class ErrorCases {

        @Test
        @DisplayName("Listing ne postoji → EntityNotFoundException")
        void listingNotFound() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.createOrder(dto));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Validacija baca grešku → ne nastavlja se")
        void validationFailurePropagates() {
            CreateOrderDto dto = validMarketBuyDto();
            doThrow(new IllegalArgumentException("Invalid order type or direction"))
                    .when(orderValidationService).validate(dto);

            assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(dto));
            verify(listingRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("FundsVerification baca grešku → order se ne čuva")
        void fundsVerificationFailurePropagates() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            doThrow(new IllegalArgumentException("Insufficient funds"))
                    .when(fundsVerificationService).verify(any(), any(), any(), any(), any(), any());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> orderService.createOrder(dto));
            assertEquals("Insufficient funds", ex.getMessage());
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Sistemska polja ordera")
    class SystemFields {

        @Test
        @DisplayName("Order se čuva sa isDone=false, remainingPortions=quantity, createdAt != null")
        void systemFieldsSetCorrectly() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus(any(), any(), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            orderService.createOrder(dto);

            verify(orderRepository).save(argThat(order ->
                    !order.isDone() &&
                            order.getRemainingPortions().equals(dto.getQuantity()) &&
                            order.getCreatedAt() != null &&
                            order.getLastModification() != null
            ));
        }

        @Test
        @DisplayName("userId i userRole se ispravno postavljaju za CLIENT")
        void userIdAndRoleSetForClient() {
            CreateOrderDto dto = validMarketBuyDto();
            mockSecurityContext("client@test.com");

            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));
            when(listingRepository.findById(1L)).thenReturn(Optional.of(testListing));
            when(listingPriceService.getPricePerUnit(any(), any(), any(), any())).thenReturn(new BigDecimal("151"));
            when(listingPriceService.calculateApproximatePrice(anyInt(), any(), anyInt())).thenReturn(new BigDecimal("755.0000"));
            when(orderStatusService.determineStatus(any(), any(), any())).thenReturn(OrderStatus.APPROVED);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(1L);
                return o;
            });

            orderService.createOrder(dto);

            verify(orderRepository).save(argThat(order ->
                    order.getUserId().equals(42L) &&
                            "CLIENT".equals(order.getUserRole())
            ));
        }



    }

    @Nested
    @DisplayName("Odobravanje ordera")
    class ApproveOrder {

        private Order pendingOrder;
        private Employee supervisor;
        private Listing listing;

        @BeforeEach
        void setUp() {
            listing = new Listing();
            listing.setId(1L);
            listing.setTicker("AAPL");
            listing.setListingType(ListingType.STOCK);
            listing.setSettlementDate(null);

            supervisor = new Employee();
            supervisor.setId(10L);
            supervisor.setFirstName("Nina");
            supervisor.setLastName("Nikolic");
            supervisor.setEmail("nina@bank.com");

            pendingOrder = new Order();
            pendingOrder.setId(1L);
            pendingOrder.setStatus(OrderStatus.PENDING);
            pendingOrder.setListing(listing);
            pendingOrder.setUserId(5L);
            pendingOrder.setUserRole("EMPLOYEE");

            mockSecurityContext("nina@bank.com");
            lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(supervisor));

        }

        @Test
        @DisplayName("PENDING order se uspesno odobrava")
        void approveOrder_success() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
            assertNotNull(result.getLastModification());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Order ne postoji → EntityNotFoundException")
        void approveOrder_orderNotFound() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.approveOrder(99L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order nije PENDING → IllegalStateException")
        void approveOrder_orderNotPending() {
            pendingOrder.setStatus(OrderStatus.APPROVED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.approveOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order DECLINED → IllegalStateException")
        void approveOrder_orderDeclined() {
            pendingOrder.setStatus(OrderStatus.DECLINED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.approveOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Settlement date prosao → automatski DECLINED")
        void approveOrder_settlementDatePassed_automaticallyDeclines() {
            listing.setSettlementDate(java.time.LocalDate.now().minusDays(1));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
            assertNotNull(result.getLastModification());
        }

        @Test
        @DisplayName("Settlement date danas → APPROVED")
        void approveOrder_settlementDateToday_approves() {
            listing.setSettlementDate(java.time.LocalDate.now());
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
        }

        @Test
        @DisplayName("Settlement date u buducnosti → APPROVED")
        void approveOrder_settlementDateFuture_approves() {
            listing.setSettlementDate(java.time.LocalDate.now().plusDays(10));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
        }

        @Test
        @DisplayName("Nema settlement date (akcije) → APPROVED")
        void approveOrder_noSettlementDate_approves() {
            listing.setSettlementDate(null);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.approveOrder(1L);

            assertEquals("APPROVED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
        }
    }
    @Nested
    @DisplayName("Odbijanje ordera")
    class DeclineOrder {

        private Order pendingOrder;
        private Employee supervisor;
        private Listing listing;

        @BeforeEach
        void setUp() {
            listing = new Listing();
            listing.setId(1L);
            listing.setTicker("AAPL");
            listing.setListingType(ListingType.STOCK);
            listing.setSettlementDate(null);

            supervisor = new Employee();
            supervisor.setId(10L);
            supervisor.setFirstName("Nina");
            supervisor.setLastName("Nikolic");
            supervisor.setEmail("nina@bank.com");

            pendingOrder = new Order();
            pendingOrder.setId(1L);
            pendingOrder.setStatus(OrderStatus.PENDING);
            pendingOrder.setListing(listing);
            pendingOrder.setUserId(5L);
            pendingOrder.setUserRole("EMPLOYEE");

            mockSecurityContext("nina@bank.com");
            lenient().when(employeeRepository.findById(10L)).thenReturn(Optional.of(supervisor));
        }

        @Test
        @DisplayName("PENDING order se uspesno odbija")
        void declineOrder_success() {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(clientRepository.findByEmail("nina@bank.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("nina@bank.com")).thenReturn(Optional.of(supervisor));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderDto result = orderService.declineOrder(1L);

            assertEquals("DECLINED", result.getStatus());
            assertEquals("Nina Nikolic", result.getApprovedBy());
            assertNotNull(result.getLastModification());
            verify(orderRepository).save(any(Order.class));
        }

        @Test
        @DisplayName("Order ne postoji → EntityNotFoundException")
        void declineOrder_orderNotFound() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.declineOrder(99L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order nije PENDING → IllegalStateException")
        void declineOrder_orderNotPending() {
            pendingOrder.setStatus(OrderStatus.APPROVED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.declineOrder(1L));
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Order je vec DECLINED → IllegalStateException")
        void declineOrder_orderAlreadyDeclined() {
            pendingOrder.setStatus(OrderStatus.DECLINED);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            assertThrows(IllegalStateException.class, () -> orderService.declineOrder(1L));
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Task 6 – getAllOrders")
    class GetAllOrdersTests {

        private Order makeOrder(Long id, OrderStatus status) {
            Order o = new Order();
            o.setId(id);
            o.setUserId(1L);
            o.setStatus(status);
            o.setUserRole("EMPLOYEE");
            o.setOrderType(OrderType.MARKET);
            o.setDirection(OrderDirection.BUY);
            o.setQuantity(10);
            o.setContractSize(1);
            o.setPricePerUnit(BigDecimal.valueOf(100));
            o.setDone(false);
            o.setRemainingPortions(10);
            o.setCreatedAt(java.time.LocalDateTime.now());
            o.setLastModification(java.time.LocalDateTime.now());
            o.setListing(testListing);
            return o;
        }

        @Test
        @DisplayName("Status ALL returns all orders")
        void getAllOrders_statusAll_returnsAllOrders() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, OrderStatus.PENDING), makeOrder(2L, OrderStatus.APPROVED))));

            Page<OrderDto> result = orderService.getAllOrders("ALL", 0, 20);

            assertEquals(2, result.getTotalElements());
            verify(orderRepository).findAll(pageable);
            verify(orderRepository, never()).findByStatus(any(), any());
        }

        @Test
        @DisplayName("Status null returns all orders")
        void getAllOrders_statusNull_returnsAllOrders() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, OrderStatus.DONE))));

            Page<OrderDto> result = orderService.getAllOrders(null, 0, 20);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("Filters by PENDING status")
        void getAllOrders_statusPending_filtersByStatus() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findByStatus(OrderStatus.PENDING, pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, OrderStatus.PENDING))));

            Page<OrderDto> result = orderService.getAllOrders("PENDING", 0, 20);

            assertEquals(1, result.getTotalElements());
            assertEquals("PENDING", result.getContent().get(0).getStatus());
        }

        @Test
        @DisplayName("Invalid status throws IllegalArgumentException")
        void getAllOrders_invalidStatus_throwsException() {
            assertThrows(IllegalArgumentException.class,
                    () -> orderService.getAllOrders("INVALID", 0, 20));
        }
    }

    @Nested
    @DisplayName("Task 7 – getMyOrders")
    class GetMyOrdersTests {

        private Order makeOrder(Long id, Long userId) {
            Order o = new Order();
            o.setId(id);
            o.setUserId(userId);
            o.setStatus(OrderStatus.APPROVED);
            o.setUserRole("CLIENT");
            o.setOrderType(OrderType.MARKET);
            o.setDirection(OrderDirection.BUY);
            o.setQuantity(1);
            o.setContractSize(1);
            o.setPricePerUnit(BigDecimal.valueOf(100));
            o.setDone(false);
            o.setRemainingPortions(1);
            o.setCreatedAt(java.time.LocalDateTime.now());
            o.setLastModification(java.time.LocalDateTime.now());
            o.setListing(testListing);
            return o;
        }

        @Test
        @DisplayName("Client sees only their own orders")
        void getMyOrders_client_returnsOnlyHisOrders() {
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));

            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findByUserId(42L, pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(1L, 42L))));

            Page<OrderDto> result = orderService.getMyOrders(0, 20);

            assertEquals(1, result.getTotalElements());
            verify(orderRepository).findByUserId(42L, pageable);
        }

        @Test
        @DisplayName("Employee sees only their own orders")
        void getMyOrders_employee_returnsOnlyHisOrders() {
            mockSecurityContext("agent@test.com");
            when(clientRepository.findByEmail("agent@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("agent@test.com")).thenReturn(Optional.of(testEmployee));

            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            when(orderRepository.findByUserId(99L, pageable))
                    .thenReturn(new PageImpl<>(List.of(makeOrder(5L, 99L))));

            Page<OrderDto> result = orderService.getMyOrders(0, 20);

            assertEquals(1, result.getTotalElements());
        }

        @Test
        @DisplayName("User not found throws EntityNotFoundException")
        void getMyOrders_userNotFound_throwsException() {
            mockSecurityContext("unknown@test.com");
            when(clientRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.getMyOrders(0, 20));
        }
    }

    @Nested
    @DisplayName("Task 8 – getOrderById")
    class GetOrderByIdTests {

        private Order makeOrder(Long id, Long userId) {
            Order o = new Order();
            o.setId(id);
            o.setUserId(userId);
            o.setStatus(OrderStatus.APPROVED);
            o.setUserRole("CLIENT");
            o.setOrderType(OrderType.MARKET);
            o.setDirection(OrderDirection.BUY);
            o.setQuantity(1);
            o.setContractSize(1);
            o.setPricePerUnit(BigDecimal.valueOf(100));
            o.setDone(false);
            o.setRemainingPortions(1);
            o.setCreatedAt(java.time.LocalDateTime.now());
            o.setLastModification(java.time.LocalDateTime.now());
            o.setListing(testListing);
            return o;
        }

        private void mockAdminContext(String email) {
            Authentication auth = mock(Authentication.class);
            when(auth.getName()).thenReturn(email);
            java.util.Collection<org.springframework.security.core.GrantedAuthority> authorities =
                    List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
            doReturn(authorities).when(auth).getAuthorities();
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(auth);
            SecurityContextHolder.setContext(ctx);
        }

        @Test
        @DisplayName("Supervisor can see any order")
        void getOrderById_supervisor_canSeeAnyOrder() {
            mockAdminContext("admin@test.com");
            when(clientRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty());
            when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testEmployee));

            Order o = makeOrder(10L, 999L);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

            OrderDto result = orderService.getOrderById(10L);

            assertNotNull(result);
            assertEquals(10L, result.getId());
        }

        @Test
        @DisplayName("User can see their own order")
        void getOrderById_ownOrder_success() {
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));

            Order o = makeOrder(3L, 42L);
            when(orderRepository.findById(3L)).thenReturn(Optional.of(o));

            OrderDto result = orderService.getOrderById(3L);

            assertNotNull(result);
            assertEquals(3L, result.getId());
        }

        @Test
        @DisplayName("User cannot see another user's order — throws IllegalStateException (403)")
        void getOrderById_otherUsersOrder_throwsForbidden() {
            mockSecurityContext("client@test.com");
            when(clientRepository.findByEmail("client@test.com")).thenReturn(Optional.of(testClient));

            Order o = makeOrder(3L, 999L);
            when(orderRepository.findById(3L)).thenReturn(Optional.of(o));

            assertThrows(IllegalStateException.class, () -> orderService.getOrderById(3L));
        }

        @Test
        @DisplayName("Order not found throws EntityNotFoundException")
        void getOrderById_notFound_throwsException() {
            mockSecurityContext("client@test.com");
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(EntityNotFoundException.class, () -> orderService.getOrderById(999L));
        }
    }
}

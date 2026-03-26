package rs.raf.banka2_bek.order.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OrderControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    @Autowired private OrderRepository orderRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private ActuaryInfoRepository actuaryInfoRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }

    @BeforeEach
    void cleanDatabase() {
        actuaryInfoRepository.deleteAll();
        orderRepository.deleteAll();
        listingRepository.deleteAll();
        employeeRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String tokenForAdmin(String email) {
        User user = new User();
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole("ADMIN");
        userRepository.save(user);
        return jwtService.generateAccessToken(user);
    }

    private String tokenForClient(String email) {
        User user = new User();
        user.setFirstName("Client");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole("CLIENT");
        userRepository.save(user);
        return jwtService.generateAccessToken(user);
    }

    private String tokenForEmployee(String email) {
        Employee emp = new Employee();
        emp.setFirstName("Emp");
        emp.setLastName("Loyee");
        emp.setEmail(email);
        emp.setPassword("x");
        emp.setSaltPassword("x");
        emp.setPhone("0600000000");
        emp.setAddress("Test");
        emp.setUsername(email);
        emp.setPosition("Agent");
        emp.setDepartment("IT");
        emp.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
        emp.setGender("M");
        emp.setActive(true);
        emp.setPermissions(Set.of());
        employeeRepository.save(emp);

        User user = new User();
        user.setFirstName("Emp");
        user.setLastName("Loyee");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole("EMPLOYEE");
        userRepository.save(user);

        return jwtService.generateAccessToken(user);
    }

    private Listing savedListing() {
        Listing l = new Listing();
        l.setTicker("AAPL");
        l.setName("Apple Inc.");
        l.setListingType(ListingType.STOCK);
        l.setPrice(BigDecimal.valueOf(150));
        l.setAsk(BigDecimal.valueOf(151));
        l.setBid(BigDecimal.valueOf(149));
        l.setExchangeAcronym("NASDAQ");
        l.setLastRefresh(LocalDateTime.now());
        return listingRepository.save(l);
    }

    private Order savedOrder(Long userId, String userRole, OrderStatus status, Listing listing) {
        Order o = new Order();
        o.setUserId(userId);
        o.setUserRole(userRole);
        o.setListing(listing);
        o.setOrderType(OrderType.MARKET);
        o.setDirection(OrderDirection.BUY);
        o.setQuantity(5);
        o.setContractSize(1);
        o.setPricePerUnit(BigDecimal.valueOf(150));
        o.setApproximatePrice(BigDecimal.valueOf(750));
        o.setStatus(status);
        o.setApprovedBy("No need for approval");
        o.setDone(false);
        o.setRemainingPortions(5);
        o.setAfterHours(false);
        o.setAllOrNone(false);
        o.setMargin(false);
        o.setCreatedAt(LocalDateTime.now());
        o.setLastModification(LocalDateTime.now());
        return orderRepository.save(o);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    // ── Task 6 – GET /orders ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Task 6 – GET /orders")
    class GetAllOrdersIntegration {

        @Test
        @DisplayName("Admin gets all orders — 200 OK")
        void adminGetsAllOrders() {
            Listing listing = savedListing();
            savedOrder(1L, "CLIENT", OrderStatus.APPROVED, listing);
            savedOrder(2L, "CLIENT", OrderStatus.PENDING, listing);

            String token = tokenForAdmin("admin@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("APPROVED");
            assertThat(response.getBody()).contains("PENDING");
        }

        @Test
        @DisplayName("Admin filters by PENDING — only pending orders returned")
        void adminFiltersByPending() {
            Listing listing = savedListing();
            savedOrder(1L, "CLIENT", OrderStatus.APPROVED, listing);
            savedOrder(2L, "CLIENT", OrderStatus.PENDING, listing);

            String token = tokenForAdmin("admin@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders?status=PENDING"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("PENDING");
            assertThat(response.getBody()).doesNotContain("APPROVED");
        }

        @Test
        @DisplayName("Admin sends invalid status — 400 Bad Request")
        void invalidStatusReturnsBadRequest() {
            String token = tokenForAdmin("admin@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders?status=INVALID"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Non-admin cannot access GET /orders — 403 Forbidden")
        void nonAdminForbidden() {
            String token = tokenForClient("client@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Unauthenticated request — 403 Forbidden")
        void unauthenticatedForbidden() {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders"),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Task 7 – GET /orders/my ──────────────────────────────────────────────

    @Nested
    @DisplayName("Task 7 – GET /orders/my")
    class GetMyOrdersIntegration {

        @Test
        @DisplayName("Client sees only their own orders — 200 OK")
        void clientSeesOwnOrders() {
            Listing listing = savedListing();

            Client client = new Client();
            client.setFirstName("Marko");
            client.setLastName("Markovic");
            client.setEmail("marko@test.com");
            client.setPassword("x");
            client.setSaltPassword("dummy-salt");
            client.setDateOfBirth(java.time.LocalDate.of(1990, 1, 1));
            client.setGender("M");
            client.setAddress("Test");
            client.setPhone("0600000001");
            client.setActive(true);
            clientRepository.save(client);

            savedOrder(client.getId(), "CLIENT", OrderStatus.APPROVED, listing);
            savedOrder(999L, "CLIENT", OrderStatus.APPROVED, listing); // tuđi

            String token = tokenForClient("marko@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/my"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            // Samo 1 order pripada ovom klijentu
            assertThat(response.getBody()).contains("\"totalElements\":1");
        }

        @Test
        @DisplayName("Employee sees only their own orders — 200 OK")
        void employeeSeesOwnOrders() {
            Listing listing = savedListing();
            String email = "agent@test.com";
            String token = tokenForEmployee(email);
            Employee emp = employeeRepository.findByEmail(email).orElseThrow();

            savedOrder(emp.getId(), "EMPLOYEE", OrderStatus.PENDING, listing);
            savedOrder(999L, "EMPLOYEE", OrderStatus.PENDING, listing); // tuđi

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/my"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"totalElements\":1");
        }

        @Test
        @DisplayName("Unauthenticated request — 403 Forbidden")
        void unauthenticatedForbidden() {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/my"),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ── Task 8 – GET /orders/{id} ────────────────────────────────────────────

    @Nested
    @DisplayName("Task 8 – GET /orders/{id}")
    class GetOrderByIdIntegration {

        @Test
        @DisplayName("Admin can see any order — 200 OK")
        void adminCanSeeAnyOrder() {
            Listing listing = savedListing();
            Order order = savedOrder(999L, "CLIENT", OrderStatus.APPROVED, listing);

            String token = tokenForAdmin("admin@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/" + order.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"id\":" + order.getId());
        }

        @Test
        @DisplayName("Client can see their own order — 200 OK")
        void clientCanSeeOwnOrder() {
            Listing listing = savedListing();

            Client client = new Client();
            client.setFirstName("Ana");
            client.setLastName("Anic");
            client.setEmail("ana@test.com");
            client.setPassword("x");
            client.setSaltPassword("dummy-salt");
            client.setDateOfBirth(java.time.LocalDate.of(1995, 3, 15));
            client.setGender("F");
            client.setAddress("Test");
            client.setPhone("0600000002");
            client.setActive(true);
            clientRepository.save(client);

            Order order = savedOrder(client.getId(), "CLIENT", OrderStatus.APPROVED, listing);

            String token = tokenForClient("ana@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/" + order.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("\"id\":" + order.getId());
        }

        @Test
        @DisplayName("Client cannot see another user's order — 403 Forbidden")
        void clientCannotSeeOtherUsersOrder() {
            Listing listing = savedListing();
            Order order = savedOrder(999L, "CLIENT", OrderStatus.APPROVED, listing); // tuđi

            String token = tokenForClient("other@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/" + order.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Order not found — 404 Not Found")
        void orderNotFound() {
            String token = tokenForAdmin("admin@test.com");
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/99999"),
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(token)),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Unauthenticated request — 403 Forbidden")
        void unauthenticatedForbidden() {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/orders/1"),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
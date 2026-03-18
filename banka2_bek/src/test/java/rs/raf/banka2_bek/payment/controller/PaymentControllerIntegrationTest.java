package rs.raf.banka2_bek.payment.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.payment.repository.AccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

//TODO: ne dodavati brisanje testova tudjih u commit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
        accountRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        jdbcTemplate.update("delete from currencies");
    }

    @Test
    void createPayment_sameCurrency_returnsCreatedAndPersistsSettlement() {
        User sender = createUser("sender.same@test.com");
        User receiver = createUser("receiver.same@test.com");
        Employee employee = createEmployee("employee.same@test.com", "employee.same");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromNumber = "111111111111111111";
        String toNumber = "222222222222222222";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNumber, receiver, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-1",
                  "description": "Test same-currency payment"
                }
                """.formatted(fromNumber, toNumber);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(sender))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");

        Account fromAfter = accountRepository.findByAccountNumber(fromNumber).orElseThrow();
        Account toAfter = accountRepository.findByAccountNumber(toNumber).orElseThrow();

        assertThat(fromAfter.getBalance()).isEqualByComparingTo("900.00000");
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo("900.00000");
        assertThat(fromAfter.getDailySpending()).isEqualByComparingTo("100.00");
        assertThat(fromAfter.getMonthlySpending()).isEqualByComparingTo("100.00");

        assertThat(toAfter.getBalance()).isEqualByComparingTo("600.00000");
        assertThat(toAfter.getAvailableBalance()).isEqualByComparingTo("600.00000");

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void createPayment_crossCurrency_returnsCreatedAndAppliesFeeAndFxRate() {
        User sender = createUser("sender.fx@test.com");
        User receiver = createUser("receiver.fx@test.com");
        Employee employee = createEmployee("employee.fx@test.com", "employee.fx");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");
        Currency usd = ensureCurrency("USD", "US Dollar", "$", "US");

        String fromNumber = "333333333333333333";
        String toNumber = "444444444444444444";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("1000.00"));
        createAccount(toNumber, receiver, employee, usd, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-FX",
                  "description": "Test cross-currency payment"
                }
                """.formatted(fromNumber, toNumber);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(sender))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"");

        Account fromAfter = accountRepository.findByAccountNumber(fromNumber).orElseThrow();
        Account toAfter = accountRepository.findByAccountNumber(toNumber).orElseThrow();

        // 0.5% fee on 100.00 => total debit 100.50000
        assertThat(fromAfter.getBalance()).isEqualByComparingTo("899.50000");
        assertThat(fromAfter.getAvailableBalance()).isEqualByComparingTo("899.50000");

        // Stored with scale 4 in DB => 608.0184
        assertThat(toAfter.getBalance()).isEqualByComparingTo("608.0184");
        assertThat(toAfter.getAvailableBalance()).isEqualByComparingTo("608.0184");

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void createPayment_rejectsWhenUnauthenticated() {
        String payload = """
                {
                  "fromAccount": "111111111111111111",
                  "toAccount": "222222222222222222",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "description": "No auth"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void createPayment_returnsBadRequestWhenPayloadIsInvalid() {
        User sender = createUser("sender.invalid@test.com");
        Employee employee = createEmployee("employee.invalid@test.com", "employee.invalid");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        createAccount("555555555555555555", sender, employee, eur, new BigDecimal("1000.00"));

        // Missing required description
        String payload = """
                {
                  "fromAccount": "555555555555555555",
                  "toAccount": "666666666666666666",
                  "amount": 100.00,
                  "paymentCode": "289"
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(sender))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Description is required");
    }

    @Test
    void createPayment_returnsBadRequestWhenInsufficientFunds() {
        User sender = createUser("sender.low@test.com");
        User receiver = createUser("receiver.low@test.com");
        Employee employee = createEmployee("employee.low@test.com", "employee.low");
        Currency eur = ensureCurrency("EUR", "Euro", "E", "EU");

        String fromNumber = "777777777777777777";
        String toNumber = "888888888888888888";

        createAccount(fromNumber, sender, employee, eur, new BigDecimal("20.00"));
        createAccount(toNumber, receiver, employee, eur, new BigDecimal("500.00"));

        String payload = """
                {
                  "fromAccount": "%s",
                  "toAccount": "%s",
                  "amount": 100.00,
                  "paymentCode": "289",
                  "referenceNumber": "REF-LOW",
                  "description": "Insufficient funds"
                }
                """.formatted(fromNumber, toNumber);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/payments"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(sender))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Insufficient funds");

        Account fromAfter = accountRepository.findByAccountNumber(fromNumber).orElseThrow();
        Account toAfter = accountRepository.findByAccountNumber(toNumber).orElseThrow();

        assertThat(fromAfter.getBalance()).isEqualByComparingTo("20.00");
        assertThat(toAfter.getBalance()).isEqualByComparingTo("500.00");
        assertThat(paymentRepository.count()).isEqualTo(0);
        assertThat(transactionRepository.count()).isEqualTo(0);
    }

    private HttpHeaders jsonHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private User createUser(String email) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole("CLIENT");
        return userRepository.save(user);
    }

    private Employee createEmployee(String email, String username) {
        Employee employee = Employee.builder()
                .firstName("Emp")
                .lastName("Test")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("+381600000000")
                .address("Test")
                .username(username)
                .password("x")
                .saltPassword("salt")
                .position("QA")
                .department("IT")
                .active(true)
                .permissions(Set.of("VIEW_STOCKS"))
                .build();
        return employeeRepository.save(employee);
    }

    private Currency ensureCurrency(String code, String name, String symbol, String country) {
        List<Long> ids = jdbcTemplate.query(
                "select id from currencies where code = ?",
                (rs, rowNum) -> rs.getLong(1),
                code
        );

        Long id;
        if (ids.isEmpty()) {
            jdbcTemplate.update(
                    "insert into currencies(code, name, symbol, country, description, active) values (?, ?, ?, ?, ?, ?)",
                    code,
                    name,
                    symbol,
                    country,
                    "test",
                    true
            );
            id = jdbcTemplate.queryForObject("select id from currencies where code = ?", Long.class, code);
        } else {
            id = ids.get(0);
        }

        return entityManager.getReference(Currency.class, id);
    }

    private Account createAccount(String accountNumber, User owner, Employee employee, Currency currency, BigDecimal balance) {
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.CHECKING)
                .currency(currency)
                .client(owner)
                .employee(employee)
                .status(AccountStatus.ACTIVE)
                .balance(balance)
                .availableBalance(balance)
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("20000.00"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();
        return accountRepository.save(account);
    }
}

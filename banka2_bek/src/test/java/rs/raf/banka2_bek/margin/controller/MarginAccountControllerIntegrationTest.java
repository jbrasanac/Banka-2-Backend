package rs.raf.banka2_bek.margin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.margin.repository.MarginAccountRepository;
import rs.raf.banka2_bek.margin.repository.MarginTransactionRepository;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;
import rs.raf.banka2_bek.margin.model.MarginTransactionType;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MarginAccountControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MarginTransactionRepository marginTransactionRepository;

    @Autowired
    private MarginAccountRepository marginAccountRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @BeforeEach
    void cleanDatabase() {
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        marginTransactionRepository.deleteAll();
        marginAccountRepository.deleteAll();
        accountRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();
        clientRepository.deleteAll();
        currencyRepository.deleteAll();
    }

    @Test
    void createMarginAccount_returnsOK_andPersistsCalculatedFields() throws Exception {
        Client client = createClient("margin.client@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");
        Employee employee = createEmployee("creator@test.com", "creator");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777771", client, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));

        String payload = """
                {
                  "accountId": %d,
                  "initialDeposit": 5000.00
                }
                """.formatted(account.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("id").asLong()).isPositive();
        assertThat(body.path("userId").asLong()).isEqualTo(client.getId());
        assertThat(body.path("bankParticipation").decimalValue()).isEqualByComparingTo("0.50");
        assertThat(body.path("initialMargin").decimalValue()).isEqualByComparingTo("10000.0000");
        assertThat(body.path("loanValue").decimalValue()).isEqualByComparingTo("5000.0000");
        assertThat(body.path("maintenanceMargin").decimalValue()).isEqualByComparingTo("5000.0000");

        entityManager.clear();
        Account after = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(after.getAvailableBalance()).isEqualByComparingTo("5000.00");
        assertThat(after.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(marginAccountRepository.count()).isEqualTo(1L);
        assertThat(marginTransactionRepository.count()).isEqualTo(1L);
    }

    @Test
    void createMarginAccount_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>("{\"accountId\":1,\"initialDeposit\":100.00}", jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createMarginAccount_returnsForbidden_forAuthenticatedNonClient() {
        Client owner = createClient("owner@test.com");
        User nonClient = createAuthUser("employee.only@test.com", "EMPLOYEE");
        Employee employee = createEmployee("creator2@test.com", "creator2");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777772", owner, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));

        String payload = """
                {
                  "accountId": %d,
                  "initialDeposit": 100.00
                }
                """.formatted(account.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(nonClient))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Only clients can create margin accounts.");
    }

    @Test
    void createMarginAccount_returnsBadRequest_whenInsufficientFunds() {
        Client client = createClient("insufficient@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");
        Employee employee = createEmployee("creator3@test.com", "creator3");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777773", client, employee, rsd,
                new BigDecimal("10.00"), new BigDecimal("10.00"));

        String payload = """
                {
                  "accountId": %d,
                  "initialDeposit": 100.00
                }
                """.formatted(account.getId());

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Insufficient available balance");
    }

    @Test
    void createMarginAccount_returnsBadRequest_whenPayloadInvalid() {
        Client client = createClient("invalid.payload@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");

        String payload = """
                {
                  "accountId": 1
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Pocetni depozit je obavezan");
    }

    @Test
    void getMyMarginAccounts_returnsOnlyCurrentClientAccounts() throws Exception {
        Client owner = createClient("owner.margin@test.com");
        Client other = createClient("other.margin@test.com");
        User ownerUser = createAuthUser(owner.getEmail(), "CLIENT");
        Employee employee = createEmployee("creator.my@test.com", "creator.my");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");

        Account ownerAccount = createAccount("777777777777777781", owner, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        Account otherAccount = createAccount("777777777777777782", other, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));

        marginAccountRepository.save(MarginAccount.builder()
                .account(ownerAccount)
                .userId(owner.getId())
                .initialMargin(new BigDecimal("10000.0000"))
                .loanValue(new BigDecimal("5000.0000"))
                .maintenanceMargin(new BigDecimal("5000.0000"))
                .bankParticipation(new BigDecimal("0.50"))
                .status(MarginAccountStatus.ACTIVE)
                .build());

        marginAccountRepository.save(MarginAccount.builder()
                .account(otherAccount)
                .userId(other.getId())
                .initialMargin(new BigDecimal("6000.0000"))
                .loanValue(new BigDecimal("3000.0000"))
                .maintenanceMargin(new BigDecimal("3000.0000"))
                .bankParticipation(new BigDecimal("0.50"))
                .status(MarginAccountStatus.ACTIVE)
                .build());

        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/my"),
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(ownerUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).isTrue();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).path("userId").asLong()).isEqualTo(owner.getId());
        assertThat(body.get(0).path("accountNumber").asText()).isEqualTo("777777777777777781");
    }

    @Test
    void getMyMarginAccounts_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/margin-accounts/my"),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getMyMarginAccounts_returnsForbidden_forAuthenticatedNonClient() {
        User nonClient = createAuthUser("nonclient.margin@test.com", "EMPLOYEE");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/margin-accounts/my"),
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(jsonHeaders(jwtService.generateAccessToken(nonClient))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Only clients can view margin accounts.");
    }

    // ── deposit() integration tests ────────────────────────────────────────────

    @Test
    void deposit_returnsOK_andUpdatesMarginAccountValues() {
        Client client = createClient("deposit.client@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");
        Employee employee = createEmployee("deposit.emp@test.com", "dep.emp");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777791", client, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        MarginAccount marginAccount = createMarginAccount(account, client,
                MarginAccountStatus.ACTIVE, new BigDecimal("10000.0000"), new BigDecimal("5000.0000"));

        String payload = """
                { "amount": 2000.00 }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        entityManager.clear();
        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getInitialMargin()).isEqualByComparingTo("12000.0000");
        assertThat(updated.getMaintenanceMargin()).isEqualByComparingTo("6000.0000");
        assertThat(marginTransactionRepository.count()).isEqualTo(1L);
        assertThat(marginTransactionRepository.findAll().get(0).getType()).isEqualTo(MarginTransactionType.DEPOSIT);
    }

    @Test
    void deposit_returnsOK_andActivatesBLOCKEDAccount() {
        Client client = createClient("deposit.blocked@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");
        Employee employee = createEmployee("deposit.blocked.emp@test.com", "dep.bl.emp");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777792", client, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        MarginAccount marginAccount = createMarginAccount(account, client,
                MarginAccountStatus.BLOCKED, new BigDecimal("10000.0000"), new BigDecimal("5000.0000"));

        String payload = """
                { "amount": 1000.00 }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>(payload, jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        entityManager.clear();
        MarginAccount updated = marginAccountRepository.findById(marginAccount.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(MarginAccountStatus.ACTIVE);
    }

    @Test
    void deposit_returnsForbidden_whenMissingJwt() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/1/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(null)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deposit_returnsForbidden_forNonClientUser() {
        User nonClient = createAuthUser("deposit.nonclient@test.com", "EMPLOYEE");

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/1/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(jwtService.generateAccessToken(nonClient))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deposit_returnsNotFound_whenMarginAccountNotFound() {
        Client client = createClient("deposit.notfound@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/99999/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("99999");
    }

    @Test
    void deposit_returnsForbidden_whenCallerIsNotOwner() {
        Client owner = createClient("deposit.owner@test.com");
        Client attacker = createClient("deposit.attacker@test.com");
        User attackerUser = createAuthUser(attacker.getEmail(), "CLIENT");
        Employee employee = createEmployee("deposit.own.emp@test.com", "dep.own.emp");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777793", owner, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        MarginAccount marginAccount = createMarginAccount(account, owner,
                MarginAccountStatus.ACTIVE, new BigDecimal("10000.0000"), new BigDecimal("5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{\"amount\":100}", jsonHeaders(jwtService.generateAccessToken(attackerUser))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("You don't have access to this margin account.");
    }

    @Test
    void deposit_returnsBadRequest_whenAmountIsZero() {
        Client client = createClient("deposit.zero@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");
        Employee employee = createEmployee("deposit.zero.emp@test.com", "dep.zero.emp");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777794", client, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        MarginAccount marginAccount = createMarginAccount(account, client,
                MarginAccountStatus.ACTIVE, new BigDecimal("10000.0000"), new BigDecimal("5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{\"amount\":0}", jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Amount must be positive number.");
    }

    @Test
    void deposit_returnsBadRequest_whenAmountIsMissing() {
        Client client = createClient("deposit.missing@test.com");
        User user = createAuthUser(client.getEmail(), "CLIENT");
        Employee employee = createEmployee("deposit.miss.emp@test.com", "dep.miss.emp");
        Currency rsd = createCurrency("RSD", "Serbian Dinar", "RSD", "RS");
        Account account = createAccount("777777777777777795", client, employee, rsd,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"));
        MarginAccount marginAccount = createMarginAccount(account, client,
                MarginAccountStatus.ACTIVE, new BigDecimal("10000.0000"), new BigDecimal("5000.0000"));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/margin-accounts/" + marginAccount.getId() + "/deposit"),
                new HttpEntity<>("{}", jsonHeaders(jwtService.generateAccessToken(user))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Amount must be positive number.");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null && !accessToken.isBlank()) {
            headers.setBearerAuth(accessToken);
        }
        return headers;
    }

    private User createAuthUser(String email, String role) {
        User user = new User();
        user.setFirstName("Auth");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("hashed");
        user.setActive(true);
        user.setRole(role);
        return userRepository.save(user);
    }

    private Client createClient(String email) {
        return clientRepository.save(Client.builder()
                .firstName("Client")
                .lastName("User")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M")
                .email(email)
                .phone("+381600000001")
                .address("Test Address")
                .password("hashed")
                .saltPassword("salt")
                .active(true)
                .build());
    }

    private Employee createEmployee(String email, String username) {
        return employeeRepository.save(Employee.builder()
                .firstName("Emp")
                .lastName("Loyee")
                .dateOfBirth(LocalDate.of(1985, 6, 15))
                .gender("M")
                .email(email)
                .phone("+381611111111")
                .address("Office")
                .username(username)
                .password("hashed")
                .saltPassword("salt")
                .position("Agent")
                .department("Ops")
                .active(true)
                .permissions(Set.of("EMPLOYEE"))
                .build());
    }

    private Currency createCurrency(String code, String name, String symbol, String country) {
        return currencyRepository.save(Currency.builder()
                .code(code)
                .name(name)
                .symbol(symbol)
                .country(country)
                .active(true)
                .build());
    }

    private MarginAccount createMarginAccount(Account account, Client client,
                                              MarginAccountStatus status,
                                              BigDecimal initialMargin,
                                              BigDecimal maintenanceMargin) {
        return marginAccountRepository.save(MarginAccount.builder()
                .account(account)
                .userId(client.getId())
                .initialMargin(initialMargin)
                .loanValue(initialMargin.divide(new java.math.BigDecimal("2")))
                .maintenanceMargin(maintenanceMargin)
                .bankParticipation(new BigDecimal("0.50"))
                .status(status)
                .build());
    }

    private Account createAccount(String accountNumber,
                                  Client client,
                                  Employee employee,
                                  Currency currency,
                                  BigDecimal balance,
                                  BigDecimal availableBalance) {
        return accountRepository.save(Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.CHECKING)
                .currency(currency)
                .client(client)
                .employee(employee)
                .status(AccountStatus.ACTIVE)
                .balance(balance)
                .availableBalance(availableBalance)
                .dailyLimit(new BigDecimal("1000000.0000"))
                .monthlyLimit(new BigDecimal("10000000.0000"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .maintenanceFee(BigDecimal.ZERO)
                .build());
    }
}



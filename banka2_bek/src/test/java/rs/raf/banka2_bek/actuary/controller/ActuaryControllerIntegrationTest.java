package rs.raf.banka2_bek.actuary.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.ActivationTokenRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActuaryControllerIntegrationTest {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Employee agentMarko;
    private Employee agentJelena;
    private Employee agentAna;
    private Employee supervisorNina;
    private String authToken;
    private String supervisorToken;
    private String agentToken;

    @BeforeEach
    void setUp() {
        restTemplate.setRequestFactory(new JdkClientHttpRequestFactory());

        actuaryInfoRepository.deleteAll();
        activationTokenRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();


        // Kreiramo zaposlene
        agentMarko = employeeRepository.save(Employee.builder()
                .firstName("Marko").lastName("Markovic")
                .email("marko.markovic@banka.rs")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender("M").phone("+38163100200").address("Beograd")
                .username("marko.markovic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Menadzer").department("IT").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        agentJelena = employeeRepository.save(Employee.builder()
                .firstName("Jelena").lastName("Jovanovic")
                .email("jelena.jovanovic@banka.rs")
                .dateOfBirth(LocalDate.of(1992, 8, 22))
                .gender("F").phone("+38164200300").address("Novi Sad")
                .username("jelena.jovanovic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Analiticar").department("Finance").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        agentAna = employeeRepository.save(Employee.builder()
                .firstName("Ana").lastName("Markovic")
                .email("ana.markovic@banka.rs")
                .dateOfBirth(LocalDate.of(1988, 3, 10))
                .gender("F").phone("+38165300400").address("Kragujevac")
                .username("ana.markovic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Menadzer").department("Operations").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        supervisorNina = employeeRepository.save(Employee.builder()
                .firstName("Nina").lastName("Nikolic")
                .email("nina.nikolic@banka.rs")
                .dateOfBirth(LocalDate.of(1985, 11, 3))
                .gender("F").phone("+38166400500").address("Beograd")
                .username("nina.nikolic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Direktor").department("Management").active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());

        // Actuary info zapisi
        ActuaryInfo infoMarko = new ActuaryInfo();
        infoMarko.setEmployee(agentMarko);
        infoMarko.setActuaryType(ActuaryType.AGENT);
        infoMarko.setDailyLimit(new BigDecimal("100000.00"));
        infoMarko.setUsedLimit(new BigDecimal("15000.00"));
        infoMarko.setNeedApproval(false);
        actuaryInfoRepository.save(infoMarko);

        ActuaryInfo infoJelena = new ActuaryInfo();
        infoJelena.setEmployee(agentJelena);
        infoJelena.setActuaryType(ActuaryType.AGENT);
        infoJelena.setDailyLimit(new BigDecimal("50000.00"));
        infoJelena.setUsedLimit(BigDecimal.ZERO);
        infoJelena.setNeedApproval(true);
        actuaryInfoRepository.save(infoJelena);

        ActuaryInfo infoAna = new ActuaryInfo();
        infoAna.setEmployee(agentAna);
        infoAna.setActuaryType(ActuaryType.AGENT);
        infoAna.setDailyLimit(new BigDecimal("75000.00"));
        infoAna.setUsedLimit(new BigDecimal("5000.00"));
        infoAna.setNeedApproval(false);
        actuaryInfoRepository.save(infoAna);

        ActuaryInfo infoNina = new ActuaryInfo();
        infoNina.setEmployee(supervisorNina);
        infoNina.setActuaryType(ActuaryType.SUPERVISOR);
        infoNina.setDailyLimit(null);
        infoNina.setUsedLimit(null);
        infoNina.setNeedApproval(false);
        actuaryInfoRepository.save(infoNina);

        createUser("admin@banka.rs", "Admin12345", "ADMIN", "Admin", "Test");
        createUser("nina.nikolic@banka.rs", "Supervisor123", "EMPLOYEE", "Nina", "Nikolic");
        createUser("marko.markovic@banka.rs", "Agent123", "EMPLOYEE", "Marko", "Markovic");

        authToken = login("admin@banka.rs", "Admin12345");
        supervisorToken = login("nina.nikolic@banka.rs", "Supervisor123");
        agentToken = login("marko.markovic@banka.rs", "Agent123");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String login(String email, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/auth/login"),
                new HttpEntity<>(body, headers),
                String.class
        );

        String responseBody = response.getBody();
        int tokenStart = responseBody.indexOf("accessToken\":\"") + 14;
        int tokenEnd = responseBody.indexOf("\"", tokenStart);
        return responseBody.substring(tokenStart, tokenEnd);
    }

    private HttpHeaders authHeaders() {
        return authHeaders(authToken);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private void createUser(String email, String rawPassword, String role, String firstName, String lastName) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setActive(true);
        user.setRole(role);
        userRepository.save(user);
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/agents
    // ══════════════════════════════════════════════════════════════════

    @Test
    void getAgentsReturnsAllAgentsWithoutSupervisors() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("Jelena Jovanovic");
        assertThat(response.getBody()).contains("Ana Markovic");
        assertThat(response.getBody()).doesNotContain("Nina Nikolic");
    }

    @Test
    void getAgentsFiltersByEmailCaseInsensitive() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?email=MARKO.MARKOVIC"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).doesNotContain("Jelena Jovanovic");
        assertThat(response.getBody()).doesNotContain("Ana Markovic");
    }

    @Test
    void getAgentsFiltersByFirstNamePartialMatch() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?firstName=jel"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Jelena Jovanovic");
        assertThat(response.getBody()).doesNotContain("Marko Markovic");
    }

    @Test
    void getAgentsFiltersByLastNameReturnsBothMarkovics() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?lastName=markovic"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("Ana Markovic");
        assertThat(response.getBody()).doesNotContain("Jelena Jovanovic");
    }

    @Test
    void getAgentsFiltersByPosition() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?position=menadzer"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("Ana Markovic");
        assertThat(response.getBody()).doesNotContain("Jelena Jovanovic");
    }

    @Test
    void getAgentsNoMatchReturnsEmptyList() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/agents?email=nepostojeci@email.com"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("[]");
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/{employeeId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    void getActuaryInfoReturnsAgentData() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentMarko.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Marko Markovic");
        assertThat(response.getBody()).contains("\"actuaryType\":\"AGENT\"");
        assertThat(response.getBody()).contains("100000.00");
        assertThat(response.getBody()).contains("15000.00");
    }

    @Test
    void getActuaryInfoReturnsSupervisorData() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + supervisorNina.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Nina Nikolic");
        assertThat(response.getBody()).contains("\"actuaryType\":\"SUPERVISOR\"");
    }

    @Test
    void getActuaryInfoReturns404ForNonExistentEmployee() {
        assertThatThrownBy(() -> restTemplate.exchange(
                url("/actuaries/999999"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        )).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void getActuaryInfoShowsNeedApprovalCorrectly() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Jelena Jovanovic");
        assertThat(response.getBody()).contains("\"needApproval\":true");
        assertThat(response.getBody()).contains("50000.00");
    }

    @Test
    void updateAgentLimitAsSupervisorReturns200AndPersistsChanges() {
        String payload = "{\"dailyLimit\":65000.00,\"needApproval\":false}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employeeId\":" + agentJelena.getId());
        assertThat(response.getBody()).contains("65000.00");
        assertThat(response.getBody()).contains("\"needApproval\":false");

        ActuaryInfo persisted = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertThat(persisted.getDailyLimit()).isEqualByComparingTo("65000.00");
        assertThat(persisted.isNeedApproval()).isFalse();
    }

    @Test
    void updateAgentLimitDailyLimitOnlyReturns200AndPreservesNeedApproval() {
        String payload = "{\"dailyLimit\":91000.00}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employeeId\":" + agentJelena.getId());
        assertThat(response.getBody()).contains("91000.00");
        assertThat(response.getBody()).contains("\"needApproval\":true");

        ActuaryInfo persisted = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertThat(persisted.getDailyLimit()).isEqualByComparingTo("91000.00");
        assertThat(persisted.isNeedApproval()).isTrue();
    }

    @Test
    void updateAgentLimitNeedApprovalOnlyReturns200AndPreservesDailyLimit() {
        String payload = "{\"needApproval\":false}";

        ResponseEntity<String> response = restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"employeeId\":" + agentJelena.getId());
        assertThat(response.getBody()).contains("50000.00");
        assertThat(response.getBody()).contains("\"needApproval\":false");

        ActuaryInfo persisted = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertThat(persisted.getDailyLimit()).isEqualByComparingTo("50000.00");
        assertThat(persisted.isNeedApproval()).isFalse();
    }

    @Test
    void updateAgentLimitAsAgentReturns403() {
        String payload = "{\"dailyLimit\":61000.00}";

        assertThatThrownBy(() -> restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(agentToken)),
                String.class
        )).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    void updateAgentLimitWithoutAuthReturns403() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThatThrownBy(() -> restTemplate.exchange(
                url("/actuaries/" + agentJelena.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>("{\"dailyLimit\":70000}", headers),
                String.class
        )).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    void updateAgentLimitReturns404WhenTargetDoesNotExist() {
        String payload = "{\"needApproval\":true}";

        assertThatThrownBy(() -> restTemplate.exchange(
                url("/actuaries/999999/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        )).isInstanceOf(HttpClientErrorException.NotFound.class);
    }

    @Test
    void updateAgentLimitReturnsDomainErrorWhenTargetIsSupervisor() {
        Employee otherSupervisor = employeeRepository.save(Employee.builder()
                .firstName("Sara").lastName("Savic")
                .email("sara.savic@banka.rs")
                .dateOfBirth(LocalDate.of(1987, 7, 7))
                .gender("F").phone("+38166111222").address("Nis")
                .username("sara.savic")
                .password(passwordEncoder.encode("pass" + "salt"))
                .saltPassword("salt")
                .position("Direktor").department("Management").active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());

        ActuaryInfo otherSupervisorInfo = new ActuaryInfo();
        otherSupervisorInfo.setEmployee(otherSupervisor);
        otherSupervisorInfo.setActuaryType(ActuaryType.SUPERVISOR);
        otherSupervisorInfo.setDailyLimit(null);
        otherSupervisorInfo.setUsedLimit(null);
        otherSupervisorInfo.setNeedApproval(false);
        actuaryInfoRepository.save(otherSupervisorInfo);

        String payload = "{\"dailyLimit\":80000.00}";

        assertThatThrownBy(() -> restTemplate.exchange(
                url("/actuaries/" + otherSupervisor.getId() + "/limit"),
                HttpMethod.PATCH,
                new HttpEntity<>(payload, authHeaders(supervisorToken)),
                String.class
        ))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> {
                    HttpStatusCode status = ((HttpClientErrorException) ex).getStatusCode();
                    assertThat(status == HttpStatus.BAD_REQUEST || status == HttpStatus.CONFLICT).isTrue();
                });
    }
}
package rs.raf.banka2_bek.stock.controller;

import lombok.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.auth.service.JwtService;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingDailyPriceInfo;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.time.LocalDate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ListingControllerIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private final RestTemplate restTemplate = createRestTemplate();

    @Autowired private ListingRepository listingRepository;
    @Autowired private ListingDailyPriceInfoRepository dailyPriceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private Long aaplId;
    private Long forexId;

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(@NonNull ClientHttpResponse response) { return false; }
            @Override
            public boolean hasError(@NonNull HttpStatusCode statusCode) { return false; }
        });
        return rt;
    }

    @BeforeEach
    void setUp() {
        dailyPriceRepository.deleteAll();
        listingRepository.deleteAll();
        userRepository.deleteAll();
        seedListings();
        aaplId = listingRepository.findByTicker("AAPL").orElseThrow().getId();
        forexId = listingRepository.findByTicker("EUR/USD").orElseThrow().getId();
        seedHistory();
    }

    private void seedListings() {
        listingRepository.save(listing("AAPL", "Apple Inc.", ListingType.STOCK));
        listingRepository.save(listing("MSFT", "Microsoft Corp.", ListingType.STOCK));
        listingRepository.save(listing("CLJ26", "Crude Oil Jun 2026", ListingType.FUTURES));
        listingRepository.save(listing("EUR/USD", "Euro/US Dollar", ListingType.FOREX));
    }

    private Listing listing(String ticker, String name, ListingType type) {
        Listing l = new Listing();
        l.setTicker(ticker);
        l.setName(name);
        l.setListingType(type);
        l.setPrice(BigDecimal.valueOf(150));
        l.setPriceChange(BigDecimal.valueOf(3));
        return l;
    }

    private String tokenForRole(String email, String role) {
        User user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail(email);
        user.setPassword("x");
        user.setActive(true);
        user.setRole(role);
        userRepository.save(user);
        return jwtService.generateAccessToken(user);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // --- Happy path ---

    @Test
    @DisplayName("GET /listings?type=STOCK vraca paginirane akcije")
    void getStockListings_returnsPagedResults() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=STOCK", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
        assertThat(response.getBody()).contains("MSFT");
        assertThat(response.getBody()).doesNotContain("EUR/USD");
    }

    @Test
    @DisplayName("GET /listings?type=STOCK&search=apple vraca rezultate case-insensitive po imenu")
    void searchByName_caseInsensitive() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=STOCK&search=apple", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
        assertThat(response.getBody()).doesNotContain("MSFT");
    }

    @Test
    @DisplayName("GET /listings?type=STOCK&search=MS vraca rezultate po ticker-u")
    void searchByTicker_partialMatch() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=STOCK&search=MS", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("MSFT");
        assertThat(response.getBody()).doesNotContain("AAPL");
    }

    @Test
    @DisplayName("GET /listings?type=FOREX vraca forex za zaposlenog")
    void employee_canAccessForex() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=FOREX", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("EUR/USD");
    }

    @Test
    @DisplayName("GET /listings?type=STOCK vraca akcije i za klijenta")
    void client_canAccessStock() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings?type=STOCK", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
    }

    @Test
    @DisplayName("GET /listings?type=FUTURES vraca futures i za klijenta")
    void client_canAccessFutures() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings?type=FUTURES", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("CLJ26");
    }

    // --- Negative cases ---

    @Test
    @DisplayName("GET /listings?type=FOREX vraca 403 za klijenta")
    void client_forexReturns403() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings?type=FOREX", token);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    @DisplayName("GET /listings?type=INVALID vraca 400")
    void invalidType_returns400() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings?type=INVALID", token);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /listings bez tokena vraca 401/403")
    void noToken_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/listings?type=STOCK"), String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    // --- GET /listings/{id} ---

    @Test
    @DisplayName("GET /listings/{id} vraca 200 sa ispravnim podacima za zaposlenog")
    void getListingById_stock_asEmployee_returnsOk() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId, token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
    }

    @Test
    @DisplayName("GET /listings/{id} vraca DTO sa izvedenim poljima")
    void getListingById_stock_containsDerivedFields() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId, token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("changePercent");
        assertThat(response.getBody()).contains("maintenanceMargin");
    }

    @Test
    @DisplayName("GET /listings/{id} vraca FOREX listing za zaposlenog")
    void getListingById_forex_asEmployee_returnsOk() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + forexId, token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("EUR/USD");
    }

    @Test
    @DisplayName("GET /listings/{id} vraca 403 za klijenta koji pristupa FOREX listingu")
    void getListingById_forex_asClient_returns403() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings/" + forexId, token);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    @DisplayName("GET /listings/{id} vraca 200 za klijenta koji pristupa STOCK listingu")
    void getListingById_stock_asClient_returnsOk() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings/" + aaplId, token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("AAPL");
    }

    @Test
    @DisplayName("GET /listings/{id} vraca 404 za nepostojeci ID")
    void getListingById_nonExistent_returns404() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/999999", token);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /listings/{id} bez tokena vraca 401/403")
    void getListingById_noToken_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/listings/" + aaplId), String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    // --- GET /listings/{id}/history ---

    private void seedHistory() {
        Listing aapl = listingRepository.findByTicker("AAPL").orElseThrow();
        Listing forex = listingRepository.findByTicker("EUR/USD").orElseThrow();
        dailyPriceRepository.save(dailyPriceEntry(aapl, LocalDate.now()));
        dailyPriceRepository.save(dailyPriceEntry(aapl, LocalDate.now().minusDays(1)));
        dailyPriceRepository.save(dailyPriceEntry(forex, LocalDate.now()));
    }

    private ListingDailyPriceInfo dailyPriceEntry(Listing listing, LocalDate date) {
        ListingDailyPriceInfo info = new ListingDailyPriceInfo();
        info.setListing(listing);
        info.setDate(date);
        info.setPrice(BigDecimal.valueOf(150));
        info.setHigh(BigDecimal.valueOf(155));
        info.setLow(BigDecimal.valueOf(145));
        info.setChange(BigDecimal.valueOf(3));
        info.setVolume(10_000L);
        return info;
    }

    @Test
    @DisplayName("GET /listings/{id}/history?period=MONTH vraca 200 sa podacima za zaposlenog")
    void getHistory_stock_month_asEmployee_returns200() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history?period=MONTH", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("price");
    }

    @Test
    @DisplayName("GET /listings/{id}/history bez period parametra koristi MONTH kao default i vraca 200")
    void getHistory_stock_defaultPeriod_returns200() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    @Test
    @DisplayName("GET /listings/{id}/history?period=ALL vraca sve podatke")
    void getHistory_allPeriod_returns200() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history?period=ALL", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("150");
    }

    @Test
    @DisplayName("GET /listings/{id}/history?period=DAY vraca dnevne podatke")
    void getHistory_dayPeriod_returns200() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history?period=DAY", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    @Test
    @DisplayName("GET /listings/{id}/history odgovor sadrzi chart polja: date, price, high, low, change, volume")
    void getHistory_responseContainsChartFields() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history?period=ALL", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
        assertThat(response.getBody()).contains("date");
        assertThat(response.getBody()).contains("price");
        assertThat(response.getBody()).contains("high");
        assertThat(response.getBody()).contains("low");
        assertThat(response.getBody()).contains("change");
        assertThat(response.getBody()).contains("volume");
    }

    @Test
    @DisplayName("GET /listings/{id}/history?period=INVALID vraca 400")
    void getHistory_invalidPeriod_returns400() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history?period=INVALID", token);

        assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /listings/999999/history vraca 404 za nepostojeci listing")
    void getHistory_nonExistentListing_returns404() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/999999/history?period=MONTH", token);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("GET /listings/{forexId}/history vraca 403 za klijenta")
    void getHistory_forexAsClient_returns403() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings/" + forexId + "/history?period=MONTH", token);

        assertThat(response.getStatusCode()).isEqualTo(FORBIDDEN);
    }

    @Test
    @DisplayName("GET /listings/{forexId}/history vraca 200 za zaposlenog")
    void getHistory_forexAsEmployee_returns200() {
        String token = tokenForRole("emp@test.com", "EMPLOYEE");
        ResponseEntity<String> response = get("/listings/" + forexId + "/history?period=ALL", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    @Test
    @DisplayName("GET /listings/{aaplId}/history vraca 200 za klijenta sa STOCK listingom")
    void getHistory_stockAsClient_returns200() {
        String token = tokenForRole("client@test.com", "CLIENT");
        ResponseEntity<String> response = get("/listings/" + aaplId + "/history?period=MONTH", token);

        assertThat(response.getStatusCode()).isEqualTo(OK);
    }

    @Test
    @DisplayName("GET /listings/{id}/history bez tokena vraca 401/403")
    void getHistory_noToken_returnsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/listings/" + aaplId + "/history?period=MONTH"), String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }
}

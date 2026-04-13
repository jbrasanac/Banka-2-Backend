package rs.raf.banka2_bek.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 6 testovi: verifikuju da OrderExecutionService
 *  1) postuje initial delay pre prvog fill-a,
 *  2) koristi FundReservationService umesto direktne balance manipulacije,
 *  3) nastavlja sa ostalim orderima ako jedan baci exception.
 *
 * Ovi testovi su odvojeni od legacy {@code OrderExecutionServiceTest} da ne
 * naslede njegove baseline failures. Fokus je iskljucivo na Phase 6 izmene.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderExecutionServicePhase6Test {

    @Mock private OrderRepository orderRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AonValidationService aonValidationService;
    @Mock private FundReservationService fundReservationService;

    @InjectMocks
    private OrderExecutionService service;

    private Listing listing;
    private Account userAccount;
    private Account bankAccount;
    private Currency usd;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bankRegistrationNumber", "22200022");
        ReflectionTestUtils.setField(service, "initialDelaySeconds", 60L);
        ReflectionTestUtils.setField(service, "afterHoursDelaySeconds", 60L);

        usd = new Currency();
        usd.setId(1L);

        listing = new Listing();
        listing.setId(10L);
        listing.setTicker("AAPL");
        listing.setName("Apple Inc");
        listing.setAsk(new BigDecimal("100.00"));
        listing.setBid(new BigDecimal("95.00"));
        listing.setListingType(ListingType.STOCK);

        userAccount = new Account();
        userAccount.setId(1L);
        userAccount.setBalance(new BigDecimal("10000.00"));
        userAccount.setAvailableBalance(new BigDecimal("8000.00"));
        userAccount.setReservedAmount(new BigDecimal("2000.00"));
        userAccount.setCurrency(usd);

        Company bankCompany = new Company();
        bankCompany.setId(99L);
        bankAccount = new Account();
        bankAccount.setId(999L);
        bankAccount.setCompany(bankCompany);
        bankAccount.setCurrency(usd);
        bankAccount.setBalance(new BigDecimal("500000.00"));
        bankAccount.setAvailableBalance(new BigDecimal("500000.00"));
    }

    private Order buyOrder(LocalDateTime approvedAt) {
        Order o = new Order();
        o.setId(100L);
        o.setUserId(42L);
        o.setUserRole("CLIENT");
        o.setListing(listing);
        o.setOrderType(OrderType.MARKET);
        o.setDirection(OrderDirection.BUY);
        o.setQuantity(10);
        o.setRemainingPortions(10);
        o.setContractSize(1);
        o.setStatus(OrderStatus.APPROVED);
        o.setAccountId(1L);
        o.setReservedAccountId(1L);
        o.setReservedAmount(new BigDecimal("1200.00"));
        o.setApprovedAt(approvedAt);
        o.setCreatedAt(approvedAt);
        return o;
    }

    private Order sellOrder(LocalDateTime approvedAt) {
        Order o = buyOrder(approvedAt);
        o.setId(101L);
        o.setDirection(OrderDirection.SELL);
        return o;
    }

    // ── 1. Initial delay guard ────────────────────────────────────────────────

    @Test
    @DisplayName("executeApprovedOrders: order mladji od initialDelay se preskace")
    void executeApprovedOrders_skipsOrdersWithinInitialDelay() {
        Order fresh = buyOrder(LocalDateTime.now()); // approvedAt = sada
        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(fresh));

        service.executeOrders();

        // Ne sme da pozove ni listing lookup ni fundReservation
        verify(listingRepository, never()).findById(any());
        verify(fundReservationService, never()).consumeForBuyFill(any(), anyInt(), any());
    }

    @Test
    @DisplayName("executeApprovedOrders: order stariji od initialDelay se izvrsava")
    void executeApprovedOrders_executesAfterInitialDelay() {
        Order stale = buyOrder(LocalDateTime.now().minusSeconds(65));
        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(stale));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
        when(accountRepository.findBankAccountByCurrencyId(eq("22200022"), eq(1L)))
                .thenReturn(Optional.of(bankAccount));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(userAccount));

        service.executeOrders();

        verify(listingRepository, times(1)).findById(10L);
        verify(fundReservationService, times(1))
                .consumeForBuyFill(eq(stale), anyInt(), any(BigDecimal.class));
    }

    // ── 2. BUY rewire ────────────────────────────────────────────────────────

    @Test
    @DisplayName("executeSingleOrder BUY: poziva consumeForBuyFill sa fill quantity")
    void executeSingleOrder_clientBuy_callsConsumeForBuyFill_withFillQuantity() {
        Order order = buyOrder(LocalDateTime.now().minusSeconds(120));
        // Forsiraj AON da fill = celokupna kolicina → deterministican test
        order.setAllOrNone(true);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(order));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(userAccount));
        when(accountRepository.findBankAccountByCurrencyId(eq("22200022"), eq(1L)))
                .thenReturn(Optional.of(bankAccount));

        service.executeOrders();

        ArgumentCaptor<Integer> qtyCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<BigDecimal> priceCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(fundReservationService).consumeForBuyFill(eq(order), qtyCap.capture(), priceCap.capture());

        assertThat(qtyCap.getValue()).isEqualTo(10);
        // total = 100 * 10 * 1 = 1000; commission = min(140, 7) = 7 → debit 1007
        assertThat(priceCap.getValue()).isEqualByComparingTo(new BigDecimal("1007.00"));
        // Order je u potpunosti popunjen → rezervacija treba da bude oslobodjena
        verify(fundReservationService, times(1)).releaseForBuy(order);
    }

    // ── 3. SELL rewire ───────────────────────────────────────────────────────

    @Test
    @DisplayName("executeSingleOrder SELL: azurira primajuci racun i poziva consumeForSellFill")
    void executeSingleOrder_clientSell_updatesReceivingAccountBalance_andCallsConsumeForSellFill() {
        Order order = sellOrder(LocalDateTime.now().minusSeconds(120));
        order.setAllOrNone(true);

        Portfolio portfolio = new Portfolio();
        portfolio.setId(5L);
        portfolio.setUserId(42L);
        portfolio.setListingId(10L);
        portfolio.setListingTicker("AAPL");
        portfolio.setListingName("Apple Inc");
        portfolio.setListingType("STOCK");
        portfolio.setQuantity(50);
        portfolio.setReservedQuantity(10);
        portfolio.setAverageBuyPrice(new BigDecimal("80.00"));

        BigDecimal initialBalance = userAccount.getBalance();
        BigDecimal initialAvailable = userAccount.getAvailableBalance();

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(order));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
        when(portfolioRepository.findByUserId(42L)).thenReturn(new ArrayList<>(List.of(portfolio)));
        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(userAccount));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(userAccount));
        when(accountRepository.findBankAccountByCurrencyId(eq("22200022"), eq(1L)))
                .thenReturn(Optional.of(bankAccount));

        service.executeOrders();

        // consumeForSellFill pozvan sa pravilnim qty
        verify(fundReservationService, times(1))
                .consumeForSellFill(eq(order), eq(portfolio), eq(10));

        // Bid = 95, qty = 10, contractSize = 1 → totalPrice = 950
        // Commission (MARKET client) = min(14% * 950 = 133, 7) = 7 → netRevenue 943
        BigDecimal expectedRevenue = new BigDecimal("943.00");
        assertThat(userAccount.getBalance()).isEqualByComparingTo(initialBalance.add(expectedRevenue));
        assertThat(userAccount.getAvailableBalance()).isEqualByComparingTo(initialAvailable.add(expectedRevenue));
    }

    // ── 4. Scheduler isolation ───────────────────────────────────────────────

    @Test
    @DisplayName("executeApprovedOrders: jedan failing order ne sprecava ostale")
    void executeApprovedOrders_continuesOtherOrders_whenOneFails() {
        Order bad = buyOrder(LocalDateTime.now().minusSeconds(120));
        bad.setId(200L);
        Order good = buyOrder(LocalDateTime.now().minusSeconds(120));
        good.setId(201L);
        good.setAllOrNone(true);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(bad, good));
        // Bad baci exception odmah na listing fetch
        when(listingRepository.findById(10L))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(Optional.of(listing));
        when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(userAccount));
        when(accountRepository.findBankAccountByCurrencyId(eq("22200022"), eq(1L)))
                .thenReturn(Optional.of(bankAccount));

        service.executeOrders();

        // Scheduler je nastavio i pokusao drugi order (consumeForBuyFill za good)
        verify(fundReservationService, times(1))
                .consumeForBuyFill(eq(good), anyInt(), any(BigDecimal.class));
    }

    // ── 5. Zaposleni ne placa proviziju ──────────────────────────────────────

    @Test
    @DisplayName("executeSingleOrder BUY (EMPLOYEE): commission = 0, nema bank transfer")
    void executeSingleOrder_employeeBuy_zeroCommission() {
        Order order = buyOrder(LocalDateTime.now().minusSeconds(120));
        order.setUserRole("EMPLOYEE");
        order.setAllOrNone(true);

        when(orderRepository.findByStatusAndIsDoneFalse(OrderStatus.APPROVED))
                .thenReturn(List.of(order));
        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(aonValidationService.checkCanExecuteAon(any(), anyInt())).thenReturn(true);

        service.executeOrders();

        ArgumentCaptor<BigDecimal> priceCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(fundReservationService).consumeForBuyFill(eq(order), eq(10), priceCap.capture());
        // Nema provizije → debit = 100 * 10 * 1 = 1000
        assertThat(priceCap.getValue()).isEqualByComparingTo(new BigDecimal("1000.00"));
        // Ne trazi bankin racun jer je commission 0
        verify(accountRepository, never()).findBankAccountByCurrencyId(any(), any());
    }
}

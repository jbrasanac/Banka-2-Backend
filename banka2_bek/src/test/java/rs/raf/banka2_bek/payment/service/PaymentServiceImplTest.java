package rs.raf.banka2_bek.payment.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.repository.AccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.implementation.PaymentServiceImpl;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private CreatePaymentRequestDto request;
    private User client;
    private Currency eur;
    private Account fromAccount;
    private Account toAccount;

    @BeforeEach
    void setUp() {
        request = new CreatePaymentRequestDto();
        request.setFromAccount("111111111111111111");
        request.setToAccount("222222222222222222");
        request.setAmount(new BigDecimal("100.00"));
        request.setPaymentCode("289");
        request.setReferenceNumber("REF-1");
        request.setDescription("Test payment");

        client = new User();
        client.setId(10L);
        client.setEmail("client@test.com");
        client.setActive(true);
        client.setRole("CLIENT");

        eur = Currency.builder().id(1L).code("EUR").name("Euro").symbol("E").country("EU").active(true).build();

        fromAccount = baseAccount(1L, request.getFromAccount(), client, eur, new BigDecimal("1000.00"));
        toAccount = baseAccount(2L, request.getToAccount(), null, eur, new BigDecimal("500.00"));

        authenticateAs(client.getEmail());
        lenient().when(userRepository.findByEmail(client.getEmail())).thenReturn(Optional.of(client));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPayment_success_updatesBalances_savesPayment_andRecordsTransactions() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(response.getAmount()).isEqualByComparingTo("100.00");

        assertThat(fromAccount.getBalance()).isEqualByComparingTo("900.00");
        assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("900.00");
        assertThat(fromAccount.getDailySpending()).isEqualByComparingTo("100.00");
        assertThat(fromAccount.getMonthlySpending()).isEqualByComparingTo("100.00");

        assertThat(toAccount.getBalance()).isEqualByComparingTo("600.00");
        assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo("600.00");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getOrderNumber()).startsWith("PAY-");
        assertThat(paymentCaptor.getValue().getFee()).isEqualByComparingTo("0");

        verify(transactionService).recordPaymentSettlement(any(Payment.class), eq(toAccount), eq(client));
    }

    @Test
    void createPayment_success_crossCurrency_appliesFeeAndFxRate() {
        Currency usd = Currency.builder()
                .id(2L)
                .code("USD")
                .name("US Dollar")
                .symbol("$")
                .country("US")
                .active(true)
                .build();
        toAccount.setCurrency(usd);

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount()))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount()))
                .thenReturn(Optional.of(toAccount));

        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(200L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);

        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getStatus().name()).isEqualTo("COMPLETED");

        // Fee = 0.5% of 100.00 = 0.50000
        assertThat(fromAccount.getBalance()).isEqualByComparingTo("899.50000");
        assertThat(fromAccount.getAvailableBalance()).isEqualByComparingTo("899.50000");

        // EUR->USD = 117.2 / 108.5 = 1.0801843318; credited amount = 108.01843318
        assertThat(toAccount.getBalance()).isEqualByComparingTo("608.01843318");
        assertThat(toAccount.getAvailableBalance()).isEqualByComparingTo("608.01843318");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getFee()).isEqualByComparingTo("0.50000");

        verify(transactionService).recordPaymentSettlement(any(Payment.class), eq(toAccount), eq(client));
    }


    @Test
    void createPayment_retriesOnUniqueViolation_thenSucceeds() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        AtomicInteger attempts = new AtomicInteger(0);
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            if (attempts.getAndIncrement() == 0) {
                throw uniqueViolation();
            }
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponseDto response = paymentService.createPayment(request);

        assertThat(response.getId()).isEqualTo(1L);
        verify(paymentRepository, times(2)).saveAndFlush(any(Payment.class));
    }

    @Test
    void createPayment_throwsWhenAllUniqueRetriesFail() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(uniqueViolation());

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate unique order number");
    }

    @Test
    void createPayment_propagatesDataIntegrityViolationWhenCauseMessageIsNull() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException()
        );
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isSameAs(ex);
    }

    @Test
    void createPayment_propagatesNonUniqueDataIntegrityViolation() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "constraint",
                new RuntimeException("Cannot add or update child row")
        );
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenThrow(ex);

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isSameAs(ex);
    }

    @Test
    void createPayment_throwsWhenFromAccountMissing() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source account does not exist");
    }

    @Test
    void createPayment_throwsWhenToAccountMissing() {
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination account does not exist");
    }

    @Test
    void createPayment_throwsWhenFromAccountInactive() {
        fromAccount.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source account is not active");
    }

    @Test
    void createPayment_throwsWhenToAccountInactive() {
        toAccount.setStatus(AccountStatus.INACTIVE);
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination account is not active");
    }

    @Test
    void createPayment_throwsWhenSameAccount() {
        toAccount.setId(fromAccount.getId());
        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void createPayment_throwsWhenFromAccountNotOwnedByAuthenticatedUser() {
        User other = new User();
        other.setId(777L);
        fromAccount.setClient(other);

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void createPayment_throwsWhenDailyLimitExceeded() {
        fromAccount.setDailyLimit(new BigDecimal("50.00"));

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Daily transfer limit exceeded");
    }

    @Test
    void createPayment_throwsWhenDailyLimitMissing() {
        fromAccount.setDailyLimit(null);

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Daily transfer limit exceeded");
    }

    @Test
    void createPayment_throwsWhenMonthlyLimitExceeded() {
        fromAccount.setMonthlyLimit(new BigDecimal("70.00"));

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monthly transfer limit exceeded");
    }

    @Test
    void createPayment_throwsWhenMonthlyLimitMissing() {
        fromAccount.setMonthlyLimit(null);

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monthly transfer limit exceeded");
    }

    @Test
    void createPayment_throwsWhenInsufficientFunds() {
        fromAccount.setAvailableBalance(new BigDecimal("20.00"));

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void createPayment_throwsWhenAvailableBalanceMissing() {
        fromAccount.setAvailableBalance(null);

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    void createPayment_throwsWhenAuthenticationMissing() {
        SecurityContextHolder.clearContext();

        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));

        assertThatThrownBy(() -> paymentService.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authenticated user is required");
    }

//    @Test
//    void createPayment_throwsWhenPrincipalIsNotUserDetails() {
//        SecurityContextHolder.getContext().setAuthentication(
//                new UsernamePasswordAuthenticationToken(
//                        "rawPrincipal",
//                        null,
//                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
//                )
//        );
//
//        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
//        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
//
//        assertThatThrownBy(() -> paymentService.createPayment(request))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("Authenticated user is required");
//    }
//
//    @Test
//    void createPayment_throwsWhenAuthenticatedUserNotFoundInDb() {
//        authenticateAs("missing@test.com");
//        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
//
//        when(accountRepository.findForUpdateByAccountNumber(request.getFromAccount())).thenReturn(Optional.of(fromAccount));
//        when(accountRepository.findForUpdateByAccountNumber(request.getToAccount())).thenReturn(Optional.of(toAccount));
//
//        assertThatThrownBy(() -> paymentService.createPayment(request))
//                .isInstanceOf(IllegalArgumentException.class)
//                .hasMessageContaining("Authenticated client does not exist");
//    }

    @Test
    void getFxRate_returnsOneForSameCurrency() throws Exception {
        BigDecimal rate = invokeGetFxRate("EUR", "EUR");
        assertThat(rate).isEqualByComparingTo("1");
    }

    @Test
    void getFxRate_supportsLowerCaseInputs() throws Exception {
        BigDecimal rate = invokeGetFxRate("usd", "rsd");
        assertThat(rate).isEqualByComparingTo("108.5000000000");
    }

    @Test
    void getFxRate_throwsForUnsupportedPair() {
        assertThatThrownBy(() -> invokeGetFxRate("EUR", "BTC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency pair");
    }

    private void authenticateAs(String email) {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        email,
                        "x",
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
                );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private Account baseAccount(Long id, String accountNumber, User owner, Currency currency, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .accountNumber(accountNumber)
                .currency(currency)
                .status(AccountStatus.ACTIVE)
                .client(owner)
                .balance(balance)
                .availableBalance(balance)
                .dailyLimit(new BigDecimal("5000.00"))
                .monthlyLimit(new BigDecimal("20000.00"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build();
    }

    private DataIntegrityViolationException uniqueViolation() {
        return new DataIntegrityViolationException(
                "duplicate key",
                new RuntimeException("Duplicate entry for key 'uk_payments_order_number'")
        );
    }

    private BigDecimal invokeGetFxRate(String from, String to) throws Exception {
        Method method = PaymentServiceImpl.class.getDeclaredMethod("getFxRate", String.class, String.class);
        method.setAccessible(true);
        try {
            return (BigDecimal) method.invoke(paymentService, from, to);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw ex;
        }
    }
}

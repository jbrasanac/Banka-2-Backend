package rs.raf.banka2_bek.loan.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.loan.dto.*;
import rs.raf.banka2_bek.loan.model.*;
import rs.raf.banka2_bek.loan.service.LoanService;
import rs.raf.banka2_bek.loan.repository.LoanInstallmentRepository;
import rs.raf.banka2_bek.loan.repository.LoanRepository;
import rs.raf.banka2_bek.loan.repository.LoanRequestRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

    private final LoanRequestRepository loanRequestRepository;
    private final LoanRepository loanRepository;
    private final LoanInstallmentRepository installmentRepository;
    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final CurrencyRepository currencyRepository;

    @Override
    @Transactional
    public LoanRequestResponseDto createLoanRequest(LoanRequestDto request, String clientEmail) {
        Client client = clientRepository.findByEmail(clientEmail)
                .orElseThrow(() -> new RuntimeException("Klijent nije pronadjen"));

        Account account = accountRepository.findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Racun nije pronadjen"));

        if (account.getClient() == null || !account.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Racun ne pripada klijentu");
        }

        Currency currency = currencyRepository.findByCode(request.getCurrency())
                .orElseThrow(() -> new RuntimeException("Valuta nije podrzana: " + request.getCurrency()));

        if (!account.getCurrency().getCode().equals(currency.getCode())) {
            throw new RuntimeException("Valuta kredita mora da se poklapa sa valutom racuna");
        }

        LoanRequest loanRequest = LoanRequest.builder()
                .loanType(LoanType.valueOf(request.getLoanType()))
                .interestType(InterestType.valueOf(request.getInterestType()))
                .amount(request.getAmount())
                .currency(currency)
                .loanPurpose(request.getLoanPurpose())
                .repaymentPeriod(request.getRepaymentPeriod())
                .account(account)
                .client(client)
                .phoneNumber(request.getPhoneNumber())
                .employmentStatus(request.getEmploymentStatus())
                .monthlyIncome(request.getMonthlyIncome())
                .permanentEmployment(request.getPermanentEmployment())
                .employmentPeriod(request.getEmploymentPeriod())
                .build();

        return toRequestResponse(loanRequestRepository.save(loanRequest));
    }

    @Override
    public Page<LoanRequestResponseDto> getLoanRequests(LoanStatus status, Pageable pageable) {
        if (status != null) {
            return loanRequestRepository.findByStatus(status, pageable).map(this::toRequestResponse);
        }
        return loanRequestRepository.findAll(pageable).map(this::toRequestResponse);
    }

    @Override
    @Transactional
    public LoanResponseDto approveLoanRequest(Long requestId) {
        LoanRequest request = loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Zahtev za kredit nije pronadjen"));

        if (request.getStatus() != LoanStatus.PENDING) {
            throw new RuntimeException("Zahtev je vec obradjen");
        }

        request.setStatus(LoanStatus.APPROVED);
        loanRequestRepository.save(request);

        BigDecimal nominalRate = getBaseRate(request.getAmount());
        BigDecimal margin = getMargin(request.getLoanType());
        BigDecimal effectiveRate = nominalRate.add(margin);
        BigDecimal monthlyRate = effectiveRate.divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        int n = request.getRepaymentPeriod();

        // A = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRn = onePlusR.pow(n, MathContext.DECIMAL128);
        BigDecimal monthlyPayment = request.getAmount()
                .multiply(monthlyRate)
                .multiply(onePlusRn)
                .divide(onePlusRn.subtract(BigDecimal.ONE), 4, RoundingMode.HALF_UP);

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusMonths(n);

        String loanNumber = "LN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Loan loan = Loan.builder()
                .loanNumber(loanNumber)
                .loanType(request.getLoanType())
                .interestType(request.getInterestType())
                .amount(request.getAmount())
                .repaymentPeriod(n)
                .nominalRate(nominalRate)
                .effectiveRate(effectiveRate)
                .monthlyPayment(monthlyPayment)
                .startDate(startDate)
                .endDate(endDate)
                .remainingDebt(request.getAmount())
                .currency(request.getCurrency())
                .status(LoanStatus.ACTIVE)
                .account(request.getAccount())
                .client(request.getClient())
                .loanPurpose(request.getLoanPurpose())
                .build();

        loan = loanRepository.save(loan);

        // Deposit loan amount to account
        Account account = request.getAccount();
        account.setBalance(account.getBalance().add(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(request.getAmount()));
        accountRepository.save(account);

        // Generate installments
        for (int i = 1; i <= n; i++) {
            LoanInstallment installment = LoanInstallment.builder()
                    .loan(loan)
                    .amount(monthlyPayment)
                    .interestRate(effectiveRate)
                    .currency(request.getCurrency())
                    .expectedDueDate(startDate.plusMonths(i))
                    .paid(false)
                    .build();
            installmentRepository.save(installment);
        }

        return toLoanResponse(loan);
    }

    @Override
    @Transactional
    public LoanRequestResponseDto rejectLoanRequest(Long requestId) {
        LoanRequest request = loanRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Zahtev za kredit nije pronadjen"));

        if (request.getStatus() != LoanStatus.PENDING) {
            throw new RuntimeException("Zahtev je vec obradjen");
        }

        request.setStatus(LoanStatus.REJECTED);
        return toRequestResponse(loanRequestRepository.save(request));
    }

    @Override
    public Page<LoanResponseDto> getMyLoans(String clientEmail, Pageable pageable) {
        Client client = clientRepository.findByEmail(clientEmail).orElse(null);
        if (client == null) return Page.empty(pageable);
        return loanRepository.findByClientId(client.getId(), pageable).map(this::toLoanResponse);
    }

    @Override
    public Page<LoanResponseDto> getAllLoans(LoanType loanType, LoanStatus status, String accountNumber, Pageable pageable) {
        return loanRepository.findWithFilters(loanType, status, accountNumber, pageable).map(this::toLoanResponse);
    }

    @Override
    public LoanResponseDto getLoanById(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Kredit nije pronadjen"));
        return toLoanResponse(loan);
    }

    @Override
    public List<InstallmentResponseDto> getInstallments(Long loanId) {
        return installmentRepository.findByLoanIdOrderByExpectedDueDateAsc(loanId).stream()
                .map(this::toInstallmentResponse)
                .collect(Collectors.toList());
    }

    // --- Interest rate tables from spec ---

    private BigDecimal getBaseRate(BigDecimal amount) {
        // Convert to RSD equivalent for rate lookup (simplified: assume RSD or use amount directly)
        double amt = amount.doubleValue();
        if (amt <= 500_000) return new BigDecimal("6.25");
        if (amt <= 1_000_000) return new BigDecimal("6.00");
        if (amt <= 2_000_000) return new BigDecimal("5.75");
        if (amt <= 5_000_000) return new BigDecimal("5.50");
        if (amt <= 10_000_000) return new BigDecimal("5.25");
        if (amt <= 20_000_000) return new BigDecimal("5.00");
        return new BigDecimal("4.75");
    }

    private BigDecimal getMargin(LoanType type) {
        return switch (type) {
            case CASH -> new BigDecimal("1.75");
            case MORTGAGE -> new BigDecimal("1.50");
            case AUTO -> new BigDecimal("1.25");
            case REFINANCING -> new BigDecimal("1.00");
            case STUDENT -> new BigDecimal("0.75");
        };
    }

    // --- Mappers ---

    private LoanRequestResponseDto toRequestResponse(LoanRequest r) {
        return LoanRequestResponseDto.builder()
                .id(r.getId())
                .loanType(r.getLoanType().name())
                .interestType(r.getInterestType().name())
                .amount(r.getAmount())
                .currency(r.getCurrency().getCode())
                .loanPurpose(r.getLoanPurpose())
                .repaymentPeriod(r.getRepaymentPeriod())
                .accountNumber(r.getAccount().getAccountNumber())
                .phoneNumber(r.getPhoneNumber())
                .employmentStatus(r.getEmploymentStatus())
                .monthlyIncome(r.getMonthlyIncome())
                .permanentEmployment(r.getPermanentEmployment())
                .employmentPeriod(r.getEmploymentPeriod())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .clientEmail(r.getClient().getEmail())
                .clientName(r.getClient().getFirstName() + " " + r.getClient().getLastName())
                .build();
    }

    private LoanResponseDto toLoanResponse(Loan l) {
        return LoanResponseDto.builder()
                .id(l.getId())
                .loanNumber(l.getLoanNumber())
                .loanType(l.getLoanType().name())
                .interestType(l.getInterestType().name())
                .amount(l.getAmount())
                .repaymentPeriod(l.getRepaymentPeriod())
                .nominalRate(l.getNominalRate())
                .effectiveRate(l.getEffectiveRate())
                .monthlyPayment(l.getMonthlyPayment())
                .startDate(l.getStartDate())
                .endDate(l.getEndDate())
                .remainingDebt(l.getRemainingDebt())
                .currency(l.getCurrency().getCode())
                .status(l.getStatus().name())
                .accountNumber(l.getAccount().getAccountNumber())
                .loanPurpose(l.getLoanPurpose())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private InstallmentResponseDto toInstallmentResponse(LoanInstallment i) {
        return InstallmentResponseDto.builder()
                .id(i.getId())
                .amount(i.getAmount())
                .interestRate(i.getInterestRate())
                .currency(i.getCurrency().getCode())
                .expectedDueDate(i.getExpectedDueDate())
                .actualDueDate(i.getActualDueDate())
                .paid(i.getPaid())
                .build();
    }
}

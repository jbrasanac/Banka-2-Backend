package rs.raf.banka2_bek.option.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.option.dto.OptionChainDto;
import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.mapper.OptionMapper;
import rs.raf.banka2_bek.option.model.Option;
import rs.raf.banka2_bek.option.model.OptionType;
import rs.raf.banka2_bek.option.repository.OptionRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OptionService {

    private static final Logger log = LoggerFactory.getLogger(OptionService.class);

    private final OptionRepository optionRepository;
    private final ListingRepository listingRepository;
    private final EmployeeRepository employeeRepository;
    private final ActuaryInfoRepository actuaryInfoRepository;

    public List<OptionChainDto> getOptionsForStock(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new EntityNotFoundException("Listing id: " + listingId + " not found."));

        List<Option> options = optionRepository.findByStockListingId(listingId);
        BigDecimal currentPrice = listing.getPrice();

        Map<LocalDate, List<Option>> grouped = options.stream()
                .collect(Collectors.groupingBy(Option::getSettlementDate));

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    OptionChainDto chain = new OptionChainDto();
                    chain.setSettlementDate(entry.getKey());
                    chain.setCurrentStockPrice(currentPrice);

                    List<OptionDto> calls = entry.getValue().stream()
                            .filter(o -> o.getOptionType() == OptionType.CALL)
                            .sorted(Comparator.comparing(Option::getStrikePrice))
                            .map(o -> OptionMapper.toDto(o, currentPrice))
                            .toList();
                    chain.setCalls(calls);

                    List<OptionDto> puts = entry.getValue().stream()
                            .filter(o -> o.getOptionType() == OptionType.PUT)
                            .sorted(Comparator.comparing(Option::getStrikePrice))
                            .map(o -> OptionMapper.toDto(o, currentPrice))
                            .toList();
                    chain.setPuts(puts);

                    return chain;
                })
                .toList();
    }

    public OptionDto getOptionById(Long optionId) {
        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new EntityNotFoundException("Option id: " + optionId + " not found."));

        BigDecimal currentPrice = option.getStockListing().getPrice();
        return OptionMapper.toDto(option, currentPrice);
    }

    @Transactional
    public void exerciseOption(Long optionId, String userEmail) {
        ensureUserCanExerciseOptions(userEmail);

        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new EntityNotFoundException("Option id: " + optionId + " not found."));

        if (option.getSettlementDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Opcija je istekla (settlement: " + option.getSettlementDate() + ")"
            );
        }

        BigDecimal currentPrice = option.getStockListing().getPrice();
        BigDecimal strikePrice = option.getStrikePrice();

        if (option.getOptionType() == OptionType.CALL && currentPrice.compareTo(strikePrice) <= 0) {
            throw new IllegalArgumentException(
                    "CALL opcija nije in-the-money (stock: " + currentPrice + ", strike: " + strikePrice + ")"
            );
        }

        if (option.getOptionType() == OptionType.PUT && currentPrice.compareTo(strikePrice) >= 0) {
            throw new IllegalArgumentException(
                    "PUT opcija nije in-the-money (stock: " + currentPrice + ", strike: " + strikePrice + ")"
            );
        }

        if (option.getOpenInterest() <= 0) {
            throw new IllegalArgumentException("Opcija nema otvorenih ugovora za izvrsavanje.");
        }

        option.setOpenInterest(option.getOpenInterest() - 1);
        optionRepository.save(option);

        log.info(
                "Opcija {} (id={}) izvrsena od strane {}. Novi openInterest={}",
                option.getTicker(),
                option.getId(),
                userEmail,
                option.getOpenInterest()
        );
    }

    private void ensureUserCanExerciseOptions(String userEmail) {
        Employee employee = employeeRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("Samo aktuar moze da izvrsi opciju."));

        if (!Boolean.TRUE.equals(employee.getActive())) {
            throw new AccessDeniedException("Samo aktivan aktuar moze da izvrsi opciju.");
        }

        boolean adminEmployee = employee.getPermissions() != null && employee.getPermissions().contains("ADMIN");
        boolean actuaryExists = actuaryInfoRepository.findByEmployeeId(employee.getId()).isPresent();

        if (!adminEmployee && !actuaryExists) {
            throw new AccessDeniedException("Samo aktuar moze da izvrsi opciju.");
        }
    }
}
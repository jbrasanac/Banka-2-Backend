package rs.raf.banka2_bek.stock.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.stock.dto.ListingDailyPriceDto;
import rs.raf.banka2_bek.stock.dto.ListingDto;
import rs.raf.banka2_bek.stock.mapper.ListingMapper;
import rs.raf.banka2_bek.stock.model.ListingDailyPriceInfo;
import rs.raf.banka2_bek.stock.model.Listing;

import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import rs.raf.banka2_bek.stock.model.ListingType;
import rs.raf.banka2_bek.stock.repository.ListingDailyPriceInfoRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.stock.repository.ListingSpec;
import rs.raf.banka2_bek.stock.service.ListingService;

import java.time.LocalDate;
import java.util.Optional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;


@Service
@RequiredArgsConstructor
public class ListingServiceImpl implements ListingService {

    private final ListingRepository listingRepository;
    private final Random random = new Random();
    private final ListingDailyPriceInfoRepository dailyPriceRepository;

    @Override
    public Page<ListingDto> getListings(String type, String search, int page, int size) {
        ListingType listingType;
        try {
            listingType = ListingType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Nepoznat tip hartije: " + type);
        }

        if (listingType == ListingType.FOREX && isClient()) {
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");
        }

        var pageable = PageRequest.of(page, size, Sort.by("ticker").ascending());
        return listingRepository
                .findAll(ListingSpec.byTypeAndSearch(listingType, search), pageable)
                .map(ListingMapper::toDto);
    }

    private boolean isClient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLIENT".equals(a.getAuthority()));
    }

    @Override
    public ListingDto getListingById(Long id) {

        Optional<Listing> listingOptional = listingRepository.findById(id);

        if (listingOptional.isEmpty()) throw new EntityNotFoundException("Listing id: " + id + " not found.");

        Listing listing = listingOptional.get();

        if (isClient() && listing.getListingType() == ListingType.FOREX)
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");

        return ListingMapper.toDto(listing);
    }

    @Override
    public List<ListingDailyPriceDto> getListingHistory(Long listingId, String period) {

        Listing listing = listingRepository.findById(listingId).orElse(null);

        if (listing == null)
            throw new EntityNotFoundException("Listing id: " + listingId + " not found.");

        if(listing.getListingType() == ListingType.FOREX && isClient())
            throw new IllegalStateException("Klijenti nemaju pristup FOREX hartijama.");

        LocalDate now = LocalDate.now();
        List<ListingDailyPriceInfo> dailyPrices;

        if ("DAY".equalsIgnoreCase(period)) dailyPrices = dailyPriceRepository.findByListingIdAndDate(listingId, now);

        else if ("WEEK".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusDays(7)
            );

        else if ("MONTH".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusDays(30)
            );

        else if ("YEAR".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusYears(1)
            );

        else if ("FIVE_YEARS".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdAndDateAfterOrderByDateDesc(
                    listingId, now.minusYears(5)
            );

        else if ("ALL".equalsIgnoreCase(period))
            dailyPrices = dailyPriceRepository.findByListingIdOrderByDateDesc(listingId);

        else throw new IllegalArgumentException("Period može biti: DAY, WEEK, MONTH, YEAR, FIVE_YEARS, ALL");

        return dailyPrices.stream().map(ListingMapper::toDailyPriceDto).toList();
    }

    @Override
    @Transactional
    public void refreshPrices() {
        // 1. Proći kroz listinge
        List<Listing> listings = listingRepository.findAll();

        for (Listing listing : listings) {
            BigDecimal currentPrice = listing.getPrice();
            if (currentPrice == null) continue;

            // 2. Ažurirati price-related podatke (Dummy logika +/- 2%)
            double changePercent = 0.98 + (0.04 * random.nextDouble());
            BigDecimal newPrice = currentPrice.multiply(BigDecimal.valueOf(changePercent))
                    .setScale(4, RoundingMode.HALF_UP);

            // Ask/Bid (Ask uvek malo veći, Bid malo manji)
            BigDecimal newAsk = newPrice.multiply(BigDecimal.valueOf(1.002)).setScale(4, RoundingMode.HALF_UP);
            BigDecimal newBid = newPrice.multiply(BigDecimal.valueOf(0.998)).setScale(4, RoundingMode.HALF_UP);

            // Promena u odnosu na staru cenu
            BigDecimal priceChange = newPrice.subtract(currentPrice);

            // 3. Postaviti lastRefresh = now()
            listing.setPrice(newPrice);
            listing.setAsk(newAsk);
            listing.setBid(newBid);
            listing.setPriceChange(priceChange);
            listing.setLastRefresh(LocalDateTime.now());

            // Ažuriramo i volume (nasumično)
            if (listing.getVolume() != null) {
                listing.setVolume((long) (listing.getVolume() * (0.9 + (0.2 * random.nextDouble()))));
            }
        }

        // 4. Poziv endpoint-a ažurira listinge u bazi
        listingRepository.saveAll(listings);
    }
    @Scheduled(fixedRate = 900000)
    public void scheduledRefresh() {
        // Scheduler samo okida business metodu, ne sadrži logiku direktno
        this.refreshPrices();
    }

    @Override
    public void loadInitialData() {
        // TODO: Implementirati ucitavanje pocetnih podataka
        // OPCIJA 1: Ucitati iz CSV fajla (dummy podaci od generacije 2023/24)
        //   - Fajl sa akcijama: AAPL, MSFT, GOOG, AMZN, TSLA, ...
        //   - Fajl sa futures: CLJ26, SIH26, GCM26, ...
        //   - Forex parovi: koristiti vec postojece iz exchange modula
        //
        // OPCIJA 2: AlphaVantage API
        //   - Company Overview za akcije
        //   - TIME_SERIES_DAILY za istorijske cene
        //
        // OPCIJA 3: Hardcoded dummy podaci u seed.sql
        //   - INSERT INTO listings (...) VALUES (...)
        //   - Najlaksi pristup, vec imamo seed mehanizam
    }
}

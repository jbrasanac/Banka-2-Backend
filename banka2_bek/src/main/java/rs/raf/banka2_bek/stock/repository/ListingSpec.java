package rs.raf.banka2_bek.stock.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.model.ListingType;

public final class ListingSpec {

    private ListingSpec() {}

    /**
     * Filtrira listinge po tipu i opcionalnoj pretrazi po ticker-u ili imenu.
     * Pretraga je case-insensitive i partial match.
     */
    public static Specification<Listing> byTypeAndSearch(ListingType type, String search) {
        return (root, query, cb) -> {
            Predicate typePredicate = cb.equal(root.get("listingType"), type);

            if (search == null || search.isBlank()) {
                return typePredicate;
            }

            String pattern = "%" + search.toLowerCase() + "%"; //contains SQL pattern
            Predicate tickerMatch = cb.like(cb.lower(root.get("ticker")), pattern);
            Predicate nameMatch   = cb.like(cb.lower(root.get("name")),   pattern);

            return cb.and(typePredicate, cb.or(tickerMatch, nameMatch));
        };
    }
}

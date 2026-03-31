package rs.raf.banka2_bek.margin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.margin.dto.MarginAccountCheckDto;
import rs.raf.banka2_bek.margin.model.MarginAccount;
import rs.raf.banka2_bek.margin.model.MarginAccountStatus;

import java.util.List;

@Repository
public interface MarginAccountRepository extends JpaRepository<MarginAccount, Long> {

    /** Pronalazi sve margin racune za datog korisnika */
    List<MarginAccount> findByUserId(Long userId);

    /** Pronalazi sve margin racune sa datim statusom */
    List<MarginAccount> findByStatus(MarginAccountStatus status);

    /** Pronalazi margin racun vezan za dati obicni racun */
    List<MarginAccount> findByAccountId(Long accountId);

    @Modifying
    @Query(value = "UPDATE margin_accounts SET status = :blocked WHERE maintenance_margin > initial_margin", nativeQuery = true)
    void blockAccountsWhereMaintenanceExceedsInitial(@Param("blocked") String blocked);

    @Query(value = """
                SELECT\s
                    m.id AS marginAccountId,
                    u.email AS ownerEmail,
                    m.maintenance_margin AS maintenanceMargin,
                    m.initial_margin AS initialMargin
                FROM margin_accounts m
                JOIN clients c ON m.user_id = c.id
                WHERE m.status = :active
                  AND m.maintenance_margin > m.initial_margin
           \s""", nativeQuery = true)
    List<MarginAccountCheckDto> findAccountsForMarginCheck(
            @Param("active") String active
    );
}

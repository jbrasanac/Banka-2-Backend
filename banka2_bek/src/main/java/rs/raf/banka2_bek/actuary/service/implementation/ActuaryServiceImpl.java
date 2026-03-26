package rs.raf.banka2_bek.actuary.service.implementation;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.mapper.ActuaryMapper;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.actuary.service.ActuaryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActuaryServiceImpl implements ActuaryService {

    private final ActuaryInfoRepository actuaryInfoRepository;

    @Override
    public List<ActuaryInfoDto> getAgents(String email, String firstName, String lastName, String position) {
        List<ActuaryInfo> agents = actuaryInfoRepository.findByTypeAndFilters(
                ActuaryType.AGENT, email, firstName, lastName, position
        );
        return agents.stream()
                .map(ActuaryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public ActuaryInfoDto getActuaryInfo(Long employeeId) {
        ActuaryInfo info = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Actuary info for employee with ID " + employeeId + " not found."
                ));

        return ActuaryMapper.toDto(info);
    }

    @Override
    public ActuaryInfoDto updateAgentLimit(Long employeeId, UpdateActuaryLimitDto dto) {
        // TODO: Implementirati
        // 1. Proveriti da je ulogovani korisnik supervizor
        // 2. Naci ActuaryInfo za datog zaposlenog
        // 3. Proveriti da je zaposleni AGENT (supervizoru se ne menja limit)
        // 4. Azurirati dailyLimit i/ili needApproval iz DTO-a
        // 5. Sacuvati i vratiti azurirane podatke
        throw new UnsupportedOperationException("TODO: Implementirati updateAgentLimit");
    }

    @Override
    @Transactional
    public ActuaryInfoDto resetUsedLimit(Long employeeId) {
        ActuaryInfo actuary = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("Actuary record not found for employee ID: " + employeeId));


        if (actuary.getActuaryType() != ActuaryType.AGENT) {
            throw new IllegalStateException("Reset is only allowed for Agents. Supervisors do not have limits.");
        }

        actuary.setUsedLimit(BigDecimal.ZERO);
        ActuaryInfo updatedActuary = actuaryInfoRepository.save(actuary);
        return ActuaryMapper.toDto(updatedActuary);
    }

    @Override
    @Scheduled(cron = "0 59 23 * * *") // Svaki dan u 23:59
    public void resetAllUsedLimits() {
        // TODO: Implementirati automatski reset svih agenata
        // 1. Dohvatiti sve ActuaryInfo gde je actuaryType = AGENT
        // 2. Postaviti usedLimit na 0 za svakoga
        // 3. Sacuvati sve
        // NAPOMENA: Ovo se poziva automatski putem @Scheduled
    }
}

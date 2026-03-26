package rs.raf.banka2_bek.actuary.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.mapper.ActuaryMapper;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.actuary.service.ActuaryService;

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
        String currentUsername = getAuthenticatedUsername();
        ActuaryInfo currentUserInfo = actuaryInfoRepository.findByEmployee_Email(currentUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not an actuary."));

        if(currentUserInfo.getActuaryType() != ActuaryType.SUPERVISOR) {
           throw new IllegalStateException("Only supervisors can update agent limits.");
        }

        if(currentUserInfo.getEmployee().getId().equals(employeeId)) {
            throw new IllegalStateException("Cannot change own actuary info.");
        }

        ActuaryInfo targetUserInfo = actuaryInfoRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist or isn't an actuary."));

        if(targetUserInfo.getActuaryType() != ActuaryType.AGENT) {
            throw new RuntimeException("Limits can only be updated for agents.");
        }

        targetUserInfo.setDailyLimit(dto.getDailyLimit() != null ? dto.getDailyLimit() : targetUserInfo.getDailyLimit());
        targetUserInfo.setNeedApproval(dto.getNeedApproval() != null ? dto.getNeedApproval() : targetUserInfo.isNeedApproval());

        actuaryInfoRepository.save(targetUserInfo);
        ActuaryInfoDto response = ActuaryMapper.toDto(targetUserInfo);
        return response;
    }

    @Override
    public ActuaryInfoDto resetUsedLimit(Long employeeId) {
        // TODO: Implementirati
        // 1. Proveriti da je ulogovani korisnik supervizor
        // 2. Naci ActuaryInfo za datog agenta
        // 3. Postaviti usedLimit na 0
        // 4. Sacuvati i vratiti
        throw new UnsupportedOperationException("TODO: Implementirati resetUsedLimit");
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

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        throw new IllegalStateException("Authenticated user is required.");
    }

}

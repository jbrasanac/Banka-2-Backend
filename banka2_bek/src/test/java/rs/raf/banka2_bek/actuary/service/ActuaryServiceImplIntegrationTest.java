package rs.raf.banka2_bek.actuary.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ActuaryServiceImplIntegrationTest {

    @Autowired
    private ActuaryService actuaryService;

    @Autowired
    private ActuaryInfoRepository actuaryInfoRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    private Employee agentMarko;
    private Employee agentJelena;
    private Employee supervisorNina;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        actuaryInfoRepository.deleteAll();
        employeeRepository.deleteAll();
        userRepository.deleteAll();

        User admin = new User();
        admin.setEmail("admin@banka.rs");
        admin.setPassword("pass");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setActive(true);
        admin.setRole("ADMIN");
        userRepository.save(admin);

        agentMarko = employeeRepository.save(Employee.builder()
                .firstName("Marko").lastName("Markovic")
                .email("marko.actuary@banka.rs")
                .dateOfBirth(LocalDate.of(1990, 5, 15))
                .gender("M").phone("+38163100200").address("Beograd")
                .username("marko.actuary")
                .password("pass")
                .saltPassword("salt")
                .position("Menadzer").department("IT").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        agentJelena = employeeRepository.save(Employee.builder()
                .firstName("Jelena").lastName("Jovanovic")
                .email("jelena.actuary@banka.rs")
                .dateOfBirth(LocalDate.of(1992, 8, 22))
                .gender("F").phone("+38164200300").address("Novi Sad")
                .username("jelena.actuary")
                .password("pass")
                .saltPassword("salt")
                .position("Analiticar").department("Finance").active(true)
                .permissions(Set.of("AGENT"))
                .build());

        supervisorNina = employeeRepository.save(Employee.builder()
                .firstName("Nina").lastName("Nikolic")
                .email("nina.supervisor@banka.rs")
                .dateOfBirth(LocalDate.of(1985, 11, 3))
                .gender("F").phone("+38166400500").address("Beograd")
                .username("nina.supervisor")
                .password("pass")
                .saltPassword("salt")
                .position("Direktor").department("Management").active(true)
                .permissions(Set.of("SUPERVISOR"))
                .build());

        actuaryInfoRepository.save(createActuaryInfo(agentMarko, ActuaryType.AGENT,
                new BigDecimal("100000.00"), new BigDecimal("15000.00"), false));
        actuaryInfoRepository.save(createActuaryInfo(agentJelena, ActuaryType.AGENT,
                new BigDecimal("50000.00"), new BigDecimal("999.99"), true));
        actuaryInfoRepository.save(createActuaryInfo(supervisorNina, ActuaryType.SUPERVISOR,
                null, null, false));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@banka.rs", null, java.util.List.of())
        );
    }

    private ActuaryInfo createActuaryInfo(Employee employee,
                                          ActuaryType type,
                                          BigDecimal dailyLimit,
                                          BigDecimal usedLimit,
                                          boolean needApproval) {
        ActuaryInfo info = new ActuaryInfo();
        info.setEmployee(employee);
        info.setActuaryType(type);
        info.setDailyLimit(dailyLimit);
        info.setUsedLimit(usedLimit);
        info.setNeedApproval(needApproval);
        return info;
    }

    @Test
    @DisplayName("resetAllUsedLimits resetuje samo agente, supervizor ostaje neizmenjen")
    void resetAllUsedLimitsResetsOnlyAgents() {
        actuaryService.resetAllUsedLimits();

        ActuaryInfo refreshedMarko = actuaryInfoRepository.findByEmployeeId(agentMarko.getId()).orElseThrow();
        ActuaryInfo refreshedJelena = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        ActuaryInfo refreshedNina = actuaryInfoRepository.findByEmployeeId(supervisorNina.getId()).orElseThrow();

        assertEquals(0, refreshedMarko.getUsedLimit().compareTo(BigDecimal.ZERO));
        assertEquals(0, refreshedJelena.getUsedLimit().compareTo(BigDecimal.ZERO));
        assertNull(refreshedNina.getUsedLimit());
        assertEquals(ActuaryType.SUPERVISOR, refreshedNina.getActuaryType());
    }

    @Test
    @DisplayName("updateAgentLimit menja samo trazena polja i cuva ih u bazi")
    void updateAgentLimitPersistsChanges() {
        authenticateAsAdmin();

        UpdateActuaryLimitDto dto = new UpdateActuaryLimitDto();
        dto.setDailyLimit(new BigDecimal("250000.00"));
        dto.setNeedApproval(true);

        ActuaryInfoDto result = actuaryService.updateAgentLimit(agentMarko.getId(), dto);

        assertEquals(new BigDecimal("250000.00"), result.getDailyLimit());
        assertTrue(result.isNeedApproval());
        assertEquals(new BigDecimal("15000.00"), result.getUsedLimit());

        ActuaryInfo refreshed = actuaryInfoRepository.findByEmployeeId(agentMarko.getId()).orElseThrow();
        assertEquals(new BigDecimal("250000.00"), refreshed.getDailyLimit());
        assertTrue(refreshed.isNeedApproval());
        assertEquals(new BigDecimal("15000.00"), refreshed.getUsedLimit());
    }

    @Test
    @DisplayName("resetUsedLimit rucno resetuje samo target agenta")
    void resetUsedLimitPersistsZero() {
        authenticateAsAdmin();

        ActuaryInfoDto result = actuaryService.resetUsedLimit(agentMarko.getId());

        assertEquals(0, result.getUsedLimit().compareTo(BigDecimal.ZERO));

        ActuaryInfo refreshedMarko = actuaryInfoRepository.findByEmployeeId(agentMarko.getId()).orElseThrow();
        ActuaryInfo refreshedJelena = actuaryInfoRepository.findByEmployeeId(agentJelena.getId()).orElseThrow();
        assertEquals(0, refreshedMarko.getUsedLimit().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("999.99"), refreshedJelena.getUsedLimit());
    }
}
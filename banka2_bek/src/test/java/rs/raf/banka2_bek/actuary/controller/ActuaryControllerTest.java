package rs.raf.banka2_bek.actuary.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import rs.raf.banka2_bek.actuary.controller.exception_handler.ActuaryExceptionHandler;
import rs.raf.banka2_bek.actuary.dto.ActuaryInfoDto;
import rs.raf.banka2_bek.actuary.dto.UpdateActuaryLimitDto;
import rs.raf.banka2_bek.actuary.service.ActuaryService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ActuaryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ActuaryService actuaryService;

    @InjectMocks
    private ActuaryController actuaryController;

    private ActuaryInfoDto testAgentDto;
    private ActuaryInfoDto testAgentDto2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(actuaryController)
                .setControllerAdvice(new ActuaryExceptionHandler())
                .build();

        testAgentDto = new ActuaryInfoDto();
        testAgentDto.setId(1L);
        testAgentDto.setEmployeeId(10L);
        testAgentDto.setEmployeeName("Marko Markovic");
        testAgentDto.setEmployeeEmail("marko.markovic@banka.rs");
        testAgentDto.setEmployeePosition("Menadzer");
        testAgentDto.setActuaryType("AGENT");
        testAgentDto.setDailyLimit(new BigDecimal("100000.00"));
        testAgentDto.setUsedLimit(new BigDecimal("15000.00"));
        testAgentDto.setNeedApproval(false);

        testAgentDto2 = new ActuaryInfoDto();
        testAgentDto2.setId(2L);
        testAgentDto2.setEmployeeId(11L);
        testAgentDto2.setEmployeeName("Jelena Jovanovic");
        testAgentDto2.setEmployeeEmail("jelena.jovanovic@banka.rs");
        testAgentDto2.setEmployeePosition("Analiticar");
        testAgentDto2.setActuaryType("AGENT");
        testAgentDto2.setDailyLimit(new BigDecimal("50000.00"));
        testAgentDto2.setUsedLimit(BigDecimal.ZERO);
        testAgentDto2.setNeedApproval(true);
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/agents
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /actuaries/agents - 200 OK sa listom agenata")
    void getAgents_returnsList() throws Exception {
        when(actuaryService.getAgents(null, null, null, null))
                .thenReturn(List.of(testAgentDto, testAgentDto2));

        mockMvc.perform(get("/actuaries/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].employeeId").value(10))
                .andExpect(jsonPath("$[0].employeeName").value("Marko Markovic"))
                .andExpect(jsonPath("$[0].employeeEmail").value("marko.markovic@banka.rs"))
                .andExpect(jsonPath("$[0].employeePosition").value("Menadzer"))
                .andExpect(jsonPath("$[0].actuaryType").value("AGENT"))
                .andExpect(jsonPath("$[0].dailyLimit").value(100000.00))
                .andExpect(jsonPath("$[0].usedLimit").value(15000.00))
                .andExpect(jsonPath("$[0].needApproval").value(false))
                .andExpect(jsonPath("$[1].employeeName").value("Jelena Jovanovic"))
                .andExpect(jsonPath("$[1].needApproval").value(true));

        verify(actuaryService).getAgents(null, null, null, null);
    }

    @Test
    @DisplayName("GET /actuaries/agents - 200 OK sa praznom listom")
    void getAgents_returnsEmptyList() throws Exception {
        when(actuaryService.getAgents(null, null, null, null))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/actuaries/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /actuaries/agents?email=marko - 200 OK filtrirano po email-u")
    void getAgents_filteredByEmail() throws Exception {
        when(actuaryService.getAgents(eq("marko"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(testAgentDto));

        mockMvc.perform(get("/actuaries/agents")
                        .param("email", "marko"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeEmail").value("marko.markovic@banka.rs"));

        verify(actuaryService).getAgents("marko", null, null, null);
    }

    @Test
    @DisplayName("GET /actuaries/agents?firstName=Jelena - 200 OK filtrirano po imenu")
    void getAgents_filteredByFirstName() throws Exception {
        when(actuaryService.getAgents(isNull(), eq("Jelena"), isNull(), isNull()))
                .thenReturn(List.of(testAgentDto2));

        mockMvc.perform(get("/actuaries/agents")
                        .param("firstName", "Jelena"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName").value("Jelena Jovanovic"));

        verify(actuaryService).getAgents(null, "Jelena", null, null);
    }

    @Test
    @DisplayName("GET /actuaries/agents?lastName=Jovanovic - 200 OK filtrirano po prezimenu")
    void getAgents_filteredByLastName() throws Exception {
        when(actuaryService.getAgents(isNull(), isNull(), eq("Jovanovic"), isNull()))
                .thenReturn(List.of(testAgentDto2));

        mockMvc.perform(get("/actuaries/agents")
                        .param("lastName", "Jovanovic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeName").value("Jelena Jovanovic"));

        verify(actuaryService).getAgents(null, null, "Jovanovic", null);
    }

    @Test
    @DisplayName("GET /actuaries/agents?position=Menadzer - 200 OK filtrirano po poziciji")
    void getAgents_filteredByPosition() throws Exception {
        when(actuaryService.getAgents(isNull(), isNull(), isNull(), eq("Menadzer")))
                .thenReturn(List.of(testAgentDto));

        mockMvc.perform(get("/actuaries/agents")
                        .param("position", "Menadzer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeePosition").value("Menadzer"));

        verify(actuaryService).getAgents(null, null, null, "Menadzer");
    }

    @Test
    @DisplayName("GET /actuaries/agents?email=marko&firstName=Marko&position=Menadzer - 200 OK sa vise filtera")
    void getAgents_multipleFilters() throws Exception {
        when(actuaryService.getAgents(eq("marko"), eq("Marko"), isNull(), eq("Menadzer")))
                .thenReturn(List.of(testAgentDto));

        mockMvc.perform(get("/actuaries/agents")
                        .param("email", "marko")
                        .param("firstName", "Marko")
                        .param("position", "Menadzer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(actuaryService).getAgents("marko", "Marko", null, "Menadzer");
    }

    // ══════════════════════════════════════════════════════════════════
    //  GET /actuaries/{employeeId}
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /actuaries/10 - 200 OK sa detaljima aktuara")
    void getActuaryInfo_returnsDto() throws Exception {
        when(actuaryService.getActuaryInfo(10L)).thenReturn(testAgentDto);

        mockMvc.perform(get("/actuaries/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.employeeId").value(10))
                .andExpect(jsonPath("$.employeeName").value("Marko Markovic"))
                .andExpect(jsonPath("$.employeeEmail").value("marko.markovic@banka.rs"))
                .andExpect(jsonPath("$.employeePosition").value("Menadzer"))
                .andExpect(jsonPath("$.actuaryType").value("AGENT"))
                .andExpect(jsonPath("$.dailyLimit").value(100000.00))
                .andExpect(jsonPath("$.usedLimit").value(15000.00))
                .andExpect(jsonPath("$.needApproval").value(false));

        verify(actuaryService).getActuaryInfo(10L);
    }

    @Test
    @DisplayName("GET /actuaries/999 - 404 kada zapis ne postoji")
    void getActuaryInfo_notFound_returns404() throws Exception {
        when(actuaryService.getActuaryInfo(999L))
                .thenThrow(new IllegalArgumentException(
                        "Actuarski zapis za zaposlenog sa ID 999 nije pronadjen."));

        mockMvc.perform(get("/actuaries/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(
                        "Actuarski zapis za zaposlenog sa ID 999 nije pronadjen."));
    }

    @Test
    @DisplayName("PATCH /actuaries/{employeeId}/limit - 200 OK i prosledjuje dto service-u")
    void updateAgentLimit_returnsUpdatedDto() throws Exception {
        ActuaryInfoDto updated = new ActuaryInfoDto();
        updated.setId(2L);
        updated.setEmployeeId(11L);
        updated.setEmployeeName("Jelena Jovanovic");
        updated.setActuaryType("AGENT");
        updated.setDailyLimit(new BigDecimal("65000.00"));
        updated.setUsedLimit(BigDecimal.ZERO);
        updated.setNeedApproval(false);

        when(actuaryService.updateAgentLimit(eq(11L), any(UpdateActuaryLimitDto.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/actuaries/11/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":65000.00,\"needApproval\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(11))
                .andExpect(jsonPath("$.dailyLimit").value(65000.00))
                .andExpect(jsonPath("$.needApproval").value(false));

        ArgumentCaptor<UpdateActuaryLimitDto> dtoCaptor = ArgumentCaptor.forClass(UpdateActuaryLimitDto.class);
        verify(actuaryService).updateAgentLimit(eq(11L), dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getDailyLimit()).isEqualByComparingTo("65000.00");
        assertThat(dtoCaptor.getValue().getNeedApproval()).isFalse();
    }

    @Test
    @DisplayName("PATCH /actuaries/{employeeId}/limit - 404 kada cilj ne postoji")
    void updateAgentLimit_notFound_returns404() throws Exception {
        when(actuaryService.updateAgentLimit(eq(999L), any(UpdateActuaryLimitDto.class)))
                .thenThrow(new IllegalArgumentException("User does not exist or isn't an actuary."));

        mockMvc.perform(patch("/actuaries/999/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyLimit\":12345}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User does not exist or isn't an actuary."));
    }

    @Test
    @DisplayName("PATCH /actuaries/{employeeId}/limit - 403 kada nije dozvoljeno")
    void updateAgentLimit_forbidden_returns403() throws Exception {
        when(actuaryService.updateAgentLimit(eq(11L), any(UpdateActuaryLimitDto.class)))
                .thenThrow(new IllegalStateException("Only supervisors can update agent limits."));

        mockMvc.perform(patch("/actuaries/11/limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"needApproval\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Only supervisors can update agent limits."));
    }
}
package rs.raf.banka2_bek.option.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.auth.dto.MessageResponseDto;
import rs.raf.banka2_bek.option.dto.OptionChainDto;
import rs.raf.banka2_bek.option.dto.OptionDto;
import rs.raf.banka2_bek.option.service.OptionGeneratorService;
import rs.raf.banka2_bek.option.service.OptionService;

import java.util.List;

@Tag(name = "Options", description = "Opcije — option chain, detalji, izvrsavanje i generisanje")
@RestController
@RequestMapping("/options")
@RequiredArgsConstructor
public class OptionController {

    private final OptionService optionService;
    private final OptionGeneratorService optionGeneratorService;

    @Operation(summary = "Vraca option chain za akciju",
            description = "Vraca sve opcije za datu akciju, grupisane po settlement datumu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Option chain uspesno vracen"),
            @ApiResponse(responseCode = "404", description = "Akcija sa datim ID-jem ne postoji",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<OptionChainDto>> getOptionsForStock(@RequestParam Long stockListingId) {
        return ResponseEntity.ok(optionService.getOptionsForStock(stockListingId));
    }

    @Operation(summary = "Vraca detalje jedne opcije")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opcija uspesno pronadjena"),
            @ApiResponse(responseCode = "404", description = "Opcija sa datim ID-jem ne postoji",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<OptionDto> getOptionById(@PathVariable Long id) {
        return ResponseEntity.ok(optionService.getOptionById(id));
    }

    @Operation(summary = "Izvrsi opciju",
            description = "Izvrsava opciju (exercise). Samo aktuari mogu izvrsavati opcije.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opcija uspesno izvrsena"),
            @ApiResponse(responseCode = "400", description = "Opcija nije validna za izvrsavanje",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Korisnik nema dozvolu",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Opcija ne postoji",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @PostMapping("/{id}/exercise")
    @PreAuthorize("hasAnyAuthority('AGENT', 'SUPERVISOR', 'ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<MessageResponseDto> exerciseOption(
            @PathVariable Long id,
            Authentication authentication
    ) {
        String userEmail = authentication.getName();
        optionService.exerciseOption(id, userEmail);
        return ResponseEntity.ok(new MessageResponseDto("Opcija uspesno izvrsena."));
    }

    @Operation(summary = "Generiši opcije za sve akcije")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Opcije uspesno generisane"),
            @ApiResponse(responseCode = "403", description = "Korisnik nema ulogu ADMIN",
                    content = @Content(schema = @Schema(implementation = MessageResponseDto.class)))
    })
    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponseDto> generateOptions() {
        optionGeneratorService.generateAllOptions();
        return ResponseEntity.ok(new MessageResponseDto("Opcije uspesno generisane za sve akcije."));
    }
}
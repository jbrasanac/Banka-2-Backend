package rs.raf.banka2_bek.card.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.card.dto.CardResponseDto;
import rs.raf.banka2_bek.card.dto.CreateCardRequestDto;
import rs.raf.banka2_bek.card.model.Card;
import rs.raf.banka2_bek.card.model.CardStatus;
import rs.raf.banka2_bek.card.repository.CardRepository;
import rs.raf.banka2_bek.card.service.implementation.CardServiceImpl;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock private CardRepository cardRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ClientRepository clientRepository;

    @InjectMocks
    private CardServiceImpl cardService;

    private Client client;
    private Account personalAccount;
    private Account businessAccount;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L).firstName("Stefan").lastName("Jovanovic")
                .email("stefan@test.com").build();

        personalAccount = Account.builder()
                .id(1L).accountNumber("222000112345678911")
                .accountType(AccountType.CHECKING)
                .client(client).build();

        businessAccount = Account.builder()
                .id(2L).accountNumber("222000112345678912")
                .accountType(AccountType.BUSINESS)
                .client(client).build();
    }

    private void mockAuth(String email) {
        UserDetails userDetails = User.builder()
                .username(email).password("pass").authorities("ROLE_CLIENT").build();
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Nested
    @DisplayName("createCard")
    class CreateCard {

        @Test
        @DisplayName("kreira karticu za licni racun")
        void successPersonal() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);
            dto.setCardLimit(BigDecimal.valueOf(50000));

            CardResponseDto result = cardService.createCard(dto);

            assertThat(result).isNotNull();
            assertThat(result.getCardNumber()).hasSize(16);
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
            assertThat(result.getCardLimit()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("baca gresku kad je dostignut max 2 kartice za licni racun")
        void maxCardsPersonal() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(2L);

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maksimalan broj kartica");
        }

        @Test
        @DisplayName("baca gresku kad je dostignut max 1 kartica za poslovni racun")
        void maxCardsBusiness() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(2L)).thenReturn(Optional.of(businessAccount));
            when(cardRepository.countByAccountIdAndStatusNot(2L, CardStatus.DEACTIVATED)).thenReturn(1L);

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(2L);

            assertThatThrownBy(() -> cardService.createCard(dto))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("maksimalan broj kartica");
        }

        @Test
        @DisplayName("default limit je 100000 kad nije zadat")
        void defaultLimit() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));
            when(accountRepository.findById(1L)).thenReturn(Optional.of(personalAccount));
            when(cardRepository.countByAccountIdAndStatusNot(1L, CardStatus.DEACTIVATED)).thenReturn(0L);
            when(cardRepository.findByCardNumber(anyString())).thenReturn(Optional.empty());
            when(cardRepository.save(any(Card.class))).thenAnswer(inv -> {
                Card c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });

            CreateCardRequestDto dto = new CreateCardRequestDto();
            dto.setAccountId(1L);

            CardResponseDto result = cardService.createCard(dto);
            assertThat(result.getCardLimit()).isEqualByComparingTo("100000");
        }
    }

    @Nested
    @DisplayName("getMyCards")
    class GetMyCards {

        @Test
        @DisplayName("vraca kartice za klijenta")
        void returnsCards() {
            mockAuth("stefan@test.com");
            when(clientRepository.findByEmail("stefan@test.com")).thenReturn(Optional.of(client));

            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .cardLimit(BigDecimal.valueOf(250000)).status(CardStatus.ACTIVE)
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findByClientId(1L)).thenReturn(List.of(card));

            List<CardResponseDto> result = cardService.getMyCards();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCardNumber()).isEqualTo("4222001234567890");
        }

        @Test
        @DisplayName("vraca praznu listu za non-client (admin)")
        void emptyForNonClient() {
            SecurityContext ctx = mock(SecurityContext.class);
            when(ctx.getAuthentication()).thenReturn(null);
            SecurityContextHolder.setContext(ctx);

            List<CardResponseDto> result = cardService.getMyCards();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("blockCard / unblockCard / deactivateCard")
    class CardStatusChanges {

        @Test
        @DisplayName("blokira aktivnu karticu")
        void blockActive() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.blockCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.BLOCKED);
        }

        @Test
        @DisplayName("ne moze da blokira deaktiviranu karticu")
        void cannotBlockDeactivated() {
            Card card = Card.builder().id(1L).status(CardStatus.DEACTIVATED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.blockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("ne moze blokirati");
        }

        @Test
        @DisplayName("odblokira blokiranu karticu")
        void unblockBlocked() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.BLOCKED).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.unblockCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.ACTIVE);
        }

        @Test
        @DisplayName("ne moze da odblokira aktivnu karticu")
        void cannotUnblockActive() {
            Card card = Card.builder().id(1L).status(CardStatus.ACTIVE).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.unblockCard(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("blokirana");
        }

        @Test
        @DisplayName("deaktivira karticu")
        void deactivateCard() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.deactivateCard(1L);
            assertThat(result.getStatus()).isEqualTo(CardStatus.DEACTIVATED);
        }
    }

    @Nested
    @DisplayName("updateCardLimit")
    class UpdateLimit {

        @Test
        @DisplayName("menja limit aktivne kartice")
        void success() {
            Card card = Card.builder()
                    .id(1L).cardNumber("4222001234567890").cardName("Visa Debit")
                    .cvv("123").account(personalAccount).client(client)
                    .status(CardStatus.ACTIVE).cardLimit(BigDecimal.valueOf(100000))
                    .createdAt(LocalDate.now()).expirationDate(LocalDate.now().plusYears(4)).build();

            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));
            when(cardRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CardResponseDto result = cardService.updateCardLimit(1L, BigDecimal.valueOf(200000));
            assertThat(result.getCardLimit()).isEqualByComparingTo("200000");
        }

        @Test
        @DisplayName("ne moze menjati limit deaktivirane kartice")
        void cannotChangeLimitOfDeactivated() {
            Card card = Card.builder().id(1L).status(CardStatus.DEACTIVATED).build();
            when(cardRepository.findById(1L)).thenReturn(Optional.of(card));

            assertThatThrownBy(() -> cardService.updateCardLimit(1L, BigDecimal.valueOf(50000)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("deaktivirane");
        }
    }
}

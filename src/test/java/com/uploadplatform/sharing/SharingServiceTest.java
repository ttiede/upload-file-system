package com.uploadplatform.sharing;

import com.uploadplatform.sharing.model.ShareLink;
import com.uploadplatform.sharing.model.ShareLink.Permissao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharingServiceTest {

    private SharingService sharingService;

    @BeforeEach
    void setUp() {
        sharingService = new SharingService();
    }

    @Test
    @DisplayName("criarLink retorna link com token UUID e ativo")
    void criarLink_retornaTokenValido() {
        ShareLink link = sharingService.criarLink(
                "arquivo-1", "dono-1", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);

        assertThat(link.getToken()).isNotBlank();
        assertThat(link.getFileId()).isEqualTo("arquivo-1");
        assertThat(link.getDonoId()).isEqualTo("dono-1");
        assertThat(link.isAtivo()).isTrue();
        assertThat(link.expirou()).isFalse();
    }

    @Test
    @DisplayName("criarLink armazena o link no mapa")
    void criarLink_armazenadoNoMapa() {
        assertThat(sharingService.getTotalLinks()).isZero();

        sharingService.criarLink("a1", "o1", Duration.ofMinutes(30), Set.of(), Permissao.BAIXAR);

        assertThat(sharingService.getTotalLinks()).isEqualTo(1);
    }

    @Test
    @DisplayName("validarLink retorna link para link público válido")
    void validarLink_linkPublicoValido() {
        ShareLink link = sharingService.criarLink(
                "arquivo-2", "dono-2", Duration.ofHours(24), Set.of(), Permissao.VISUALIZAR);

        Optional<ShareLink> resultado = sharingService.validarLink(link.getToken(), null);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getFileId()).isEqualTo("arquivo-2");
    }

    @Test
    @DisplayName("validarLink retorna vazio para token inexistente")
    void validarLink_tokenInexistente() {
        assertThat(sharingService.validarLink("token-fantasma", null)).isEmpty();
    }

    @Test
    @DisplayName("validarLink respeita allowlist de emails via HashSet.contains()")
    void validarLink_allowlistDeEmails() {
        Set<String> permitidos = Set.of("alice@example.com");
        ShareLink link = sharingService.criarLink(
                "arquivo-3", "dono-3", Duration.ofHours(1), permitidos, Permissao.BAIXAR);

        assertThat(sharingService.validarLink(link.getToken(), "alice@example.com")).isPresent();
        assertThat(sharingService.validarLink(link.getToken(), "bob@example.com")).isEmpty();
        assertThat(sharingService.validarLink(link.getToken(), null)).isEmpty();
    }

    @Test
    @DisplayName("validarLink retorna vazio após link revogado")
    void validarLink_linkRevogado() {
        ShareLink link = sharingService.criarLink(
                "arquivo-4", "dono-4", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);

        sharingService.revogarLink(link.getToken(), "dono-4");

        assertThat(sharingService.validarLink(link.getToken(), null)).isEmpty();
    }

    @Test
    @DisplayName("revogarLink remove o link do mapa")
    void revogarLink_removeDoMapa() {
        ShareLink link = sharingService.criarLink(
                "arquivo-5", "dono-5", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);
        assertThat(sharingService.getTotalLinks()).isEqualTo(1);

        sharingService.revogarLink(link.getToken(), "dono-5");

        assertThat(sharingService.getTotalLinks()).isZero();
    }

    @Test
    @DisplayName("revogarLink lança exceção quando dono não confere")
    void revogarLink_donoErrado() {
        ShareLink link = sharingService.criarLink(
                "arquivo-6", "dono-6", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);

        assertThatThrownBy(() -> sharingService.revogarLink(link.getToken(), "atacante"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Acesso negado");
    }

    @Test
    @DisplayName("buscarLinksAtivos filtra por fileId e retorna List")
    void buscarLinksAtivos_filtraPorArquivo() {
        sharingService.criarLink("arq-A", "dono", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);
        sharingService.criarLink("arq-A", "dono", Duration.ofHours(2), Set.of(), Permissao.BAIXAR);
        sharingService.criarLink("arq-B", "dono", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);

        List<ShareLink> linksA = sharingService.buscarLinksAtivos("arq-A");

        assertThat(linksA).hasSize(2);
        assertThat(linksA).allMatch(l -> l.getFileId().equals("arq-A"));
        assertThat(linksA).isInstanceOf(List.class);
        assertThat(linksA.get(0).getFileId()).isEqualTo("arq-A");
    }

    @Test
    @DisplayName("estatisticas retorna contagem correta por fileId via Collectors.groupingBy")
    void estatisticas_contagemCorreta() {
        sharingService.criarLink("arq-X", "o", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);
        sharingService.criarLink("arq-X", "o", Duration.ofHours(1), Set.of(), Permissao.VISUALIZAR);
        sharingService.criarLink("arq-Y", "o", Duration.ofHours(1), Set.of(), Permissao.BAIXAR);

        Map<String, Long> stats = sharingService.estatisticas();

        assertThat(stats.get("arq-X")).isEqualTo(2L);
        assertThat(stats.get("arq-Y")).isEqualTo(1L);
    }
}

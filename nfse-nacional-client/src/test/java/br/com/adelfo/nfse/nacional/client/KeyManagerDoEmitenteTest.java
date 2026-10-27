package br.com.adelfo.nfse.nacional.client;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.security.auth.x500.X500Principal;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * O ADN de produção anuncia só as ACs raiz ICP-Brasil; repassá-las ao KeyManager padrão faz o JDK
 * enviar uma lista vazia de certificados. Ver {@link KeyManagerDoEmitente}.
 */
class KeyManagerDoEmitenteTest {

    /** As ACs que o adn.nfse.gov.br anunciou em 27/10/2026. */
    private static final Principal[] SO_RAIZES = {
            new X500Principal("CN=Autoridade Certificadora Raiz Brasileira v5, O=ICP-Brasil, C=BR"),
            new X500Principal("CN=Autoridade Certificadora Raiz Brasileira v10, O=ICP-Brasil, C=BR")};

    /** Registra os emissores recebidos e, como o JDK, só escolhe o alias se a lista não o excluir. */
    private static final class Espiao extends X509ExtendedKeyManager {
        final List<Principal[]> emissoresRecebidos = new ArrayList<>();

        private String escolher(Principal[] issuers) {
            emissoresRecebidos.add(issuers);
            return issuers == null ? "emitente" : null;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
            return escolher(issuers);
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return escolher(issuers);
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return issuers == null ? new String[]{"emitente"} : null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return null;
        }
    }

    @Test
    void semOEnvoltorio_aListaDeRaizesExcluiOCertificado() {
        // É o comportamento do JDK que derrubava a conexão.
        assertNull(new Espiao().chooseEngineClientAlias(new String[]{"RSA"}, SO_RAIZES, null));
    }

    @Test
    void comOEnvoltorio_oCertificadoDoEmitenteEOferecidoMesmoAssim() {
        Espiao espiao = new Espiao();
        KeyManagerDoEmitente km = new KeyManagerDoEmitente(espiao);

        assertEquals("emitente", km.chooseEngineClientAlias(new String[]{"RSA"}, SO_RAIZES, null));
        assertEquals("emitente", km.chooseClientAlias(new String[]{"RSA"}, SO_RAIZES, null));
        assertEquals("emitente", km.getClientAliases("RSA", SO_RAIZES)[0]);
        assertNull(espiao.emissoresRecebidos.get(0), "a lista do servidor não deve chegar ao delegado");
    }
}

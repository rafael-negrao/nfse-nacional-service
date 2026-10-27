package br.com.adelfo.nfse.nacional.client;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * {@link X509KeyManager} que oferece o certificado do emitente <b>sem filtrar pelas ACs que o
 * servidor anuncia</b> no {@code CertificateRequest}.
 *
 * <p>O {@code KeyManager} padrão do JDK só envia um certificado se algum emissor da cadeia
 * guardada no keystore constar da lista {@code certificate_authorities} do servidor. Os dois
 * ambientes anunciam listas diferentes: produção restrita lista 176 ACs, intermediárias inclusive;
 * o ADN de produção ({@code adn.nfse.gov.br}) lista <b>só as quatro raízes</b> ICP-Brasil (v5, v10,
 * v11, v12). Um {@code .pfx} que traz apenas o certificado folha — o da Adelfo, emitido pela
 * {@code AC Certisign RFB G5}, é assim — não tem como ligar a folha a uma raiz, e o JDK conclui que
 * não há certificado adequado: responde com uma lista <b>vazia</b>.
 *
 * <p>O sintoma não aponta a causa. O servidor aborta, e o JDK acusa
 * {@code SSLException: Tag mismatch} ao decifrar o alerta — com qualquer cifra, o que faz suspeitar
 * da implementação de ChaCha20 ou de um intermediário na rede. Só o {@code javax.net.debug}
 * revela "No X.509 cert selected". O {@code curl} funciona porque o OpenSSL envia o certificado
 * configurado independentemente da lista, e o servidor aceita a folha sozinha.
 *
 * <p>Ignorar a lista é seguro aqui porque o keystore vem de {@link CertificadoDigital}: um único
 * certificado, o do emitente, que é quem tem de se autenticar em qualquer caso. Diagnosticado em
 * 27/10/2026.
 */
final class KeyManagerDoEmitente extends X509ExtendedKeyManager {

    private final X509KeyManager delegado;

    KeyManagerDoEmitente(X509KeyManager delegado) {
        this.delegado = delegado;
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        return delegado.chooseClientAlias(keyType, null, socket);
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        // O HttpClient do JDK usa SSLEngine, então é por aqui que a escolha passa de fato.
        return delegado instanceof X509ExtendedKeyManager estendido
                ? estendido.chooseEngineClientAlias(keyType, null, engine)
                : delegado.chooseClientAlias(keyType, null, null);
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return delegado.getClientAliases(keyType, null);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return delegado.getServerAliases(keyType, issuers);
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        return delegado.chooseServerAlias(keyType, issuers, socket);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        return delegado.getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        return delegado.getPrivateKey(alias);
    }
}

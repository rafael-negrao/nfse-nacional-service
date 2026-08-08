package br.com.adelfo.nfse.nacional.service.config;

import br.com.adelfo.nfse.nacional.client.CertificadoDigital;
import br.com.adelfo.nfse.nacional.client.NfseHttpClient;
import br.com.adelfo.nfse.nacional.service.NfseService;
import br.com.adelfo.nfse.nacional.service.NfseServiceImpl;
import br.com.adelfo.nfse.nacional.service.ParametrosMunicipaisService;
import br.com.adelfo.nfse.nacional.service.ParametrosMunicipaisServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Auto-configuração Spring Boot da biblioteca NFS-e Nacional.
 *
 * <p>Ativa-se quando a aplicação consumidora expõe um {@code @Bean CertificadoDigital} — não há
 * property de habilitação. Para substituir a implementação padrão, basta declarar um
 * {@code @Bean NfseService} próprio: o {@code @ConditionalOnMissingBean} cede a vez.
 */
@Configuration
@ConditionalOnBean(CertificadoDigital.class)
@EnableConfigurationProperties(NfseProperties.class)
public class NfseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(NfseHttpClient.class)
    public NfseHttpClient nfseHttpClient(CertificadoDigital certificado, NfseProperties properties) {
        return new NfseHttpClient(certificado,
                Duration.ofMillis(properties.getConnectionTimeoutMs()),
                Duration.ofMillis(properties.getReadTimeoutMs()));
    }

    @Bean
    @ConditionalOnMissingBean(NfseService.class)
    public NfseService nfseService(CertificadoDigital certificado, NfseHttpClient httpClient) {
        return new NfseServiceImpl(certificado, httpClient);
    }

    /** Consultas de parametrização municipal — outra API, no ADN; ver a Javadoc da interface. */
    @Bean
    @ConditionalOnMissingBean(ParametrosMunicipaisService.class)
    public ParametrosMunicipaisService parametrosMunicipaisService(NfseHttpClient httpClient) {
        return new ParametrosMunicipaisServiceImpl(httpClient);
    }
}

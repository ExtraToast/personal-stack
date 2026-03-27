package com.jorisjonkers.personalstack.auth.config

import com.jorisjonkers.personalstack.auth.domain.service.TotpService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DomainBeanConfig {
    @Bean
    fun totpService(): TotpService = TotpService()
}

package com.jorisjonkers.privatestack.auth.config

import com.jorisjonkers.privatestack.auth.domain.service.TotpService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DomainBeanConfig {
    @Bean
    fun totpService(): TotpService = TotpService()
}

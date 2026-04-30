package com.jorisjonkers.personalstack.common.observability

import org.aspectj.lang.annotation.Aspect
import org.jooq.DSLContext
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import

@AutoConfiguration
@ConditionalOnProperty(prefix = "personalstack.timing", name = ["enabled"], matchIfMissing = true)
@EnableConfigurationProperties(TimingProperties::class)
@Import(
    TimingAutoConfiguration.WebTiming::class,
    TimingAutoConfiguration.JooqTiming::class,
)
class TimingAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(Aspect::class)
    @EnableAspectJAutoProxy
    class WebTiming {
        @Bean
        fun requestTimingFilter(properties: TimingProperties): RequestTimingFilter = RequestTimingFilter(properties)

        @Bean
        fun methodTimingAspect(properties: TimingProperties): MethodTimingAspect = MethodTimingAspect(properties)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(DSLContext::class)
    class JooqTiming {
        @Bean
        fun jooqTimingCustomizer(properties: TimingProperties): DefaultConfigurationCustomizer {
            val listener = JooqTimingListener(properties)
            return DefaultConfigurationCustomizer { configuration ->
                val combined = configuration.executeListenerProviders() + DefaultExecuteListenerProvider(listener)
                configuration.set(*combined)
            }
        }
    }
}

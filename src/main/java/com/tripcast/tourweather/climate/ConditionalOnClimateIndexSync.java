package com.tripcast.tourweather.climate;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

/**
 * 수동 동기화 API 또는 자동 배치 중 하나라도 켜져 있을 때 공용 빈(서비스, 외부 API 클라이언트)을 등록한다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionalOnClimateIndexSync.AnySyncEnabled.class)
public @interface ConditionalOnClimateIndexSync {

    class AnySyncEnabled extends AnyNestedCondition {

        AnySyncEnabled() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(name = "app.climate-index-sync.api-enabled", havingValue = "true")
        static class ApiEnabled {
        }

        @ConditionalOnProperty(name = "app.climate-index-sync.scheduler-enabled", havingValue = "true")
        static class SchedulerEnabled {
        }
    }
}

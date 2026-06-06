package com.gpr.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.gpr.payroll", "com.gpr.common"})
@EntityScan(basePackages = {"com.gpr.common.entity", "com.gpr.payroll.entity"})
@EnableJpaRepositories(basePackages = "com.gpr.payroll.repository")
public class WosPayrollApplication {
    static void main(String[] args) {
        SpringApplication.run(WosPayrollApplication.class, args);
    }
}

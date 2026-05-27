package com.gpm.payroll;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.gpm.payroll", "com.gpm.common"})
@EntityScan(basePackages = {"com.gpm.common.entity", "com.gpm.payroll.entity"})
@EnableJpaRepositories(basePackages = "com.gpm.payroll.repository")
public class WosPayrollApplication {
    static void main(String[] args) {
        SpringApplication.run(WosPayrollApplication.class, args);
    }
}

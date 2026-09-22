package io.golem.datasync.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class DefaultDatabaseUrlTest {
    @Test
    void fileDatabaseDoesNotCombineIncompatibleH2Options() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        String url = properties.getProperty("spring.datasource.url");
        assertThat(url).startsWith("jdbc:h2:file:");
        assertThat(url).doesNotContain("AUTO_SERVER=TRUE");
    }
}

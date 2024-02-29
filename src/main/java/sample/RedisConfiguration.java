package sample;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfiguration {

	@Bean
	JedisPooled jedisPooled(RedisProperties properties) {
		return new JedisPooled(properties.getUri());
	}

}

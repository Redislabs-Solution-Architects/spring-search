package sample;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(RedisProperties.CONFIG_PREFIX)
public class RedisProperties {

	public static final String CONFIG_PREFIX = "redis";

	private String uri = "redis://localhost:6379";

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

}

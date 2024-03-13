package sample;

import com.redis.om.spring.annotations.EnableRedisDocumentRepositories;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.geo.Point;
import sample.domain.Company;
import sample.repositories.CompanyRepository;

import java.util.Arrays;
import java.util.Set;

@SpringBootApplication
@EnableRedisDocumentRepositories
public class SearchApplication {
	@Autowired
	CompanyRepository companyRepo;

	@Bean
	CommandLineRunner loadTestData() {
		return args -> {
			companyRepo.deleteAll();
			Company redis = Company.of("Redis", "https://redis.com", new Point(-122.066540, 37.377690), 526, 2011, Set.of(CompanyMeta.of("Redis", 100, Set.of("RedisTag"))));
			redis.setTags(Set.of("fast", "scalable", "reliable"));

			Company microsoft = Company.of("Microsoft", "https://microsoft.com", new Point(-122.124500, 47.640160), 182268, 1975, Set.of(CompanyMeta.of("MS", 50, Set.of("MsTag"))));
			microsoft.setTags(Set.of("innovative", "reliable"));

			companyRepo.save(redis);
			companyRepo.save(redis); // save again to test @LastModifiedDate
			companyRepo.save(microsoft);
		};
	}

	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(SearchApplication.class, args);

		System.out.println("Let's inspect the beans provided by Spring Boot:");

		String[] beanNames = ctx.getBeanDefinitionNames();
		Arrays.sort(beanNames);
		for (String beanName : beanNames) {
			System.out.println(beanName);
		}
	}

}

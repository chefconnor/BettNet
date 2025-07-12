package space.bettnet.sporting;

import org.springframework.ai.vectorstore.pinecone.autoconfigure.PineconeVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude={DataSourceAutoConfiguration.class, PineconeVectorStoreAutoConfiguration.class})
@EnableScheduling
public class SportingApplication {

	public static void main(String[] args) {
		SpringApplication.run(SportingApplication.class, args);
	}

}

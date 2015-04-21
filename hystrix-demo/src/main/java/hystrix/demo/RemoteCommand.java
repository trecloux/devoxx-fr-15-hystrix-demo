package hystrix.demo;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RemoteCommand  {

    private final RestTemplate restTemplate = new RestTemplate();

    @HystrixCommand(fallbackMethod = "fallback")
    public String go() {
        return restTemplate.getForObject("http://localhost:9090/call", String.class);
    }

    public String fallback() {
        return "N/A";
    }


}

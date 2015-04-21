package hystrix.backend;

import com.google.common.util.concurrent.Uninterruptibles;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@RestController
public class BackendEndpoint {

    private long responseTime = 200;

    @RequestMapping("/call")
    public String call() {
        Uninterruptibles.sleepUninterruptibly(responseTime, MILLISECONDS);
        return "OK";
    }

    @RequestMapping(value = "/responseTime", method = RequestMethod.POST)
    public void setResponseTime(@RequestBody String responseTime) {
        this.responseTime = Long.parseLong(responseTime);
    }
    @RequestMapping(value = "/responseTime", method = RequestMethod.GET)
    public long getResponseTime() {
        return responseTime;
    }
}

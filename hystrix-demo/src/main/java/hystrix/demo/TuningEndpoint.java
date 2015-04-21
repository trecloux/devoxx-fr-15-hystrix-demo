package hystrix.demo;

import com.netflix.config.ConfigurationManager;
import org.apache.commons.configuration.ConfigurationUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
public class TuningEndpoint {
    @RequestMapping(value = "/timeout", method = POST)
    public void setTimeout(@RequestBody String timeout) {
        ConfigurationManager.getConfigInstance().setProperty("hystrix.command.remoteCommand.execution.isolation.thread.timeoutInMilliseconds", timeout);
    }

    @RequestMapping(value = "/timeout", method = GET)
    public Object getTimeout() {
        ConfigurationUtils.dump(ConfigurationManager.getConfigInstance().subset("hystrix"), System.out);
        return ConfigurationManager.getConfigInstance().getProperty("hystrix.command.remoteCommand.execution.isolation.thread.timeoutInMilliseconds");
    }

}

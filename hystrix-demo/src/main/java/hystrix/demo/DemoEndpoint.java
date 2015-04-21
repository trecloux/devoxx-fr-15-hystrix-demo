package hystrix.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;


@RestController
public class DemoEndpoint {

    private double remoteCommandRatio = 0.1;
    private Random random = new Random();

    @Autowired
    private RemoteCommand remoteCommand;

    @RequestMapping("/demo")
    public String demo(){
        if (shouldCallRemoteSystem()) {
            return remoteCommand.go();
        } else {
            return "OK";
        }
    }

    @RequestMapping("/ping")
    public String ping(){
        return "OK";
    }


    private boolean shouldCallRemoteSystem() {
        return random.nextDouble() <= remoteCommandRatio;
    }
}

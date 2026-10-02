package edu.cit.mingoy.channel;

import org.springframework.stereotype.Component;

@Component
public class InstanceIdentity {

    private final String instanceId;

    public InstanceIdentity() {
        this.instanceId = System.getProperty("shop.instance-id");
    }

    public String getInstanceId() {
        return instanceId;
    }
}
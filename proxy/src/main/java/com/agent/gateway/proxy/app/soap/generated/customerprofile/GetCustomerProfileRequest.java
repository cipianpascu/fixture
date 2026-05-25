package com.agent.gateway.proxy.app.soap.generated.customerprofile;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "GetCustomerProfileRequest", namespace = "http://agent.com/customerprofile")
@XmlAccessorType(XmlAccessType.FIELD)
public class GetCustomerProfileRequest {

    @XmlElement(name = "customerId", namespace = "http://agent.com/customerprofile", required = true)
    private String customerId;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
}

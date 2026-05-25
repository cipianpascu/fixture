package com.agent.gateway.proxy.app.soap.generated.customerprofile;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "GetCustomerProfileResponse", namespace = "http://agent.com/customerprofile")
@XmlAccessorType(XmlAccessType.FIELD)
public class GetCustomerProfileResponse {

    @XmlElement(name = "customerId", namespace = "http://agent.com/customerprofile", required = true)
    private String customerId;

    @XmlElement(name = "fullName", namespace = "http://agent.com/customerprofile", required = true)
    private String fullName;

    @XmlElement(name = "segment", namespace = "http://agent.com/customerprofile", required = true)
    private String segment;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }
}

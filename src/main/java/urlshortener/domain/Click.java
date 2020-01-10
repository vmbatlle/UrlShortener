package urlshortener.domain;

import java.sql.Timestamp;

public class Click {

    private Long id;
    private String hash;
    private Timestamp created;
    private String referrer;
    private String browser;
    private String platform;
    private String ip;
    private String device;
    private String country;

    public Click(Long id, String hash, Timestamp created, String referrer,
                 String browser, String platform, String ip, String country) {
        this.id = id;
        this.hash = hash;
        this.created = created;
        this.referrer = referrer;
        this.browser = browser;
        this.platform = platform;
        this.ip = ip;
        //this.device = device;
        this.country = country;
    }

    public Long getId() {
        return id;
    }

    public String getHash() {
        return hash;
    }

    public Timestamp getCreated() {
        return created;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getBrowser() {
        return browser;
    }
    
    public String getDevice() {
        return device;
    }

    public String getPlatform() {
        return platform;
    }

    public String getIp() {
        return ip;
    }

    public String getCountry() {
        return country;
    }
}

package urlshortener.service;

import urlshortener.domain.Click;

import java.sql.Date;

public class ClickBuilder {

    private String hash;
    private Date created;
    private String referrer;
    private String browser;
    private String platform;
    private String ip;
    // TODO: Use this attribute for its purpose
    // private String device;
    private String country;

    static ClickBuilder newInstance() {
        return new ClickBuilder();
    }

    Click build() {
        return new Click(null, hash, created, referrer,
                browser, platform, ip, country/*, device*/);
    }

    ClickBuilder hash(String hash) {
        this.hash = hash;
        return this;
    }

    ClickBuilder createdNow() {
        this.created = new Date(System.currentTimeMillis());
        return this;
    }

    ClickBuilder noReferrer() {
        this.referrer = null;
        return this;
    }

    ClickBuilder unknownBrowser() {
        this.browser = null;
        return this;
    }

    ClickBuilder unknownPlatform() {
        this.platform = null;
        return this;
    }


    ClickBuilder ip(String ip) {
        this.ip = ip;
        return this;
    }
    
    ClickBuilder referrer(String referrer) {
        this.referrer = referrer;
        return this;
    }
    
    ClickBuilder browser(String browser) {
        this.browser = browser;
        return this;
    }
    
    ClickBuilder platform(String platform) {
        this.platform = platform;
        return this;
    }
    
    /*
    ClickBuilder device(String device) {
        this.device = device;
        return this;
    }
    */

    ClickBuilder withoutCountry() {
        this.country = null;
        return this;
    }

}

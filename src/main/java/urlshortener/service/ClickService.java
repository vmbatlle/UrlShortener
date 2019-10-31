package urlshortener.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import urlshortener.domain.Click;
import urlshortener.repository.ClickRepository;

import java.sql.Date;
import java.util.List;

@Service
public class ClickService {

    private static final Logger log = LoggerFactory
            .getLogger(ClickService.class);

    private final ClickRepository clickRepository;

    public ClickService(ClickRepository clickRepository) {
        this.clickRepository = clickRepository;
    }

    public void saveClick(String hash, String ip) {
        Click cl = ClickBuilder.newInstance().hash(hash).createdNow().ip(ip).build();
        cl = clickRepository.save(cl);
        log.info(cl.toString());
        log.info(cl != null ? "[" + hash + "] saved with id [" + cl.getId() + "]" : "[" + hash + "] was not saved");
    }

    public void saveClick2(String hash, String ip, String os, String device, String browser, String referrer) {
        Click cl = ClickBuilder.newInstance().hash(hash).createdNow().ip(ip).browser(browser).platform(os).referrer(referrer).build();
        cl = clickRepository.save(cl);
        log.info(cl.toString());
        log.info(cl != null ? "[" + hash + "] saved with id [" + cl.getId() + "]" : "[" + hash + "] was not saved");
    }

    public String clicksRecived(){
        long lim = 100;
        long off = 0;
        List<Click> lc = clickRepository.list(lim, off);
        
        JSONObject json = new JSONObject();
        try{
            JSONArray array = new JSONArray();
            JSONObject item = new JSONObject();
            
            for (Click click : lc) {
                item.put("URI", ("http://.../"+click.getHash()));
                item.put("Referrer URI", click.getReferrer());
                item.put("Browser", click.getBrowser());
                item.put("OS", click.getPlatform());
                item.put("IP", click.getIp());
                item.put("Date", click.getCreated().toGMTString());
                array.put(item);
                item = new JSONObject();
            }
            json.put("clicks", array);
            return json.toString(4);
        }catch(Exception e){
            e.printStackTrace();
            return json.toString();
        }
        
    }

}

package urlshortener.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import urlshortener.domain.Click;
import urlshortener.repository.ClickRepository;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
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
        log.info(cl != null ? "[" + hash + "] saved with id [" + cl.getId() + "]" : "[" + hash + "] was not saved");
    }

    public void saveClickUserAgent(String hash, String ip, String os, String device, String browser, String referrer) {
        Click cl = ClickBuilder.newInstance().hash(hash).createdNow().ip(ip).browser(browser).platform(os).referrer(referrer).build();
        cl = clickRepository.save(cl);
        log.info(cl != null ? "[" + hash + "] saved with id [" + cl.getId() + "]" : "[" + hash + "] was not saved");
    }

    public List<Click> clicksReceived(long pag, long lim){
        return clickRepository.list(lim, (pag - 1)*lim);
    }
    
    public List<Click> clicksReceivedDated(LocalDateTime time, long pag, long lim){
        return clickRepository.findByDate(time, lim, (pag - 1)*lim);
    }

    public Long count(){
        return clickRepository.count();
    }
    
    public Long countByDate(LocalDateTime time){
        return clickRepository.countByDate(time);
    }
    
    public String clicksReceived(){
        // TODO: Use future parameters for escalability
        // long lim = 100;
        // long off = 0;
        JSONObject json = this.jsonClicks();
        try{
            return json.toString(4);
        }catch(Exception e){
            e.printStackTrace();
            return json.toString();
        }
        
    }

    public JSONObject jsonClicks(){
        long lim = 100;
        long off = 0;
        List<Click> lc = clickRepository.list(lim, off);
        
        JSONObject json = new JSONObject();
        try{
            JSONArray array = new JSONArray();
            JSONObject item = new JSONObject();
            
            for (Click click : lc) {
                item.put("URI", click.getId());
                item.put("Referrer URI", click.getReferrer());
                item.put("Browser", click.getBrowser());
                item.put("OS", click.getPlatform());
                item.put("IP", click.getIp());
                DateFormat dateFormat = new SimpleDateFormat("d mon yyyy hh:mm:ss GMT");
                item.put("Date", dateFormat.format(click.getCreated()));
                array.put(item);
                item = new JSONObject();
            }
            json.put("clicks", array);
            
        }catch(Exception e){
            e.printStackTrace();
        }
        return json;
    }

}

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

    public Click saveClick(String hash, String ip) {
        Click cl = ClickBuilder.newInstance().hash(hash).createdNow().ip(ip).build();
        cl = clickRepository.save(cl);
        log.info(cl != null ? "[" + hash + "] saved with id [" + cl.getId() + "]" : "[" + hash + "] was not saved");
        return cl;
    }

    /**
     * Create a new click with the given params
     * @param hash hash of the sorted url
     * @param ip address of the client
     * @param os operative system of the client
     * @param device device used by the client
     * @param browser browser used by the client
     * @param referrer web host that contains the short url
     * @return click created
     */
    public Click saveClickUserAgent(String hash, String ip, String os, String device, String browser, String referrer) {
        Click cl = ClickBuilder.newInstance().hash(hash).createdNow().ip(ip).browser(browser).platform(os).referrer(referrer).build();
        cl = clickRepository.save(cl);
        log.info(cl != null ? "[" + hash + "] saved with id [" + cl.getId() + "]" : "[" + hash + "] was not saved");
        return cl;
    }

    public List<Click> clicksReceived(long pag, long lim){
        return clickRepository.list(lim, (pag - 1)*lim);
    }
    
    public List<Click> allClicks(){
        return clickRepository.listAll();
    }
    
    /**
     * 
     * @param id Max id value to get
     * @return all the clicks with ids lower than id
     */
    public List<Click> allClicksUntil(Long id){
        return clickRepository.listAll(id);
    }
    
    /**
     * 
     * @param time oldest date of clicks
     * @param pag  page of clicks according lim
     * @param lim size of page
     * @return page of lim clicks (or less) with date>=time
     */
    public List<Click> clicksReceivedDated(LocalDateTime time, long pag, long lim){
        return clickRepository.findByDate(time, lim, (pag - 1)*lim);
    }
    
    /**
     * 
     * @param start oldest date of clicks
     * @param end most recent date of clicks
     * @param pag  page of clicks according lim
     * @param lim size of page
     * @return page of lim clicks (or less) with date>=time
     */
    public List<Click> clicksReceivedDated(LocalDateTime start, LocalDateTime end, long pag, long lim){
        return clickRepository.findByDate(start, end, lim, (pag - 1)*lim);
    }

    public Long count(){
        return clickRepository.count();
    }
    
    /**
     * 
     * @param time oldest date of clicks
     * @return number of clicks with date>=time
     */
    public Long countByDate(LocalDateTime time){
        return clickRepository.countByDate(time);
    }

}

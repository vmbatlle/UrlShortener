package urlshortener.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.List;

import com.google.gson.Gson;

import urlshortener.domain.Download;
import urlshortener.web.FilePetitions;
import urlshortener.domain.Click;
import urlshortener.service.ClickService;

/**
 * Component that periodicaly creates files of download petitions
 * that are not ready yet.
 */
@Component
public class FileCreator {

    private final FilePetitions fg;
    private final ClickService clickService;

    public FileCreator(FilePetitions f_g, ClickService cs){
        this.fg = f_g;
        this.clickService = cs;
    }

    @Scheduled(fixedDelay = 2000)
    private void periodicCheck() {
        for (Long id : fg.getKeys()){//Iterate over all petition keys
            Download d = fg.getDownload(id);
            if (!d.getReady()){ // If petition is no ready yet create the according json file
                Gson gson = new Gson();
                List<Click> clicks = clickService.allClicksUntil(id);
                String filePath = "files/" + d.getId() + ".json";
                File json = new File(filePath);
                try {
                    json.createNewFile();
                    Writer wr =  new FileWriter(filePath);
                    gson.toJson(clicks, wr);
                    wr.flush();
                    wr.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Set the petition to ready
                d.setReady(true);
            }
        }
    }
};
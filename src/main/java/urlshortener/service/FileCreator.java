package urlshortener.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;

import urlshortener.domain.Download;
import urlshortener.web.FilePetitions;
import urlshortener.domain.Click;
import urlshortener.service.ClickService;

import org.jsoup.Connection.Response;

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
        for (Long id : fg.getKeys()){
            Download d = fg.getDownload(id);
            if (!d.getReady()){
                Gson gson = new Gson();
                List<Click> clicks = clickService.allClicks();
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
                d.setReady(true);
            }
        }
    }
};
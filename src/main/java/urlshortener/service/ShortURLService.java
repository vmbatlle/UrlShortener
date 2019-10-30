package urlshortener.service;

import org.springframework.stereotype.Service;
import urlshortener.domain.ShortURL;
import urlshortener.repository.ShortURLRepository;
import urlshortener.web.UrlShortenerController;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@Service
public class ShortURLService {

    private final ShortURLRepository shortURLRepository;

    public ShortURLService(ShortURLRepository shortURLRepository) {
        this.shortURLRepository = shortURLRepository;
    }

    public ShortURL findByKey(String id) {
        return shortURLRepository.findByKey(id);
    }

    public ShortURL save(String url, String sponsor, String ip) {
        if (sponsor != null) { System.out.println("SPONSOR: " + sponsor);}
        ShortURL su = ShortURLBuilder.newInstance()
                .target(url)
                .sponsor(sponsor)
                .uri((String hash) -> linkTo(methodOn(UrlShortenerController.class).redirectTo(hash, null)).toUri())
                .createdNow()
                .randomOwner()
                .temporaryRedirect()
                .treatAsSafe()
                .ip(ip)
                .unknownCountry()
                .build();
        System.out.println("HASH: " + su.getHash());
        return shortURLRepository.save(su);
    }
}

package urlshortener.service;

import org.springframework.stereotype.Service;
import urlshortener.domain.ShortURL;
import urlshortener.repository.ShortURLRepository;
import urlshortener.web.UrlShortenerController;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.util.List;

@Service
public class ShortURLService {

    private final ShortURLRepository shortURLRepository;

    public ShortURLService(ShortURLRepository shortURLRepository) {
        this.shortURLRepository = shortURLRepository;
    }

    public ShortURL findByKey(String id) {
        return shortURLRepository.findByKey(id);
    }

    public void delete(String id) {
        shortURLRepository.delete(id);
    }

    public List<ShortURL> all() {
        long limit = shortURLRepository.count();
        return shortURLRepository.list(limit, 0L);
    }

    public ShortURL save(String url, String sponsor, String ip) {
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
        return shortURLRepository.save(su);
    }
}

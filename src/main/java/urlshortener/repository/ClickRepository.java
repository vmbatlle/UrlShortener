package urlshortener.repository;

import urlshortener.domain.Click;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickRepository {

    List<Click> findByHash(String hash);

    List<Click> findByDate(LocalDateTime time, Long limit, Long offset);

    Long clicksByHash(String hash);

    Click save(Click cl);

    void update(Click cl);

    void delete(Long id);

    void deleteAll();

    Long count();
    
    Long countByDate(LocalDateTime time);

    List<Click> list(Long limit, Long offset);
}

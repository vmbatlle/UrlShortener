package urlshortener.repository;

import urlshortener.domain.Click;

import java.time.LocalDateTime;
import java.util.List;

public interface ClickRepository {

    List<Click> findByHash(String hash);

    /**
     * 
     * @param time oldest date of clicks
     * @param limit  number of elements to retrieve
     * @param offset first element to retrieve
     * @return limit number of clicks with date>=time
     */
    List<Click> findByDate(LocalDateTime time, Long limit, Long offset);
    
    /**
     * 
     * @param start oldest date of clicks
     * @param end most recent date of clicks
     * @param limit  number of elements to retrieve
     * @param offset first element to retrieve
     * @return limit number of clicks with date>=time
     */
    List<Click> findByDate(LocalDateTime start, LocalDateTime end, Long limit, Long offset);

    Long clicksByHash(String hash);

    Click save(Click cl);

    void update(Click cl);

    void delete(Long id);

    void deleteAll();

    Long count();
    
    /**
     * @param time oldest date of clicks
     * @return number of clicks with date>=time
     */
    Long countByDate(LocalDateTime time);

    List<Click> list(Long limit, Long offset);

    /**
     * @return all the clicks
     */
    List<Click> listAll();
    
    /**
     * 
     * @param id max id
     * @return return all the clicks with id<=@param{id}
     */
    List<Click> listAll(Long id);
}

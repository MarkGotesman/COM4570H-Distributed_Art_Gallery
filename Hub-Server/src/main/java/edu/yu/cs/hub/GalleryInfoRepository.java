package edu.yu.cs.hub;

import java.net.URL;
import java.util.*;

import javax.enterprise.context.ApplicationScoped;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

@ApplicationScoped
public class GalleryInfoRepository implements PanacheRepository<GalleryInfo> {
    public Map<Long, URL> toMap() {
        Map<Long, URL> giMap = new HashMap<>();
        List<GalleryInfo> giList = listAllActive();

        for (GalleryInfo gi : giList) {
            giMap.put(gi.id, gi.url);
        }

        return giMap;
    }

    public List<GalleryInfo> listAllActive() {
        return find("active is true").list();
    }

}
package edu.yu.cs.hub;

import java.net.URL;

import javax.persistence.Entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;

@Entity
public class GalleryInfo extends PanacheEntity {
    public URL url;
    public boolean active = true;
}

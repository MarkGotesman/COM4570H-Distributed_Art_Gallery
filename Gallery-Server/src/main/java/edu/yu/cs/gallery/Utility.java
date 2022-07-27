package edu.yu.cs.gallery;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import edu.yu.cs.gallery.repositories.GalleryRepository;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;

import io.quarkus.logging.Log;

@Singleton
public class Utility {
    protected Gallery gallery;
    protected Map<Long, URL> allServers;
    protected Long leaderID;
    
    @Inject
    GalleryRepository gr;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    //Forwards the URL to the client with a 307
    protected Response temporaryRedirect(long id, @Context UriInfo uriInfo) throws URISyntaxException {
        if (allServers.keySet().contains(id)) {
            return Response.temporaryRedirect(new URI(allServers.get(id).toString() + uriInfo.getPath())).build();
        }
        return Response.status(NOT_FOUND).build();
    }
    
    //URI must contain the string "batch" in place of the galleryId 
    @Fallback(fallbackMethod = "fallbackRedirect")
    protected void redirect(long galleryId, UriInfo uriInfo, HttpMethod httpMethod, Object body, List<Object> returnEntities) throws URISyntaxException {

        WebClient webClient = WebClient.create(allServers.get(galleryId).toString());

        String path = uriInfo.getPath().replace("batch", Long.toString(galleryId));

        ResponseEntity<Object> response = webClient.method(httpMethod)
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toEntity(Object.class)
                .block();

        returnEntities.addAll(List.of(response.getBody()));
    }

    protected Response readRedirect (long[] galleryIDs, UriInfo uriInfo, Request request, Runnable<Response> localMethod) throws URISyntaxException {
        List<Object> returnEntities = new ArrayList<>();
        
        for (long galleryID : galleryIDs) {
            if (allServers.containsKey(galleryID)) {
                
                if (galleryID == gallery.id) {
                    Response response = localMethod.run(this.gallery);
                    returnEntities.addAll(List.of(response.getEntity()));
                    response.getStatus();
                } else {
                    redirect(galleryID, uriInfo, HttpMethod.valueOf(request.getMethod()), "",
                            returnEntities);
                }
            }
        }
        
        return Response.status(Status.OK).entity(returnEntities).build();
    }
    
    protected Response writeRedirect(Gallery[] galleries, UriInfo uriInfo, Request request, Runnable<Response> localMethod) throws URISyntaxException {

        //If the user attempts to make a batch write to a server that is not the leader, return a temporaryRedirect to the correct server.
        if (gallery.id != leaderID) {
            return temporaryRedirect(leaderID, uriInfo);
        }

        List<Object> returnEntities = new ArrayList<>();

        for (Gallery gallery : galleries) {
            if (allServers.containsKey(this.gallery.id)) {
                

                if (this.gallery.id == gallery.id) {
                    Response response = localMethod.run(gallery);
                    returnEntities.addAll(List.of(response.getEntity()));
                    response.getStatus();
                } else {
                     redirect(gallery.id, uriInfo, HttpMethod.valueOf(request.getMethod()), gallery.artList, returnEntities);
                }
            }
        }

        return Response.status(Status.OK).entity(returnEntities).build();
    }
    
    protected void fallbackRedirect (long galleryId, UriInfo uriInfo, HttpMethod httpMethod, Object body, List<Object> returnEntities) throws URISyntaxException {
        Log.info("Failed Gallery ID: " + galleryId + ", URI: " + uriInfo);
        returnEntities.add("Failed call to Gallery ID: " + galleryId );
    }
    
    @FunctionalInterface
    public interface Runnable<E> {
        E run(Gallery gl) throws URISyntaxException;
    }
}
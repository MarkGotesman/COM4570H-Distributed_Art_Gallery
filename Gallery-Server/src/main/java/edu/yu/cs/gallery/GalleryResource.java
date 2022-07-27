package edu.yu.cs.gallery;

import edu.yu.cs.gallery.repositories.ArtRepository;
import edu.yu.cs.gallery.repositories.GalleryRepository;

import java.util.*;
import java.net.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response;

import javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.*;

import org.springframework.http.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import edu.yu.cs.gallery.Utility.Runnable;


@Path("/galleries")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON_VALUE)
@Consumes(MediaType.APPLICATION_JSON_VALUE)
public class GalleryResource {
    @Inject 
    GalleryRepository gr;
    @Inject
    ArtRepository ar;
    
    @Inject 
    ServerStartUp ssu;
    @Inject 
    Utility utility;
    
    @ConfigProperty(name = "serverURL")
    String serverURL;

    @ConfigProperty(name = "hubURL")
    String hubURL;

    @GET
    public Response getOnServer() {
        return Response.status(Status.OK).entity(gr.findAll().firstResult()).build();
    }

    @GET
    @Path("{id}")
    public Response getById(@PathParam("id") Long id, @Context UriInfo uriInfo) throws URISyntaxException {
        if (utility.gallery == null || id != utility.gallery.id) {
            return utility.temporaryRedirect(id, uriInfo);
        }
        return Response.status(Status.OK).entity(gr.findById(id)).build();
    }

    @PUT
    @Path("{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Gallery gallery, @Context UriInfo uriInfo) throws URISyntaxException {
        if (utility.gallery == null || id != utility.gallery.id) {
            return utility.temporaryRedirect(id, uriInfo);
        }
        Gallery currentGallery = gr.findById(id);
        if (currentGallery == null) {
            throw new NotFoundException();
        }
        currentGallery.name = gallery.name;
        return Response.status(Status.OK).entity(currentGallery).build();
    }

    @DELETE
    @Path("{id}")
    @Transactional
    public Response deleteById(@PathParam("id") Long id, @Context UriInfo uriInfo) throws URISyntaxException {
        if (utility.gallery == null || id != utility.gallery.id) {
            return utility.temporaryRedirect(id, uriInfo);
        }
        boolean deleted = gr.deleteById(id);
        if (deleted) {
            utility.gallery = null;
            WebClient webClient = WebClient.create("https://" + hubURL);
    
            webClient.delete()
                    .uri("/hub/" + id)
                    .retrieve()           
                    .bodyToMono(Long.class).block();
        }
        return deleted ? Response.noContent().build() : Response.status(BAD_REQUEST).build();
    }

    /*
     * Upon creation of a gallery with a POST:
     * User -> utility Gallery-Server (GalleryResource): Creating a gallery
     * utility Gallery-Server (GalleryResource) -> Hub-Server (Hub): Posting it's
     * url/"registration"
     * Hub-Server (Hub) -> ALL Gallery-Server (GalleryResource): Return the list of
     * the IP addresses
     * 
     * Upon user requesting gallery info in a GET:
     * utility Gallery-Server (GalleryResource): Check if utility gallery can process the
     * request (is there a global way to check all incoming request to a given
     * resource?)
     */

    //User talks to gallery
    @POST
    @Transactional
    public Response create(Gallery gallery, @Context UriInfo uriInfo) throws UnknownHostException, MalformedURLException {
        if (utility.gallery != null) {
            return Response.status(409, "Gallery-Server already has a gallery assigned").build();
        }
        
        utility.gallery = gallery;
        gallery.url = new URL ("https://" + serverURL);
        gallery.id = this.registerWithHub(gallery.url);
        
        gr.persist(gallery);
        if (!gr.isPersistent(gallery)) {
            throw new NotFoundException();
        }

        UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        uriBuilder.path(Long.toString(gallery.id));
        
        return Response.created(uriBuilder.build()).entity(gallery).status(Status.CREATED).build();
    }
    
    //Gallery talks to hub
    private long registerWithHub(URL url) throws UnknownHostException {
        WebClient webClient = WebClient.create("https://" + hubURL);
        Long response = webClient.post()
                .uri("/hub")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"url\":\"" + url + "\"}")
                .retrieve()           
                .bodyToMono(Long.class).block();

        return response;
    }

    //Hub talks to the gallery and sets the map of the IDs to the URLs.
    @POST 
    @Path("/servers")
    public void updateIPs(Map<Long, URL> as) {
        utility.allServers = as;
    }
    
    // Hub talks to the gallery and sets the leaderID.
    @POST 
    @Path("/leader")
    public void updateLeader(Long leaderID) {
        utility.leaderID = leaderID;
    }

// ---------------------------------------Batch-Requests--------------------------------------------//
    @GET
    @Path("/batch")
    public Response getOnServer(@QueryParam("gallery") long[] galleries, @Context UriInfo uriInfo, @Context Request request) throws URISyntaxException {
        Runnable<Response> localMethod = (Gallery gl) -> getById(gl.id, uriInfo);
    
        return utility.readRedirect(galleries, uriInfo, request, localMethod);
    }

    // For testing purposes
    @Path("/servers")
    @GET
    public Map<Long, URL> IPs() {
        return utility.allServers;
    }

    @Path("/leader")
    @GET
    public long leader() {
        return utility.leaderID;
    }

}
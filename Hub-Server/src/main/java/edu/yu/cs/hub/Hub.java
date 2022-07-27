package edu.yu.cs.hub;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import javax.inject.Inject;
import javax.transaction.Transactional;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.net.URL;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.http.MediaType;


@Path("/hub")
@Produces(MediaType.APPLICATION_JSON_VALUE)
@Consumes(MediaType.APPLICATION_JSON_VALUE)
public class Hub { 
    @Inject
    GalleryInfoRepository galleryInfoRepo;
    @Inject
    Utility utility;
    
    //Gallery talking to hub (during user POST to create a gallery)
    //Hub talks back to gallery and returns the updated list of the IPs 
    @POST
    @Transactional
    public long create(GalleryInfo gi, @Context UriInfo uriInfo) throws JsonProcessingException {
        galleryInfoRepo.persist(gi);
        utility.setLeaderID();
        utility.updateServers();

        UriBuilder uriBuilder = uriInfo.getAbsolutePathBuilder();
        uriBuilder.path(Long.toString(gi.id));
        
        return gi.id;
    }
    
    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public Response update(@PathParam("id") Long id, URL url) {
        GalleryInfo gi = galleryInfoRepo.findById(id);
        if (gi == null) {
            throw new NotFoundException();
        }
        gi.url = url; 
        
        utility.updateServers();
        return Response.status(Status.OK).build();
    }
    
    @DELETE
    @Path("{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        URL url = galleryInfoRepo.findById(id).url;
        boolean deleted = galleryInfoRepo.deleteById(id);
        if (deleted) {
            utility.updateServers(Arrays.asList(url));
        }
        return deleted ? Response.noContent().build() : Response.status(BAD_REQUEST).build();
    }

    // for testing
    @GET
    @Path("/resetleader")
    public void resetLeader() {
        utility.setLeaderID();
        utility.updateServers();
    }
}



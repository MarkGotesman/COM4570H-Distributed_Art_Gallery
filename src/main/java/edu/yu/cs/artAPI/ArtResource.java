package edu.yu.cs.artAPI;

import java.util.List;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.core.Response;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import edu.yu.cs.artAPI.repositories.ArtRepository;

@Path("/arts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArtResource {

    @Inject ArtRepository ar;
    
    @GET
    public List<Art> getAll() {
        return ar.listAll();
    }

    @GET
    @Path("{id}")
    public Art getById(@PathParam("id") Long id) {
        return ar.findById(id);
    }
    
    @GET
    @Path("name/{name}")
    public Art getByName(@PathParam("name") String name) {
        return ar.findByName(name);
    }
    
    @GET
    @Path("creator/{creator}")
    public List<Art> getByCreator(@PathParam("creator") String creator) {
        return ar.findByCreator(creator);
    }
    
    @GET
    @Path("gallery/{gallery}")
    public List<Art> getByGallery(@PathParam("gallery") String gallery) {
        return ar.findByGallery(gallery);
    }

    @POST
    @Transactional
    public Response create(Art art) {
        ar.persist(art);
        if (ar.isPersistent(art)) {
            return Response.status(Status.CREATED).entity(art).build();
        }
        return Response.status(NOT_FOUND).build();
    }
  
    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response update(@PathParam("id") Long id, Art art) {
        Art entity = ar.findById(id);
        if (entity == null) {
            return Response.status(NOT_FOUND).build();
        }
        entity.name = art.name;
        entity.creator = art.creator;

        return Response.status(Status.OK).entity(entity).build();
    } 

    @DELETE
    @Path("{id}")
    @Transactional
    public Response deleteById(@PathParam("id") Long id) {
        // Response response = Response.status(Status.CREATED).entity(art).build();
        boolean deleted = ar.deleteById(id);
        return deleted ? Response.noContent().build() : Response.status(BAD_REQUEST).build();
    }
}
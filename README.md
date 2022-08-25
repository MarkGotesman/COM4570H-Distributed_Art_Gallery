# Part 1: Introduction & Technology Used
### Goal:
Model a distributed datastore with horizontal scaling of servers, while remaining client agnostic via redirects. In our project, we modeled art galleries storing art.


### Technology Used:
**Language:** Java.

**Server Framework:** Quarkus.

**Web Tunneling Service:** Ngrok.

**External HTTP Client:** Postman.

**Internal (Java) HTTP Client:** Spring Webclient.

**Database:** H2.

**JPA Implementation:** Hibernate ORM, Simplified with Panache.

Significant packages used in code:
java.net, javax.ws, org.eclipse.microprofile, org.springframework, quarkus.hibernate.orm.panache

Launching was done via environment variables in VSCode launch.json launch configurations, and debugging was likewise done within VS Code and debug ports. 

### Final Result & Demo:
https://drive.google.com/file/d/1BdabtOh2molJYoU2TSoxmjRgW2eb8lwb

# Part 2: Coding Process
## Stage 1: Persistence
**Entities.** Created Art and Gallery entities as models, extended PanacheEntity to inherit many useful methods. Fields were kept simple (Art has a name, creator, and Gallery; Gallery just a name and list of Arts). Many Arts would be stored under one Gallery, so Art’s Gallery field was marked with the @ManyToOne annotation, and Gallery was given a field called artList annotated with @OneToMany(mappedBy = "gallery". (Files created: Art.java, Gallery.java)

**Repositories.** Decided to use the repository pattern for facilitating the relationship between the entities and the database, extended PanacheRepository to inherit many useful methods for querying the database. Created an advanced lookup method in ArtRepository to execute an SQL query matching 1, 2, or 3 parameters (where when null is passed in for those parameters, the SQL wildcard sign “%” is used for that parameter in the query). Used an H2 database to successfully persist data after a session is closed (information was written to disk and not just in memory). (Files created: ArtRepository.java, GalleryRepository.java). 

**Endpoints/Resources.** Created basic endpoints that facilitated CRUD operations (i.e., HTTP requests GET, POST, PUT, and DELETE) for Arts and Galleries. This required injecting the relevant repositories into these classes, and calling the appropriate methods. Path parameters were used for IDs, as well as query parameters for the Art query described above. At this point, multiple galleries were allowed for one server. The relationship between Arts and Galleries was properly mapped, where a specific Gallery could be queried for only its associated pieces of Art. Response objects were returned, which contained the relevant status codes for the call (200 if OK, 404 if not found, etc.), and the modified/queried entities were returned in the body. (Files created: ArtResource.java, GalleryResource.java).
## Stage 2: Server-Hub Communication 
**Project Structure.** Stage 1 had kept the project totally local, running one server on one machine with one database. In stage 2, the intent was to distribute the load of that one server onto a scalable number of servers, where each server would have only a single Gallery associated with it. The distributed service was intended to be client agnostic, meaning that though there may be multiple servers with multiple URLs, if a client has the URL for one of them, they should be able to access the data there or be redirected to the server where the requested data resides. To accomplish this, a Hub-server was created which facilitated intercommunication between multiple Gallery-servers. Once a Gallery was posted to a Gallery-server, that Gallery-server would communicate to the Hub its URL (and receive an ID), and the Hub would create an internal map matching the ID of the Gallery to its URL. Then, the Hub would propagate that map to all registered Gallery-servers. With that map, if a client made a request to http://galleryserver1…/galleries/2, Gallery-server 1 would redirect the client (via HTTP response code 307 “Temporary Redirect”) to the URL of the resource (e.g. http://galleryserver2…/galleries/2).

**External Software.** To facilitate the now distributed nature of the project, more software had to be brought in:

**Web Tunneling Service:** Ngrok is a service that enables localhost ports to be accessed anywhere, with a very simple CLI. With a pro account (purchased for the project), multiple tunnels could be opened at once, with persistent URLs. These URLs were set as environment variables on startup of a Gallery-server, and pulled into the servers with config properties, to be sent to the Hub upon registration. 
**Internal (Java) HTTP Client:** Spring Webclient was used in our Java code to make requests between servers. Though the syntax was a bit hard to learn, eventually we had a working system for making HTTP requests from within Java.

**Hub-Server.** Newly created Java project to house the Hub.
Entity and Repository. Hub did not store an entire Gallery entity with all the associated pieces of Art; all it needed to know was the Gallery ID and the URL of the Galler-server where that Gallery resides. So, we created a stripped-down version of a Gallery called GalleryInfo, persisted again with H2 and the repository pattern. We also made a method  toMap() that returned the contents of the GalleryInfoRepository as a map of Long (the IDs) mapped to URL. (Files created: GalleryInfo.java, GalleryInfoRepository.java).

Hub Endpoint. The /hub endpoint was created as the entrypoint for all Gallery-servers to communicate with the Hub. A POST endpoint was where a Gallery-server initiated communication and first sent its URL. The Hub persisted a GalleryInfo (thereby assigning a unique ID), sent the URL map to all the servers that had registered with it, and returned the assigned ID back to the Gallery-server so that the created Gallery could be associated with that ID (and that even between Gallery-servers, the ID would always be unique). Sending the URL map required looping through the entire GalleryInfoRepository, and sending the entire map to each Gallery-server (accessed by the URL of the corresponding GalleryInfo entity) at a specially designated endpoint (http://.../galleries/servers). (File created: Hub.java).

Gallery-Server. Renamed project of the code from stage 1, now only houses one gallery per server.

Gallery-Server to Hub-Server Communication. The POST endpoint for creating a Gallery was reworked to facilitate communication. First, a check is made to see if this Gallery-server already has a Gallery associated with it; if it does, the POST immediately returns. Otherwise, the POST continues to assign the URL field of the Gallery to the URL pulled from the config property (which was set by an environment variable), and set its ID to the ID returned by the POST call to the Hub-server at the /hub endpoint. That call is what initiates the Hub to propagate (or re-propagate) the updated URL map to all the registered servers. When a Gallery-server receives that call, it stores the map as a local variable and does not persist it on disk. This was done because upon a restart of the system, there is no guarantee that the old URLs are still current, and the map should therefore be rebuilt when the Hub-server next makes a call propagating the current map.

Startup. A critical part of this stage was to have any Gallery-server (with a Gallery currently persisted to it) to register itself with the Hub on startup and provide its current URL. This is because the URL may have changed between sessions, and that information needs to be updated in the Hub and propagated to all other Gallery-servers. This issue of having the server execute behavior upon startup proved tricky, as the few patterns that we found either made the call too early in the bootstrap lifecycle (where the Gallery-server was not yet ready to receive communication back from the Hub-server) or too late (the Gallery-server would execute the process lazily, only communicating with the Hub when a client first communicated with it). We eventually did find something that worked, namely having a startup class annotated with @QuarkusMain and a method that implemented QuarkusApplication. With this syntax, we were able to force a Gallery-server to register itself with the Hub upon startup, via a PUT call. In the Hub, this updated the URL of the corresponding GalleryInfo. (File created: ServerStartUp.java). 

Utility Class. Another place of code-refactoring was in creating a utility class to hold useful methods accessible across the Gallery-server project. It first of all holds a cached-version of the Gallery that is associated with its Gallery-server (this variable is set in ServerStartUp), as well as a simple redirect method to return a 307 when the client calls a resource not located on this server, as explained in “Project Structure” above. This method takes the ID of the Gallery that the user requested data from, as well as a UriInfo object which is pulled by the @Context annotation in the method headers across GalleryResource and ArtResource. The URI of the redirect is constructed, with the path pulled from the UriInfo and the domain name pulled from the URL map as the value for the passed in ID of a Gallery. (File created: Utility.java). 

Redirects. An if statement was included in the first line of every resource method, which checked if the requested Gallery ID matched that of the Gallery entity associated with the current server. If it did not match, or if there was no Gallery registered at all, then a call to Utility.redirect() was made, passing in the intended ID of the call as well as the UriInfo.
## Stage 3: Batch Reading
We added functionality to allow for a get call on multiple galleries and multiple art pieces

With query params, the user can indicate which galleries they want to get the information from. 

Added a “/batch” endpoint in GalleryResource and ArtResource, which then calls a redirect method. 

The redirect method loops through the galleries specified in the query params and for each gallery, it takes the uri of the request found using UriInfo and replaces the word batch with the galleryID of the current gallery. This makes it so that the new URI fits the endpoint of the get methods in either GalleryResource or ArtResource (the call to batch gallery going from /galleries/batch to /galleries/galleryID and the batch call to arts going from /galleries/batch/arts to /galleries/galleryID/arts). Then we call a get on that new URI using Spring WebClient and then add the results to a list of entities to return from all of the galleries. Since we have one method for both the art and gallery get, the response could be either a single gallery or an artList, so we have a if/else to do the proper method to add the response to the list. When done looping through every gallery, we return a list of all searched for entities.

As opposed to the “client redirect” of stage 2, which returns the URI of the proper server to the client and relies on the client to follow the redirect, this “server redirect” has the server loop through all of the servers, make the http call, gather all of the information and then send back a completed list to the user.
## Stage 4: Batch Writing/Leader Election
Similar to the previous stage, we added functionality to allow for posting and putting multiple pieces of art to multiple galleries in one request.

To help with this, we first edited ArtResource’s post method to allow for posting multiple pieces of art to one gallery. We used a try-catch to parse the json. First, it attempts to parse the json into a single art object and catches an exception that would be thrown if the json contained multiple art objects. Then, we do another try-catch to attempt to parse it into multiple art objects, catching in the case where the json contained neither a single art nor multiple pieces of art. 

We also changed ArtResource’s put method to allow for multiple arts to be entered.

We then added a /batch endpoint for both the post method and put method that calls a redirect method that deals with the requests to the galleries. We changed the format of the read redirect and write redirect to call a method that prepares it for a call to the second redirect method, whose logic is the same for a read and write. The prepper methods loops through either the galleryIDs or the Gallery objects and calls the inner redirect method whose function is the same as the previous stage. 

We then realized that using these redirect methods, a server could end up calling a request on itself, which is not ideal. To solve that, we made a FunctionalInterface of Runnable that overrides the run() method. Then, in each batch request, in both GalleryResource and ArtResource, we define a lambda that calls the proper method, depending on what the request is (in the batch read, the method called by the lambda is getAll). We then pass that lambda as an additional parameter to the redirect() method. Then, in the redirect method, we now check if the server this request was called on is the same as the server being looped through, and if it is, we run the lambda passed in rather than doing another http request. 

There is also a potential problem with doing a batch write, that multiple servers could attempt to edit the same information at the same time, and as a result there may be possible data inconsistency. This was not an issue with the batch read, because in that situation, the servers are not changing any information in the database. However here, there could be conflicting changes to the database which can not all be performed, which will result in some of the changes being left out

To solve this, we made a Hub appointed leader, which is the only server able to do batch writes. Whenever the Hub restarts or another galleryServer registers with the Hub, the Hub picks a new random leader and sends the leader to the servers. Within the batch writes, a server checks if it is the leader and if it is not, it sends a redirect with the leader’s url.
## Stage 5: Health Checks
We added a quarkus extension to allow the hub to see if the servers are still running. This is necessary because if the leader goes down, there will be no server which can do a batch write. 

We added another extension to allow us to schedule a method to be run periodically.
Every 10 seconds the Hub loops through all of the servers and attempts a get call on each one. If the call fails, the hub deletes that server and if it was the leader, choses a new leader
## Stage 6: Fault Tolerance 
We added a Fallback method for the redirect method. Previously, when looping through the galleries, if one of the galleries failed to return something, either because of an exception or the server was not up properly, we stopped the method there and returned a list of everything that had been gotten until that point. Now, the Fallback method gets called and adds a message indicating the problem with that server and then the redirect method resumes with the rest of the galleries.

We realized that there was a problem with the hub deleting the server from the database entirely when it was down, so instead it marks it as inactive.

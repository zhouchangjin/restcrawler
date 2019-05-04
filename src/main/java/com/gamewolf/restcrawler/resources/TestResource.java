package com.gamewolf.restcrawler.resources;

import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/test")
@Produces(MediaType.APPLICATION_JSON)
public class TestResource {
	
	
    @GET
    public String sayHello(@QueryParam("name") Optional<String> name) {
    	return name.get();
    }

}

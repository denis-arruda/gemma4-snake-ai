package dev.denisarruda.gemmasnakeai.simulation.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.denisarruda.gemmasnakeai.game.entity.Cell;
import dev.denisarruda.gemmasnakeai.game.entity.GameState;
import dev.denisarruda.gemmasnakeai.simulation.control.SimulationRunner;
import dev.denisarruda.gemmasnakeai.simulation.entity.SimulationRun;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/simulations")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class SimulationsResource {

    static final System.Logger LOGGER = System.getLogger(SimulationsResource.class.getName());

    final SimulationRunner runner;
    final ObjectMapper mapper;

    @Inject
    SimulationsResource(SimulationRunner runner, ObjectMapper mapper) {
        this.runner = runner;
        this.mapper = mapper;
    }

    @POST
    public Response start() {
        var id = generateId();
        var initial = runner.startRun(id);
        LOGGER.log(System.Logger.Level.INFO, "Started simulation {0}", id);
        return Response.status(201).entity(toJson(initial)).build();
    }

    @POST
    @Path("/restart")
    public Response restart() {
        var id = generateId();
        var initial = runner.startRun(id);
        LOGGER.log(System.Logger.Level.INFO, "Restarted simulation, new run {0}", id);
        return Response.ok(toJson(initial)).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return runner.getRun(id)
            .map(run -> Response.ok(runToJson(run)).build())
            .orElse(Response.status(404).build());
    }

    private static String generateId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private ObjectNode toJson(GameState state) {
        var root = mapper.createObjectNode();
        root.put("id", state.runId());
        root.put("status", state.status().name());
        root.put("step", state.step());
        root.put("score", state.score());

        var snake = mapper.createObjectNode();
        snake.set("head", cellNode(state.head()));
        snake.set("body", cellArray(state.body()));
        snake.put("direction", state.direction().name());
        root.set("snake", snake);

        root.set("food", cellArray(
            state.food().stream().map(f -> f.position()).toList()));
        return root;
    }

    private ObjectNode runToJson(SimulationRun run) {
        var root = mapper.createObjectNode();
        root.put("id", run.id());
        root.put("status", run.status().name());
        root.put("finalScore", run.finalScore());
        root.put("steps", run.snapshots().size());
        return root;
    }

    private ObjectNode cellNode(Cell cell) {
        var node = mapper.createObjectNode();
        node.put("x", cell.x());
        node.put("y", cell.y());
        return node;
    }

    private ArrayNode cellArray(List<Cell> cells) {
        var array = mapper.createArrayNode();
        for (var cell : cells) {
            array.add(cellNode(cell));
        }
        return array;
    }
}

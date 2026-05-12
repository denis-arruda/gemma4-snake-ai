package dev.denisarruda.gemmasnakeai.simulation.boundary;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class SimulationsResourceTest {

    @Test
    void postSimulations_returns201WithId() {
        given()
            .when().post("/simulations")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("status", notNullValue());
    }

    @Test
    void getSimulation_returns404ForUnknown() {
        given()
            .when().get("/simulations/unknown-id")
            .then()
            .statusCode(404);
    }

    @Test
    void postRestart_returns200WithNewState() {
        given()
            .when().post("/simulations/restart")
            .then()
            .statusCode(200)
            .body("id", notNullValue());
    }
}

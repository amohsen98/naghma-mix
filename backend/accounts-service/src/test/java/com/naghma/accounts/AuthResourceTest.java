package com.naghma.accounts;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Runs against a Dev Services PostgreSQL container (needs Docker). */
@QuarkusTest
class AuthResourceTest {

    @Test
    void registerLoginAndReadProfile() {
        String email = "dj-" + UUID.randomUUID() + "@example.com";
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"secret-pass\",\"displayName\":\"Karim\",\"locale\":\"ar\"}")
                .post("/api/auth/register")
                .then().statusCode(201)
                .body("user.tier", equalTo("FREE"))
                .body("user.locale", equalTo("ar"));

        String token = given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email.toUpperCase() + "\",\"password\":\"secret-pass\"}")
                .post("/api/auth/login")
                .then().statusCode(200).body("token", notNullValue())
                .extract().path("token");

        given().auth().oauth2(token).get("/api/auth/me")
                .then().statusCode(200).body("displayName", equalTo("Karim"));
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingTheAccount() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"nobody@example.com\",\"password\":\"whatever1\"}")
                .post("/api/auth/login")
                .then().statusCode(401).body("detail", equalTo("Invalid email or password"));
    }

    @Test
    void shortPasswordFailsValidation() {
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"a@b.co\",\"password\":\"short\",\"displayName\":\"A\"}")
                .post("/api/auth/register")
                .then().statusCode(400);
    }
}

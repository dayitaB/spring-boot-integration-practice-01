package ec.edu.epn.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.epn.dto.PassengerRequest;
import ec.edu.epn.model.Passenger;
import ec.edu.epn.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PassengerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PassengerRepository passengerRepository;

    // Limpia la base de datos H2 antes de cada prueba
    @BeforeEach
    void setUp() {
        passengerRepository.deleteAll();
    }

    // ─── Métodos auxiliares privados ─────────────────────────────────────────

    private PassengerRequest crearRequest(String nombre, String apellido,
                                           String email, String pasaporte) {
        PassengerRequest req = new PassengerRequest();
        req.setFirstName(nombre);
        req.setLastName(apellido);
        req.setEmail(email);
        req.setPassportNumber(pasaporte);
        return req;
    }

    private Passenger crearPasajero(String nombre, String apellido,
                                     String email, String pasaporte) throws Exception {
        PassengerRequest req = crearRequest(nombre, apellido, email, pasaporte);
        MvcResult resultado = mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(resultado.getResponse().getContentAsString(), Passenger.class);
    }

    // ─── Pruebas ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreatePassenger() throws Exception {
        PassengerRequest req = crearRequest("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Erick"))
                .andExpect(jsonPath("$.lastName").value("Costa"))
                .andExpect(jsonPath("$.email").value("erick@epn.edu.ec"))
                .andExpect(jsonPath("$.passportNumber").value("PA123456"));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA111111");

        // Mismo email, pasaporte diferente → debe rechazar con 400
        PassengerRequest duplicado = crearRequest("Anderson", "Cango", "erick@epn.edu.ec", "PA999999");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldFindAllPassengers() throws Exception {
        crearPasajero("Erick",    "Costa",  "erick@epn.edu.ec",    "PA111111");
        crearPasajero("Anderson", "Cango",  "anderson@epn.edu.ec", "PA222222");

        mockMvc.perform(get("/api/passengers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].email",
                        containsInAnyOrder("erick@epn.edu.ec", "anderson@epn.edu.ec")));
    }

    @Test
    void shouldFindPassengerById() throws Exception {
        Passenger creado = crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        mockMvc.perform(get("/api/passengers/{id}", creado.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(creado.getId()))
                .andExpect(jsonPath("$.firstName").value("Erick"));
    }

    @Test
    void shouldFindPassengerByEmail() throws Exception {
        crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        mockMvc.perform(get("/api/passengers/email/{email}", "erick@epn.edu.ec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("erick@epn.edu.ec"))
                .andExpect(jsonPath("$.lastName").value("Costa"));
    }

    @Test
    void shouldFindPassengerByPassportNumber() throws Exception {
        crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        mockMvc.perform(get("/api/passengers/passport/{pasaporte}", "PA123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passportNumber").value("PA123456"))
                .andExpect(jsonPath("$.firstName").value("Erick"));
    }

    @Test
    void shouldReturn404WhenPassengerNotFound() throws Exception {
        mockMvc.perform(get("/api/passengers/{id}", 99999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void shouldUpdatePassenger() throws Exception {
        Passenger creado = crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        PassengerRequest actualizacion = crearRequest(
                "Erick Rodrigo", "Costa Mora", "erick.updated@epn.edu.ec", "PA654321");

        mockMvc.perform(put("/api/passengers/{id}", creado.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(actualizacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Erick Rodrigo"))
                .andExpect(jsonPath("$.email").value("erick.updated@epn.edu.ec"));
    }

    @Test
    void shouldDeletePassenger() throws Exception {
        Passenger creado = crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        // Eliminar
        mockMvc.perform(delete("/api/passengers/{id}", creado.getId()))
                .andExpect(status().isNoContent());

        // Verificar que ya no existe → 404
        mockMvc.perform(get("/api/passengers/{id}", creado.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidEmail() throws Exception {
        // Email sin formato válido → debe rechazar con 400
        PassengerRequest req = crearRequest("Daniel", "Oña", "esto-no-es-un-email", "PA777777");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));
    }

    @Test
    void shouldRejectDuplicatePassportNumber() throws Exception {
        crearPasajero("Erick", "Costa", "erick@epn.edu.ec", "PA123456");

        // Mismo pasaporte, email diferente → debe rechazar con 400
        PassengerRequest duplicado = crearRequest("Dylan", "Granizo", "dylan@epn.edu.ec", "PA123456");

        mockMvc.perform(post("/api/passengers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}

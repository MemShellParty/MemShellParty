package com.reajason.javaweb.boot.controller;

import com.reajason.javaweb.boot.dto.MemShellGenerateRequest;
import com.reajason.javaweb.boot.service.GenerationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JSON binding contract for structured shell-tool parameters.
 */
class MemShellGeneratorControllerBindingTest {

    @Test
    void literalJsonObjectReachesGenerationRequest() throws Exception {
        GenerationService generationService = mock(GenerationService.class);
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new MemShellGeneratorController(generationService))
                .build();

        mockMvc.perform(post("/api/memshell/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shellToolConfig\":{\"remoteObject\":{\"id\":7,\"name\":\"Node 7\"}}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<MemShellGenerateRequest> captor = ArgumentCaptor.forClass(MemShellGenerateRequest.class);
        verify(generationService).generateMemShell(captor.capture());
        assertEquals(Map.of("id", 7, "name", "Node 7"),
                captor.getValue().getShellToolConfig().get("remoteObject"));
    }
}

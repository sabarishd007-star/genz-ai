package com.genzai.project;

import com.genzai.exception.ResourceNotFoundException;
import com.genzai.llm.LlmProviderService;
import com.genzai.project.dto.CreateProjectRequest;
import com.genzai.project.dto.ProjectResponse;
import com.genzai.project.model.Project;
import com.genzai.project.model.ProjectBlueprint;
import com.genzai.project.model.ProjectMilestone;
import com.genzai.project.repository.*;
import com.genzai.project.service.BlueprintValidationService;
import com.genzai.project.service.ProjectService;
import com.genzai.project.service.VivaEvaluatorService;
import com.genzai.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectBlueprintRepository blueprintRepository;
    private ProjectMilestoneRepository milestoneRepository;
    private VivaQuestionRepository vivaQuestionRepository;
    private VivaAnswerRepository vivaAnswerRepository;
    private BlueprintValidationService validationService;
    private VivaEvaluatorService vivaEvaluatorService;
    private LlmProviderService llmProviderService;
    private UserActivityService userActivityService;
    private ProjectService projectService;

    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        projectRepository = Mockito.mock(ProjectRepository.class);
        blueprintRepository = Mockito.mock(ProjectBlueprintRepository.class);
        milestoneRepository = Mockito.mock(ProjectMilestoneRepository.class);
        vivaQuestionRepository = Mockito.mock(VivaQuestionRepository.class);
        vivaAnswerRepository = Mockito.mock(VivaAnswerRepository.class);
        validationService = new BlueprintValidationService();
        validationService.init();
        vivaEvaluatorService = Mockito.mock(VivaEvaluatorService.class);
        llmProviderService = Mockito.mock(LlmProviderService.class);
        userActivityService = Mockito.mock(UserActivityService.class);

        projectService = new ProjectService(
                projectRepository,
                blueprintRepository,
                milestoneRepository,
                vivaQuestionRepository,
                vivaAnswerRepository,
                validationService,
                vivaEvaluatorService,
                llmProviderService,
                userActivityService
        );
    }

    @Test
    @DisplayName("Create project falls back to schema-valid template when LLM is unavailable")
    void createProject_FallsBackToTemplate_WhenLlmUnavailable() {
        when(llmProviderService.isGeneralLlmAvailable()).thenReturn(false);

        Project savedProject = Project.builder()
                .id(UUID.randomUUID())
                .userId(userA)
                .title("Smart Campus RAG")
                .domain("AI/ML")
                .idea("Local transformer notes grounding")
                .status("ACTIVE")
                .build();
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        ProjectBlueprint savedBlueprint = ProjectBlueprint.builder()
                .id(UUID.randomUUID())
                .projectId(savedProject.getId())
                .userId(userA)
                .version(1)
                .source("TEMPLATE")
                .content(Map.of("problemStatement", "Grounding student notes locally"))
                .build();
        when(blueprintRepository.save(any(ProjectBlueprint.class))).thenReturn(savedBlueprint);

        CreateProjectRequest req = CreateProjectRequest.builder()
                .title("Smart Campus RAG")
                .domain("AI/ML")
                .idea("Local transformer notes grounding")
                .build();

        ProjectResponse response = projectService.createProject(userA, req);

        assertThat(response.getTitle()).isEqualTo("Smart Campus RAG");
        assertThat(response.getSource()).isEqualTo("TEMPLATE");
        verify(userActivityService, times(1)).logActivity(eq(userA), eq("PROJECT_CREATED"), anyString(), anyString());
    }

    @Test
    @DisplayName("User B gets 404 when attempting to query User A's project")
    void userBCannotAccessUserAProject_Returns404() {
        UUID projectAId = UUID.randomUUID();
        // Project exists for User A, but repository findByIdAndUserId(projectAId, userB) returns empty
        when(projectRepository.findByIdAndUserId(projectAId, userB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(userB, projectAId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found with id: " + projectAId);
    }

    @Test
    @DisplayName("Milestone completion writes one idempotent activity_log row")
    void milestoneUpdate_WritesIdempotentActivityLog() {
        UUID projectId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();

        Project project = Project.builder().id(projectId).userId(userA).build();
        ProjectMilestone milestone = ProjectMilestone.builder()
                .id(milestoneId)
                .projectId(projectId)
                .userId(userA)
                .title("Complete Vector Schema")
                .status("DOING")
                .build();

        when(projectRepository.findByIdAndUserId(projectId, userA)).thenReturn(Optional.of(project));
        when(milestoneRepository.findByIdAndUserId(milestoneId, userA)).thenReturn(Optional.of(milestone));
        when(milestoneRepository.save(any(ProjectMilestone.class))).thenAnswer(i -> i.getArgument(0));

        // First transition to DONE
        projectService.updateMilestoneStatus(userA, projectId, milestoneId, "DONE");
        verify(userActivityService, times(1)).logActivity(
                eq(userA), eq("PROJECT_MILESTONE_COMPLETED"), contains("Complete Vector Schema"), anyString()
        );

        // Second duplicate transition to DONE for same milestone should be idempotent (no duplicate log call)
        projectService.updateMilestoneStatus(userA, projectId, milestoneId, "DONE");
        verify(userActivityService, times(1)).logActivity(
                eq(userA), eq("PROJECT_MILESTONE_COMPLETED"), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("Export README never includes another user's data and returns 404 for User B")
    void exportReadme_NeverIncludesAnotherUserData() {
        UUID projectAId = UUID.randomUUID();
        when(projectRepository.findByIdAndUserId(projectAId, userB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.exportReadme(userB, projectAId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project not found");
    }
}
